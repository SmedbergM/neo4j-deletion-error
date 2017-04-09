import sbtassembly.PathList

name := "neo4j-deletion-error"

version := "0.1.0"

scalaVersion := "2.11.8"

scalacOptions ++= Seq("-feature", "-deprecation", "-language:implicitConversions")
logLevel := Level.Warn

val http4sVersion = "0.15.3a"

libraryDependencies ++= Seq(
  "org.scalaz" % "scalaz-concurrent_2.10" % "7.2.8",
  "org.json4s" %% "json4s-jackson" % "3.5.0",
  "com.michaelpollmeier" %% "gremlin-scala" % "3.2.3.4",
  "org.apache.tinkerpop" % "neo4j-gremlin" % "3.2.3" exclude("com.github.jeremyh", "jBCrypt"),
  "org.neo4j" % "neo4j-tinkerpop-api-impl" % "0.4-3.0.3",
  "com.typesafe.scala-logging"  %% "scala-logging"              % "3.1.0",
  "ch.qos.logback" % "logback-classic" % "1.2.1" // SLF4J implementation
)

assemblyMergeStrategy in assembly := {
  case PathList(ps @ _*) if ps.last.equalsIgnoreCase("manifest.mf") => MergeStrategy.discard
  case PathList("META-INF", _*) => MergeStrategy.filterDistinctLines
  case PathList("pom.xml") => MergeStrategy.filterDistinctLines
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}