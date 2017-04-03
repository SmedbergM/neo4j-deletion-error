package com.github.smedbergm.neo4jbug.neo4j

import java.io.File

import scala.collection.JavaConverters._
import com.github.smedbergm.neo4jbug.utils._
import com.typesafe.scalalogging.LazyLogging
import org.apache.tinkerpop.gremlin.neo4j.structure.Neo4jGraph
import gremlin.scala._
import org.apache.tinkerpop.gremlin.structure.Transaction

import scalaz.\/
import scalaz.concurrent.Task

class ExampleDB(storageDirectory: File) extends ScalazSupport with LazyLogging {
  import ExampleDB._

  if (storageDirectory.exists && storageDirectory.isDirectory && storageDirectory.canWrite) {
    // OK
  } else if (storageDirectory.exists) {
    throw new IllegalArgumentException("Parameter storageDirectory must be a writable directory.")
  } else {
    storageDirectory.mkdir()
  }

  val graphDB: ScalaGraph = Neo4jGraph.open(storageDirectory.getAbsolutePath).asScala

  sys.addShutdownHook{
    graphDB.close()
  }

  def addFoo(name: String): Task[Foo] = for {
    id <- getOpenFooID
    foo <- addFoo(Foo(id, name))
  } yield foo

  def addFoo(foo: Foo): Task[Foo] = for {
    _ <- graphDB.V.hasLabel[Foo].has(idKey, foo.id).headOption().succeedIfEmpty
      .handleWith{ case NonemptyOption => Task.fail(BadRequestException(s"Foo #${foo.id} already exists."))}
    tx <- Task{graphDB.tx()}
    newVertex <- Task(graphDB + foo).handleWith(rollback(tx))
    _ = tx.commit()
  } yield newVertex.toCC[Foo]

  def getFoo(fooID: Int): Task[Foo] = for {
    v <- graphDB.V.hasLabel[Foo].has(idKey, fooID).headOption().toTask
      .handleWith{case EmptyOption => Task.fail(NotFoundException(s"Foo #${fooID} not found."))}
    foo <- Task(v.toCC[Foo])
  } yield foo

  def getFoos(start: Int = 0, n: Int = 10): Task[List[Foo]] = Task {
    graphDB.V.hasLabel[Foo].map(_.toCC[Foo]).toStream.skip(start).iterator().asScala.take(n).toList
  }

  def removeFoo(fooID: Int): Task[Foo] = for {
    v <- graphDB.V.hasLabel[Foo].has(idKey, fooID).headOption().toTask
      .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Foo #${fooID} not found."))}
    _ = logger.debug(s"About to remove vertex ${v}")
    storedFoo <- Task(v.toCC[Foo])
    _ = logger.debug(s"About to remove $storedFoo")
    tx <- Task {graphDB.tx()}
    _ = logger.debug("Beginning transaction")
    _ <- v.out.drop.headOption().succeedIfEmpty.handleWith(rollback(tx))
    _ = logger.debug("Drop step completed.")
    _ <- Task.now(v.remove()).handleWith(rollback(tx))
    _ = logger.debug("Foo removal completed")
    _ = tx.commit()
  } yield storedFoo

  def removeFoo2(fooID: Int): Task[Foo] = for {
    v <- graphDB.V.hasLabel[Foo].has(idKey, fooID).headOption().map(_.asScala).toTask
     .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Foo #${fooID} not found."))}
    _ = logger.debug(s"About to remove vertex $v")
    storedFoo <- Task(v.toCC[Foo])
    tx <- Task(graphDB.tx())
    _ <- v.out.drop.headOption.succeedIfEmpty.handleWith(rollback(tx))
    _ = logger.debug("Drop step completed")
    _ <- graphDB.V.hasLabel[Foo].has(idKey, fooID).drop.headOption().succeedIfEmpty
        .handleWith(rollback(tx))
    _ = logger.debug("Foo removal completed")
    _ = tx.commit()
    _ = logger.debug("Commit completed")
  } yield storedFoo

  def removeFoosChildren(fooID: Int): Task[Int] = for {
    v <- graphDB.V.hasLabel[Foo].has(idKey, fooID).headOption().toTask
      .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Foo #${fooID} not found."))}
    tx <- Task(graphDB.tx())
    count <- v.out.foldLeft(0)((count,v) => {v.remove(); count + 1}).headOption.toTask
    _ = tx.commit()
  } yield count

  def updateFoo(foo: Foo): Task[Foo] = for {
    v <- graphDB.V.hasLabel[Foo].has(idKey, foo.id).headOption().toTask
      .handleWith{case EmptyOption => Task.fail(NotFoundException(s"Foo #${foo.id} not found."))}
    tx <- Task(graphDB.tx())
    _ <- Task(v.updateWith(foo)).handleWith(rollback(tx))
    _ = tx.commit()
  } yield v.toCC[Foo]

  private def getOpenFooID: Task[Int] = {
    countFrom(0).find { x =>
      graphDB.V.hasLabel[Foo].has(idKey,x).headOption().isEmpty
    }.toTask.handleWith{ case EmptyOption => Task.fail(BadRequestException("Every possible ID is taken. How did you do that?"))}
  }

  def addBar(fooID: Int, name: String): Task[Bar] = for {
    fooV <- graphDB.V.hasLabel[Foo].has(idKey, fooID).headOption().toTask
      .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Foo #${fooID} not found."))}
    fooVOut = fooV.out().map(_.value2(idKey)).toSet
    barID <- countFrom(0).find{id => !fooVOut.contains(id)}.toTask
      .handleWith{ case EmptyOption => Task.fail(BadRequestException("Every possible ID is taken. How did you do that?"))}
    tx <- Task(graphDB.tx())
    barV <- Task {
      val barV = graphDB + Bar(barID, name)
      fooV --- "owns" --> barV
      barV
    }.handleWith{rollback(tx)}
    _ = tx.commit()
  } yield barV.toCC[Bar]

  def addBar(bar: Bar, fooID: Int): Task[Bar] = for {
    fooV <- graphDB.V.hasLabel[Foo].has(idKey, fooID).headOption().toTask
      .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Foo #${fooID} not found."))}
    _ <- fooV.out().hasLabel[Bar].has(idKey, bar.id).headOption().succeedIfEmpty
      .handleWith{ case NonemptyOption => Task.fail(BadRequestException(s"Bar #${bar.id} already exists in the database."))}
    tx <- Task(graphDB.tx())
    barV <- Task({
      val barV = graphDB + bar
      fooV --- "owns" --> barV
      barV
    }).handleWith{ rollback(tx) }
    _ = tx.commit()
  } yield barV.toCC[Bar]

  def generateFooWithBars(n: Int = 1000): Task[Foo] = {
    getOpenFooID.map { fooID =>
      val tx = graphDB.tx()
      val fooV = graphDB + Foo(fooID, "Autogenerated Foo")
      tx.commit()
      fooV
    }.map { fooV =>
      val tx = graphDB.tx()
      (0 until n).foreach { barID =>
        val barV = graphDB + Bar(barID, s"Autogenerated barchild of Foo #${fooV.value2(idKey)}")
        fooV --- "owns" --> barV
      }
      tx.commit()
      fooV.toCC[Foo]
    }
  }

  def getBar(barID: Int, fooID: Int): Task[Bar] = graphDB.V
    .hasLabel[Foo]
    .has(idKey, fooID)
    .out
    .hasLabel[Bar]
    .has(idKey, barID)
    .headOption
    .map(_.toCC[Bar])
    .toTask
    .handleWith {case EmptyOption => Task.fail(NotFoundException(s"Bar #${barID} not found."))}

  def getBars(fooID: Int): Task[List[Bar]] = Task {
    graphDB.V
    .hasLabel[Foo]
    .has(idKey, fooID)
    .out
    .hasLabel[Bar]
    .map(_.toCC[Bar])
    .toList
    .sortBy(_.id)
  }

  def removeBar(fooID: Int, barID: Int): Task[Bar] = for {
    fooV <- graphDB.V.hasLabel[Foo].has(idKey, fooID).headOption.toTask
      .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Foo #$fooID not found."))}
    barV <- fooV.out.hasLabel[Bar].has(idKey, barID).headOption.toTask
      .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Bar #$barID not found."))}
    storedBar <- Task(barV.toCC[Bar])
    tx <- Task(graphDB.tx())
    _ <- Task(barV.remove()).handleWith(rollback(tx))
    _ = tx.commit()
  } yield storedBar

  def updateBar(fooID: Int, bar: Bar): Task[Bar] = for {
    fooV <- graphDB.V.hasLabel[Foo].has(idKey, fooID).headOption.toTask
      .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Foo #$fooID not found."))}
    barV <- fooV.out.hasLabel[Bar].has(idKey, bar.id).headOption.toTask
      .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Bar #${bar.id} not found."))}
    tx <- Task(graphDB.tx())
    _ <- Task(barV.updateWith(bar)).handleWith(rollback(tx))
    _ = tx.commit()
  } yield barV.toCC[Bar]

  def close(): Unit = graphDB.close()
}

object ExampleDB extends LazyLogging {
  val idKey = Key[Int]("id")
  val nameKey = Key[String]("name")

  def rollback[T](tx: Transaction): PartialFunction[Throwable, Task[T]] = {
    case exc =>
      logger.debug(s"Rolling back due to exception $exc")
      tx.rollback()
      Task.fail(exc).asInstanceOf[Task[T]]
  }

  private def countFrom(n: Int): Stream[Int] = if (n == Int.MaxValue) {
    Stream(n)
  } else {
    n #:: countFrom(n + 1)
  }
}

case class Foo(id: Int, name: String)
case class Bar(id: Int, name: String)