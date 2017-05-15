package com.github.smedbergm.neo4jbug.neo4j

import java.io.File
import scala.collection.JavaConverters._
import scalaz.concurrent.Task

import com.github.smedbergm.neo4jbug.utils.{EmptyOption, BadRequestException, NonemptyOption, ScalazSupport}
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
    graphDB.close()
  }

  val graphDB = Neo4jGraph.open(storageDirectory.getAbsolutePath)
  val graphTraversal = new GraphTraversalSource(graphDB)

  override def addFoo(foo: Foo): Task[Foo] = for {
    _ <- graphTraversal.V().hasLabel(fooLabel).has(idKey, foo.id).asScala.toTraversable.headOption.succeedIfEmpty
      .handleWith{ case NonemptyOption => Task.fail(BadRequestException(s"Foo #${foo.id} already exists."))}
    tx <- Task(graphTraversal.tx())
    _ = tx.open()
    v <- Task[Vertex](graphDB.addVertex(Token.label, fooLabel, idKey, Int.box(foo.id), nameKey, foo.name))
      .handleWith(rollback(tx))
    _ = tx.commit()
  } yield Foo(v.property[Int](idKey).value(), v.property[String](nameKey).value())

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

  override def getFoos(start: Int, n: Int): Task[List[Foo]] = ???

  override def removeFoo(id: Int): Task[Foo] = ???

  override def generateFooWithBars(n: Int): Task[Foo] = ???

  override def getBars(fooId: Int): Task[List[Bar]] = ???

  override def removeBar(fooID: Int, barId: Int): Task[Bar] = ???

  override def close(): Unit = {
    graphDB.close()
  }
}

object ExampleDB4j extends LazyLogging {
  val fooLabel = "LABEL=foo"
  val barLabel = "LABEL=bar"
  val idKey = "vertex$id"
  val nameKey = "vertex$name"

  def rollback[T](tx: Transaction): PartialFunction[Throwable, Task[T]] = {
    case exc =>
      logger.debug(s"Rolling back due to exception", exc)
      tx.rollback()
      Task.fail(exc).asInstanceOf[Task[T]]
  }
}