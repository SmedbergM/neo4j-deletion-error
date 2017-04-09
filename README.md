# neo4j-deletion-error
MWE displaying a concurrency bug in the Neo4j/gremlin/tinkerpop stack. After a vertex has been deleted, other processors may attempt to access that vertex, resulting in a `org.neo4j.graphdb.NotFoundException` caused by a `org.neo4j.kernel.api.exceptions.EntityNotFoundException`.

I suspect that this MWE will run without error on a single-processor machine, but I haven't actually tried that.

## To Run
To run from the project root directory,
```
neo4j-deletion-error$ sbt run
```

To compile and run from elsewhere, first do
```
neo4j-deletion-error$ sbt assembly
```
This will create a runnable JAR in `target/scala-2.11/` named something like `neo4j-deletion-error-assembly-0.1.0.jar`. This jar can now be renamed or moved wherever you'd like it.

The only command-line argument that this program accepts is

`-s [storageDirectory]`

This arg is optional; the default location is `HOME/.neo4jbug/db`.

## Logging
This program is configured to log at a fairly chatty level to both the console and to a logfile located at `HOME/.neo4jbug/neo4jbug.log`. This can all be changed by modifying `src/main/resources/logback.xml`.