package com.github.smedbergm.neo4jbug.http

import com.github.smedbergm.neo4jbug.neo4j.{Bar, ExampleDB, Foo}
import com.github.smedbergm.neo4jbug.utils.ScalazSupport
import org.http4s._
import org.http4s.dsl._

import scalaz.concurrent.Task

class DBService(db: ExampleDB) extends ResponseSupport with ScalazSupport {

  val handleRequest: PartialFunction[Request, Task[Response]] = {
    case GET -> Root => Ok(Usage.usage)
    case GET -> Root / "ping" => Ok("pong")

    case GET -> Root / "foo" :? Start(optStart) +& N(optN) =>
      val start = optStart.getOrElse(0)
      val n = optN.getOrElse(10)
      db.getFoos(start,n).flatMap(toTaskResponse).handleWith(toErrorResponse)
    case GET -> Root / "foo" / IntVar(fooID) =>
      db.getFoo(fooID).flatMap(toTaskResponse).handleWith(toErrorResponse)
    case GET -> Root / "foo" / IntVar(fooID) / "bar" :? Start(optStart) +& N(optN) =>
      val start = optStart.getOrElse(0)
      val end = start + optN.getOrElse(10)
      db.getBars(fooID).map{foos => Page(foos.slice(start, end), foos.length)}.flatMap(toTaskResponse).handleWith(toErrorResponse)
    case GET -> Root / "foo" / IntVar(fooID) / "bar" / IntVar(barID) =>
      db.getBar(barID, fooID).flatMap(toTaskResponse).handleWith(toErrorResponse)

    case POST -> Root / "foo" :? Name(name) =>
      db.addFoo(name).flatMap(toTaskResponse).handleWith(toErrorResponse)
    case POST -> Root / "foo" / IntVar(fooID) / "bar" :? Name(name) =>
      db.addBar(fooID, name).flatMap(toTaskResponse).handleWith(toErrorResponse)
    case POST -> Root / "foo" / "random" :? N(optN) => for {
      n <- optN.orElse(Some(1000)).toTask
      foo <- db.generateFooWithBars(n)
      response <- toTaskResponse(foo).handleWith(toErrorResponse)
    } yield response

    case DELETE -> Root / "foo" / IntVar(fooID) :? Retraverse(optRetraverse) => optRetraverse match {
      case Some(true) => db.removeFoo(fooID).flatMap(toTaskResponse).handleWith(toErrorResponse)
      case _ => db.removeFoo2(fooID).flatMap(toTaskResponse).handleWith(toErrorResponse)
    }
    case DELETE -> Root / "foo" / IntVar(fooID) / "bar" =>
      db.removeFoosChildren(fooID).flatMap(count => Ok(s"Removed ${count} children.")).handleWith(toErrorResponse)
    case DELETE -> Root / "foo" / IntVar(fooID) / "bar" / IntVar(barID) =>
      db.removeBar(fooID, barID).flatMap(toTaskResponse).handleWith(toErrorResponse)
  }

  val dbService = HttpService(handleRequest)
}

object Usage {
  val usage =
    """Available Routes:
      |GET /ping
      |GET /foo/{fooID}
      |GET /foo/{fooID}/bar
      |GET /foo/{fooID}/bar?start={startIndex=0}&n={length=10} # returns an JSON array of the Bars owned by that Foo
      |GET /foo/{fooID}/bar/{barID}
      |POST /foo/{fooID}?name={name}
      |POST /foo/{fooID}/bar?name={name}
      |POST /foo/random # creates a Foo with the next available fooID and gives it 1000 Bar children.
      |POST /foo/random?n={length=1000}
      |DELETE /foo/{fooID}
      |DELETE /foo/{fooID}/bar # deletes the children of Foo #fooID without deleting the node itself.
      |DELETE /foo/{fooID}/bar/{barID}""".stripMargin
}

object Start extends OptionalQueryParamDecoderMatcher[Int]("start")
object N extends OptionalQueryParamDecoderMatcher[Int]("n")
object Name extends QueryParamDecoderMatcher[String]("name")
object Retraverse extends OptionalQueryParamDecoderMatcher[Boolean]("retraverse")

case class Page[T](results: List[T], total: Int)