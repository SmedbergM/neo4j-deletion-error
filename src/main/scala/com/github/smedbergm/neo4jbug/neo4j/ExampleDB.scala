package com.github.smedbergm.neo4jbug.neo4j

import java.io.File

import com.github.smedbergm.neo4jbug.utils._
import org.apache.tinkerpop.gremlin.neo4j.structure.Neo4jGraph
import gremlin.scala._
import org.apache.tinkerpop.gremlin.structure.Transaction

import scalaz.concurrent.Task

class ExampleDB(storageDirectory: File) extends ScalazSupport {
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

  def removeFoo(fooID: Int): Task[Foo] = for {
    v <- graphDB.V.hasLabel[Foo].has(idKey, fooID).headOption().toTask
     .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Foo #${fooID} not found."))}
    storedFoo <- Task(v.toCC[Foo])
    tx <- Task(graphDB.tx())
    _ <- v.out.drop.headOption.succeedIfEmpty.handleWith(rollback(tx))
    _ <- Task(v.remove()).handleWith(rollback(tx))
    _ = tx.commit()
  } yield storedFoo

  def updateFoo(foo: Foo): Task[Foo] = for {
    v <- graphDB.V.hasLabel[Foo].has(idKey, foo.id).headOption().toTask
      .handleWith{case EmptyOption => Task.fail(NotFoundException(s"Foo #${foo.id} not found."))}
    tx <- Task(graphDB.tx())
    _ <- Task(v.updateWith(foo)).handleWith(rollback(tx))
    _ = tx.commit()
  } yield v.toCC[Foo]

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
    }).handleWith{ case exc => tx.rollback(); Task.fail(exc)}
    _ = tx.commit()
  } yield barV.toCC[Bar]

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

  def removeBar(barID: Int, fooID: Int): Task[Bar] = for {
    fooV <- graphDB.V.hasLabel[Foo].has(idKey, fooID).headOption.toTask
      .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Foo #$fooID not found."))}
    barV <- fooV.out.hasLabel[Bar].has(idKey, barID).headOption.toTask
      .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Bar #$barID not found."))}
    storedBar <- Task(barV.toCC[Bar])
    tx <- Task(graphDB.tx())
    _ <- Task(barV.remove()).handleWith(rollback(tx))
    _ = tx.commit()
  } yield storedBar

  def updateBar(bar: Bar, fooID: Int): Task[Bar] = for {
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

object ExampleDB {
  val idKey = Key[Int]("id")
  val nameKey = Key[String]("name")

  def rollback[T](tx: Transaction): PartialFunction[Throwable, Task[T]] = {
    case exc =>
      tx.rollback()
      Task.fail(exc).asInstanceOf[Task[T]]
  }
}

case class Foo(id: Int, name: String)
case class Bar(id: Int, name: String)