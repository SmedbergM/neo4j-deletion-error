package com.github.smedbergm.neo4jbug.neo4j

import java.io.File

import com.github.smedbergm.neo4jbug.utils.{BadRequestException, EmptyOption, NotFoundException, ScalazSupport}
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

  val graphDB = Neo4jGraph.open(storageDirectory.getAbsolutePath).asScala

  sys.addShutdownHook{
    graphDB.close()
  }

  def addFoo(foo: Foo): Task[Foo] = graphDB.V.hasLabel[Foo]
    .has(idKey, foo.id)
    .headOption() match {
      case None => for {
        tx <- Task {graphDB.tx()}
        newVertex <- Task(graphDB + foo).handleWith{ case exc =>
            tx.rollback()
            Task.fail(exc)
        }
        _ = tx.commit()
      } yield newVertex.toCC[Foo]
      case Some(_) => Task.fail(BadRequestException(s"Foo #${foo.id} already exists in database."))
    }

  def getFoo(fooID: Int): Task[Foo] = for {
    v <- graphDB.V.hasLabel[Foo].has(idKey, fooID).headOption().toTask
      .handleWith{case EmptyOption => Task.fail(NotFoundException(s"Foo #${fooID} not found."))}
    foo <- Task(v.toCC[Foo])
  } yield foo

  def removeFoo(fooID: Int): Task[Foo] = for {
    v <- graphDB.V.hasLabel[Foo].has(idKey, fooID).headOption().toTask
     .handleWith{ case EmptyOption => Task.fail(NotFoundException(s"Foo #${fooID} not found."))}
    foo <- Task(v.toCC[Foo])
    tx <- Task(graphDB.tx())
    _ = Task(v.remove())
      .handleWith { case exc =>
        tx.rollback()
        Task.fail(exc)
      }
    _ = tx.commit()
  } yield foo

  def updateFoo(foo: Foo): Task[Foo] = for {
    v <- graphDB.V.hasLabel[Foo].has(idKey, foo.id).headOption().toTask
      .handleWith{case EmptyOption => Task.fail(NotFoundException(s"Foo #${foo.id} not found."))}
    tx <- Task(graphDB.tx())
    _ <- Task(v.updateWith(foo))
      .handleWith{ case exc =>
        tx.rollback()
        Task.fail(exc)
      }
    _ = tx.commit()
  } yield v.toCC[Foo]

  def addBar(bar: Bar, fooID: Int): Task[Bar] = ???
  def getBar(barID: Int): Task[Bar] = ???
  def getBars(fooID: Int): Task[Iterable[Bar]] = ???
  def removeBar(bar: Bar, fooID: Int): Task[Bar] = ???
  def updateBar(bar: Bar, fooID: Int): Task[Bar] = ???

  def close() = graphDB.close()
}

object ExampleDB {
  val idKey = Key[Int]("id")
  val nameKey = Key[String]("name")
}

case class Foo(id: Int, name: String)
case class Bar(id: Int, name: String)