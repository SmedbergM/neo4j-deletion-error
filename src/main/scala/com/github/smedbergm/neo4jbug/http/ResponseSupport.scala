package com.github.smedbergm.neo4jbug.http

import com.github.smedbergm.neo4jbug.utils.{BadRequestException, EmptyOption, NonemptyOption, NotFoundException}
import org.http4s.{MediaType, Response}
import org.http4s.dsl._
import org.json4s.ShortTypeHints
import org.json4s.jackson.Serialization.write

import scalaz.concurrent.Task

trait ResponseSupport {
  implicit val formats = org.json4s.DefaultFormats + ShortTypeHints(
    classOf[BadRequestException] :: classOf[NotFoundException] :: Nil
  )

  def toTaskResponse[T <: AnyRef](t: T): Task[Response] = Ok(write(t)).withType(MediaType.`application/json`)

  val toErrorResponse: PartialFunction[Throwable, Task[Response]] = {
    val makeResponse: PartialFunction[Throwable, Task[Response]] = {
      case exc: BadRequestException => BadRequest(write(exc))
      case NonemptyOption => BadRequest(write(NonemptyOption))
      case exc: NotFoundException => NotFound(write(exc))
      case EmptyOption => NotFound(write(EmptyOption))
      case exc => InternalServerError(s"Unexpected error: ${exc.toString}")
    }
    makeResponse.andThen(response => response.withType(MediaType.`application/json`))
  }
}
