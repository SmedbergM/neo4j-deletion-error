package com.github.smedbergm.neo4jbug.http

import com.github.smedbergm.neo4jbug.neo4j.ExampleDB

import org.http4s._
import org.http4s.dsl._
import scalaz.concurrent.Task

class DBService(db: ExampleDB) extends ResponseSupport {

  val handleRequest: PartialFunction[Request, Task[Response]] = {
    case GET -> Root => Ok(Usage.usage)
    case GET -> Root / "ping" => Ok("pong")

    case GET -> Root / "foo" / IntVar(fooID) =>
      db.getFoo(fooID).flatMap(toTaskResponse).handleWith(toErrorResponse)
    case GET -> Root / "foo" / IntVar(fooID) / "bar" =>
      db.getBars(fooID).flatMap(toTaskResponse).handleWith(toErrorResponse)
    case GET -> Root / "foo" / IntVar(fooID) / "bar" / IntVar(barID) =>
      db.getBar(barID, fooID).flatMap(toTaskResponse).handleWith(toErrorResponse)
  }

  val dbService = HttpService(handleRequest)
}

object Usage {
  val usage =
    """Available Routes:
      |GET /ping
      |GET /foo/{fooID}
      |GET /foo/{fooID}/bar # returns an JSON array of all the Bars owned by that Foo
      |GET /foo/{fooID}/bar/{barID}""".stripMargin
}