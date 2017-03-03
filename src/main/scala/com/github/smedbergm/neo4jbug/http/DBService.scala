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

    case GET -> Root / "foo" / IntVar(fooID) =>
      db.getFoo(fooID).flatMap(toTaskResponse).handleWith(toErrorResponse)
    case GET -> Root / "foo" / IntVar(fooID) / "bar" :? Start(optStart) +& N(optN) =>
      val start = optStart.getOrElse(0)
      val end = start + optN.getOrElse(10)
      db.getBars(fooID).map{foos => foos.slice(start, end)}.flatMap(toTaskResponse).handleWith(toErrorResponse)
    case GET -> Root / "foo" / IntVar(fooID) / "bar" / IntVar(barID) =>
      db.getBar(barID, fooID).flatMap(toTaskResponse).handleWith(toErrorResponse)
    case PUT -> Root / "foo" / IntVar(fooID) :? Name(name) =>
      db.addFoo(Foo(fooID, name)).flatMap(toTaskResponse).handleWith(toErrorResponse)
    case PUT -> Root / "foo" / IntVar(fooID) / "bar" / IntVar(barID) :? Name(name) =>
      db.addBar(Bar(barID, name), fooID).flatMap(toTaskResponse).handleWith(toErrorResponse)
    case PUT -> Root / "foo" / "random" :? N(optN) +& SingleTransaction(optSingleTransaction) => for {
      n <- optN.orElse(Some(1000)).toTask
      singleTransaction <- optSingleTransaction.orElse(Some(true)).toTask
      foo <- db.generateFooWithBars(n, singleTransaction)
      response <- toTaskResponse(foo).handleWith(toErrorResponse)
    } yield response
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
      |PUT /foo/{fooID}?name={name}
      |PUT /foo/{fooID}/bar/{barID}?name={name}
      |PUT /foo/random # puts a foo with the next available fooID and gives it 1000 Bar children.""".stripMargin
}

object Start extends OptionalQueryParamDecoderMatcher[Int]("start")
object N extends OptionalQueryParamDecoderMatcher[Int]("n")
object Name extends QueryParamDecoderMatcher[String]("name")
object SingleTransaction extends OptionalQueryParamDecoderMatcher[Boolean]("single-transaction")