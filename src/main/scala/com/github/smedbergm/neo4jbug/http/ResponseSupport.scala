package com.github.smedbergm.neo4jbug.http

import java.io.{ByteArrayOutputStream, PrintStream}

import com.github.smedbergm.neo4jbug.utils._
import com.typesafe.scalalogging.LazyLogging
import org.http4s.{MediaType, Response}
import org.http4s.dsl._
import org.json4s.ShortTypeHints
import org.json4s.jackson.Serialization.write

import scalaz.concurrent.Task

trait ResponseSupport extends LazyLogging {
  implicit val formats = org.json4s.DefaultFormats + ShortTypeHints(
    classOf[BadRequestException] :: classOf[NotFoundException] :: classOf[InternalServerException] :: Nil
  )

  def toTaskResponse[T <: AnyRef](t: T): Task[Response] = Ok(write(t)).withType(MediaType.`application/json`)

  val toErrorResponse: PartialFunction[Throwable, Task[Response]] = {
    val makeResponse: PartialFunction[Throwable, Task[Response]] = {
      case exc: BadRequestException => BadRequest(write(exc))
      case NonemptyOption => BadRequest(write(NonemptyOption))
      case exc: NotFoundException => NotFound(write(exc))
      case EmptyOption => NotFound(write(EmptyOption))
      case exc: InternalServerException => InternalServerError(write(exc))
      case exc =>
        logStackTrace(exc)
        InternalServerError(s"Unexpected error: ${exc.toString}")
    }
    makeResponse.andThen(response => response.withType(MediaType.`application/json`))
  }

  def logStackTrace(exc: Throwable): Unit = {
    val outputStream = new ByteArrayOutputStream()
    val printStream = new PrintStream(outputStream)
    exc.printStackTrace(printStream)
    logger.error(outputStream.toString)
  }
}
