package com.github.smedbergm.neo4jbug

import com.github.smedbergm.neo4jbug.http.DBService
import com.github.smedbergm.neo4jbug.neo4j.ExampleDB
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.{Server, ServerApp}

import scalaz.concurrent.Task

object Main extends ServerApp {
  val dbPath = sys.env.getOrElse("NEO4JPATH","/tmp/neo")
  val dbDir = new java.io.File(dbPath)
  val db = new ExampleDB(dbDir)
  val dBService = new DBService(db)

  override def server(args: List[String]): Task[Server] = {
    BlazeBuilder.bindHttp(8080, "localhost")
      .mountService(dBService.dbService)
      .start
  }

  println("Hello world!")
}
