package com.github.smedbergm.neo4jbug.neo4j

import java.io.File

import org.apache.tinkerpop.gremlin.neo4j.structure.Neo4jGraph
import gremlin.scala._

class ExampleDB(storageDirectory: File) {
  val graphDB = Neo4jGraph.open(storageDirectory.getAbsolutePath).asScala
}

case class Foo(x: Int, name: String)