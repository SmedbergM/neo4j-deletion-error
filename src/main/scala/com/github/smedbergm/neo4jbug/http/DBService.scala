package com.github.smedbergm.neo4jbug.http

import org.http4s._
import org.http4s.dsl._

import scalaz.concurrent.Task

object DBService {
  val handleRequest: PartialFunction[Request, Task[Response]] = {
    case GET -> Root => Ok("Hello, World!")
    case GET -> Root / "ping" => Ok("pong")
  }

  val dbService = HttpService(handleRequest)
}
