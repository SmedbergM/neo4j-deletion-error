package com.github.smedbergm.neo4jbug.utils

sealed trait CustomException extends Throwable {
  val errorMessage: String
  override def getMessage: String = errorMessage
}

case class BadRequestException(errorMessage:String) extends CustomException

case class NotFoundException(errorMessage: String) extends CustomException