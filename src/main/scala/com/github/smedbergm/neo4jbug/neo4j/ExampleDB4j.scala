package com.github.smedbergm.neo4jbug.neo4j

import java.io.File
import scala.collection.JavaConverters._
import scalaz._
import Scalaz._
import scalaz.concurrent.Task

import com.github.smedbergm.neo4jbug.utils._
import com.typesafe.scalalogging.LazyLogging
import org.apache.tinkerpop.gremlin.neo4j.structure.Neo4jGraph
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.structure.{Transaction, Vertex, T => Token}

class ExampleDB4j(storageDirectory: File) extends ExampleDB with ScalazSupport with LazyLogging {
  import ExampleDB4j._
  import com.github.smedbergm.neo4jbug.Implicits._

  if (storageDirectory.exists && storageDirectory.isDirectory && storageDirectory.canWrite) {
    // OK
  } else if (storageDirectory.exists) {
    throw new IllegalArgumentException(s"Parameter storageDirectory = ${storageDirectory.getAbsolutePath} must be a writable directory")
  } else {
    assert(storageDirectory.mkdirs())
  }

  sys.addShutdownHook {
    logger.debug("Application shutdown requested, closing DB.")
    graphDB.close()
    logger.debug("DB closed.")
  }

  val graphDB = Neo4jGraph.open(storageDirectory.getAbsolutePath)
  val graphTraversal = new GraphTraversalSource(graphDB)

  private def vertexToFoo(v: Vertex): Task[Foo] = Task.now {
    logger.debug(s"Parsing a foo from vertex $v")
    val foo = Foo(v.property[Int](idKey).value(), v.property[String](nameKey).value())
    logger.debug(s"Parsed $foo from $v")
    foo
  }

  override def addFoo(foo: Foo): Task[Foo] = for {
    _ <- Task(logger.debug(s"Attempting to add $foo.")) // To ensure we're using the thread pool
    _ <- graphTraversal.V().hasLabel(fooLabel).has(idKey, foo.id).asScala.toTraversable.headOption.succeedIfEmpty
      .handleWith{ case NonemptyOption => Task.fail(BadRequestException(s"Foo #${foo.id} already exists."))}
    tx <- Task.now(graphTraversal.tx())
    _ = logger.debug(s"Adding foo $foo, opening transaction.")
    v <- Task.now[Vertex](graphDB.addVertex(Token.label, fooLabel, idKey, Int.box(foo.id), nameKey, foo.name))
      .handleWith(rollback(tx))
    _ = logger.debug(s"Adding foo $foo, about to commit.")
    _ = Task.now(tx.commit()).handleWith(rollback(tx))
    storedFoo <- vertexToFoo(v)
  } yield storedFoo

  private def countFrom(n: Int): Stream[Int] = if (n == Int.MaxValue) {
    Stream(n)
  } else {
    n #:: countFrom(n + 1)
  }

  private def getOpenFooId: Task[Int] = {
    countFrom(0).find { x =>
      !graphTraversal.V().hasLabel(fooLabel).has(idKey, x).hasNext()
    }.toTask.handleWith{
      case EmptyOption => Task.fail(BadRequestException("Every possible ID is taken. How did you do that?"))
    }
  }

  override def getFoos(start: Int, n: Int): Task[List[Foo]] = {
    Task(logger.debug("Fetching foos")).flatMap {_ =>
      graphTraversal.V().hasLabel(fooLabel).asScala.drop(start).take(n).map(vertexToFoo).toList.sequence
    }
  }

  override def removeFoo(fooID: Int): Task[Foo] = for {
    _ <- Task(logger.debug(s"Attempting to remove Foo #$fooID."))
    v <- graphTraversal.V().hasLabel(fooLabel).has(idKey, fooID).asScala.toTraversable.headOption.toTask
      .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Foo #${fooID} not found.")) }
    _ = logger.debug(s"Located Foo #$fooID at vertex ${v}. Attempting to remove.")
    storedFoo <- vertexToFoo(v)
    tx = graphDB.tx()
    _ = logger.debug(s"Beginning removal. isOpen = ${tx.isOpen}")
    _ <- graphTraversal.V(v.id).out(edgeLabel).drop().asScala.toIterable.headOption.succeedIfEmpty
      .handleWith(rollback(tx))
    _ = logger.debug("Drop step completed.")
    _ <- Task.now(v.remove()).handleWith(rollback(tx))
    _ = logger.debug("Foo removal completed.")
    _ = tx.commit()
  } yield storedFoo

  override def generateFooWithBars(n: Int): Task[Foo] = for {
    _ <- Task(logger.debug("Attempting to generate a Foo."))
    fooID <- getOpenFooId
    _ = logger.debug(s"Generating Foo #$fooID")
    tx = graphDB.tx()
    _ = logger.debug(s"Transaction retrieved and ${if (tx.isOpen) "is" else "is not"} open.")
    fooV = graphDB.addVertex(Token.label, fooLabel, idKey, Int.box(fooID), nameKey, "Autogenerated Foo")
    _ = (0 until n).foreach { barID =>
      val barV = graphDB.addVertex(Token.label, barLabel, idKey, Int.box(barID), nameKey, s"Autogenerated barchild of Foo #${fooID}")
      fooV.addEdge(edgeLabel, barV)
    }
    _ = tx.commit()
    _ = logger.debug(s"Foo $fooID created with $n bars. Transaction committed and ${if (tx.isOpen) "not closed" else "closed"}.")
    savedFoo <- vertexToFoo(fooV)
  } yield savedFoo

  private def vertexToBar(v: Vertex): Task[Bar] = Task.now(
    Bar(v.property[Int](idKey).value, v.property[String](nameKey).value)
  )

  override def getBars(fooId: Int): Task[List[Bar]] = {
    graphTraversal.V().hasLabel(fooLabel).has(idKey, fooId)
      .out(edgeLabel).hasLabel(barLabel).asScala.map(vertexToBar).toList.sequence
  }

  override def removeBar(fooID: Int, barID: Int): Task[Bar] = for {
    _ <- Task(logger.debug(s"Attempting to remove Bar #$barID from Foo #$fooID."))
    barV <- graphTraversal.V().hasLabel(fooLabel).has(idKey, fooID).out(edgeLabel).hasLabel(barLabel).has(idKey, barID)
      .asScala.toIterable.headOption.toTask
      .handleWith {
        case EmptyOption =>
          val message = s"Bar #$barID not found owned by Foo #$fooID"
          logger.error(message)
          Task.fail(NotFoundException(message))
      }
    _ = logger.debug(s"Bar #$barID found at vertex $barV")
    bar <- vertexToBar(barV)
    _ = logger.debug(s"Parsed $bar from vertex $barV")
    tx = graphDB.tx()
    _ = logger.debug(s"Beginning removal. Transaction ${if (tx.isOpen) "is" else "is not"} open.")
    _ = barV.remove()
    _ = tx.commit()
    _ = logger.debug(s"Removal successful. Transaction ${if (tx.isOpen) "is" else "is not"} open.")
  } yield bar

  override def close(): Unit = {
    graphDB.close()
  }
}

object ExampleDB4j extends LazyLogging {
  val fooLabel = "LABEL=foo"
  val barLabel = "LABEL=bar"
  val edgeLabel = "LABEL=owns"
  val idKey = "vertex$id"
  val nameKey = "vertex$name"

  def rollback[T](tx: Transaction): PartialFunction[Throwable, Task[T]] = {
    case exc =>
      logger.debug(s"Rolling back due to exception", exc)
      tx.rollback()
      tx.close()
      Task.fail(exc).asInstanceOf[Task[T]]
  }
}