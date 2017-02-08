name := "neo4j-deletion-error"

version := "0.1.0"

scalaVersion := "2.11.8"

val http4sVersion = "0.15.3"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "com.michaelpollmeier" %% "gremlin-scala" % "3.2.3.4"
)
