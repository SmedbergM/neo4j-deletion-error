package com.github.smedbergm.neo4jbug.neo4j

import scalaz.concurrent.Task

trait ExampleDB {
  def addFoo(foo: Foo): Task[Foo]
  def getFoos(start: Int = 0, n: Int = Int.MaxValue): Task[List[Foo]]
  def removeFoo(id: Int): Task[Foo]
  def generateFooWithBars(n: Int): Task[Foo]
  def getBars(fooId: Int): Task[List[Bar]]
  def removeBar(fooID: Int, barId: Int): Task[Bar]

  def close(): Unit

}

case class Foo(id: Int, name: String)
case class Bar(id: Int, name: String)