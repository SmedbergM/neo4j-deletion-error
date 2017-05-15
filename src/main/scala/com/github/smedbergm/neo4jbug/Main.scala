package com.github.smedbergm.neo4jbug

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ExecutorService, ThreadFactory}

import com.github.smedbergm.neo4jbug.neo4j.{ExampleDB4s, Foo, ExampleDB, ExampleDB4j}
import com.typesafe.scalalogging.LazyLogging
import scalaz.concurrent.Task

object Main extends App with LazyLogging {
  import Implicits._

  val storageDirectoryName = args.toList.sliding(2).collectFirst {
    case "-s" :: dir :: Nil => dir
    case "--storage" :: dir :: Nil => dir
  } orElse sys.props.get("user.home").map(_ + File.separator + ".neo4jbug" + File.separator + "db")
  val storageDirectory = new File(storageDirectoryName.getOrElse(sys.exit(1)))
  val db: ExampleDB = if (storageDirectory.isDirectory && storageDirectory.canWrite || storageDirectory.mkdirs()) {
    val db = new ExampleDB4j(storageDirectory)
    logger.info(s"Neo4j successfully started, storing data at ${storageDirectory.getAbsolutePath}")
    db
  } else {
    logger.error(s"Cannot write Neo4j to ${storageDirectory.getAbsolutePath}, exiting.")
    sys.exit(1)
  }

  (100 to 1000 by 100).foreach { n =>
    db.generateFooWithBars(n).map{ foo =>
      logger.info(s"Generated ${foo} with ${n} bars.")
    }.unsafePerformSync
  }

  val t: Task[List[Foo]] = db.getFoos().flatMap { foos =>
    Task.gatherUnordered(foos.map {
      case foo@Foo(id,_) if id%3 == 0 => Task {
        logger.info(s"Retrieved $foo from DB")
        foo
      }
      case foo@Foo(id,_) if id%3 == 1 => {
        logger.info(s"Removing $foo from DB")
        db.removeFoo(id)
      }
      case foo@Foo(id,_) if id%3 == 2 => {
        logger.info(s"Removing some bars owned by $foo")
        for {
          bars <- db.getBars(id)
          barsToRemove = bars.filter(_.id % 3 == 2)
          removed <- Task.gatherUnordered(barsToRemove.map(bar => db.removeBar(id, bar.id)), exceptionCancels = false)
            .handleWith{case exc =>
              logger.error(s"Encountered an error removing Bars from $foo", exc)
              Task.fail(exc)
            }
          _ = logger.info(s"Removed ${removed.length} Bars.")
        } yield foo
      }
    })
  }
  t.unsafePerformSync

  logger.info("Done, closing DB connection.")
  db.close()
  logger.info("DB connection closed, exiting.")
}

object Implicits {
  implicit val es: ExecutorService = Executors.newFixedThreadPool(100, new ThreadFactory {
    private val threadID = new AtomicInteger(0)
    override def newThread(r: Runnable): Thread = new Thread(r, f"neo4jbug-main-${threadID.getAndIncrement()}%02d")
  })
}