name := "neo4j-deletion-error"

version := "0.1.0"

scalaVersion := "2.11.8"

scalacOptions ++= Seq("-feature", "-deprecation", "-language:implicitConversions")

val http4sVersion = "0.15.3a"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.json4s" %% "json4s-jackson" % "3.5.0",
  "com.michaelpollmeier" %% "gremlin-scala" % "3.2.3.4",
  "org.apache.tinkerpop" % "neo4j-gremlin" % "3.2.3" exclude("com.github.jeremyh", "jBCrypt"),
  "org.neo4j" % "neo4j-tinkerpop-api-impl" % "0.4-3.0.3",
  "ch.qos.logback" % "logback-classic" % "1.2.1" // SLF4J implementation
)
