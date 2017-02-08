package com.github.smedbergm.neo4jbug

import com.github.smedbergm.neo4jbug.http.DBService
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.{Server, ServerApp}

import scalaz.concurrent.Task

object Main extends ServerApp {
  override def server(args: List[String]): Task[Server] = {
    BlazeBuilder.bindHttp(8080, "localhost")
      .mountService(DBService.dbService)
      .start
  }

  println("Hello world!")
}
