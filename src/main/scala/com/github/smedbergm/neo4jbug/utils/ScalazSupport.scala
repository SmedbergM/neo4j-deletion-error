package com.github.smedbergm.neo4jbug.utils

import scalaz.concurrent.Task


trait ScalazSupport {
  case class RichOption[T](optT: Option[T]) {
    def toTask: Task[T] = optT.map(t => Task.now(t)).getOrElse(Task.fail(EmptyOption))
    def succeedIfEmpty: Task[Unit] = if (optT.isEmpty) {
      Task.now(())
    } else {
      Task.fail(NonemptyOption)
    }
  }

  implicit def toRich[T](optT: Option[T]): RichOption[T] = RichOption(optT)
  implicit def fromRich[T](rOptT: RichOption[T]): Option[T] = rOptT match {
    case RichOption(optT) => optT
  }
}

object EmptyOption extends Throwable
object NonemptyOption extends Throwable