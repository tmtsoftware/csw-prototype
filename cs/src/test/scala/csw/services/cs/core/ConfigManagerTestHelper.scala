package csw.services.cs.core

import java.io.{ File, IOException }

import akka.actor.ActorSystem
import com.typesafe.scalalogging.slf4j.Logger
import org.scalatest.FunSuite
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{ Failure, Success }

/**
 * Common test code for classes that implement the ConfigManager trait
 */
object ConfigManagerTestHelper extends FunSuite {
  val logger = Logger(LoggerFactory.getLogger("ConfigManagerTestHelper"))

  val path1 = new File("some/test1/TestConfig1")
  val path2 = new File("some/test2/TestConfig2")

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  // Run tests using the given config manager instance
  def runTests(manager: ConfigManager, oversize: Boolean)(implicit system: ActorSystem): Future[Unit] = {
    import system.dispatcher
    val result = for {
      // Try to update a file that does not exist (should fail)
      updateIdNull ← manager.update(path1, ConfigData(contents2), comment2) recover {
        case e: IOException ⇒ null
      }

      // Add, then update the file twice
      createId1 ← manager.create(path1, ConfigData(contents1), oversize, comment1)
      createId2 ← manager.createOrUpdate(path2, ConfigData(contents1), oversize, comment1)
      updateId1 ← manager.update(path1, ConfigData(contents2), comment2)
      updateId2 ← manager.createOrUpdate(path1, ConfigData(contents3), oversize, comment3)

      // Check that we can access each version
      result1 ← manager.get(path1).flatMap(_.get.toFutureString)
      result2 ← manager.get(path1, Some(createId1)).flatMap(_.get.toFutureString)
      result3 ← manager.get(path1, Some(updateId1)).flatMap(_.get.toFutureString)
      result4 ← manager.get(path1, Some(updateId2)).flatMap(_.get.toFutureString)
      result5 ← manager.get(path2).flatMap(_.get.toFutureString)
      result6 ← manager.get(path2, Some(createId2)).flatMap(_.get.toFutureString)

      historyList1 ← manager.history(path1)
      historyList2 ← manager.history(path2)

      // Test default file features
      default1 ← manager.getDefault(path1).flatMap(_.get.toFutureString)
      _ ← manager.setDefault(path1, Some(updateId1))
      default2 ← manager.getDefault(path1).flatMap(_.get.toFutureString)
      _ ← manager.resetDefault(path1)
      default3 ← manager.getDefault(path1).flatMap(_.get.toFutureString)
      _ ← manager.setDefault(path1, Some(updateId2))

      // test list()
      list ← manager.list()

      // Should throw exception if we try to create a file that already exists
      createIdNull ← manager.create(path1, ConfigData(contents2), oversize, comment2) recover {
        case e: IOException ⇒ null
      }

    } yield {
      // At this point all of the above Futures have completed,so we can do some tests
      assert(updateIdNull == null)

      assert(result1 == contents3)
      assert(result2 == contents1)
      assert(result3 == contents2)
      assert(result4 == contents3)
      assert(result5 == contents1)
      assert(result6 == contents1)

      assert(historyList1.size == 3)
      assert(historyList2.size == 1)
      assert(historyList1(0).comment == comment3)
      assert(historyList2(0).comment == comment1)
      assert(historyList1(1).comment == comment2)
      assert(historyList1(2).comment == comment1)

      assert(list.size == 3) // +1 for README file added when creating the bare rep
      for (info ← list) {
        info.path match {
          case this.path1 ⇒ assert(info.comment == this.comment3)
          case this.path2 ⇒ assert(info.comment == this.comment1)
          case x          ⇒ if (x.getName != "README") sys.error("Test failed for " + info)
        }
      }

      assert(default1 == contents3)
      assert(default2 == contents2)
      assert(default3 == contents3)

      assert(createIdNull == null)

      logger.info(s"\n\n runTests passed\n\n")
    }
    result.onComplete {
      case Success(_) ⇒
        logger.info("runTest done")
      case Failure(ex) ⇒
        logger.error("runTest failed", ex)
    }
    result
  }

  // Verify that a second config service can still see all the files that were checked in by the first
  def runTests2(manager: ConfigManager, oversize: Boolean)(implicit system: ActorSystem): Future[Unit] = {
    import system.dispatcher

    // Sequential, non-blocking for-comprehension
    val result = for {
      // Check that we can access each version
      result1 ← manager.get(path1).flatMap(_.get.toFutureString)
      result5 ← manager.get(path2).flatMap(_.get.toFutureString)

      // test history()
      historyList1 ← manager.history(path1)
      historyList2 ← manager.history(path2)

      // test list()
      list ← manager.list()

      // Should throw exception if we try to create a file that already exists
      createIdNull ← manager.create(path1, ConfigData(contents2), oversize, comment2) recover {
        case e: IOException ⇒ null
      }
    } yield {
      // At this point all of the above Futures have completed,so we can do some tests
      assert(result1 == contents3)
      assert(result5 == contents1)
      assert(createIdNull == null)

      assert(historyList1.size == 3)
      assert(historyList2.size == 1)
      assert(historyList1(0).comment == comment3)
      assert(historyList2(0).comment == comment1)
      assert(historyList1(1).comment == comment2)
      assert(historyList1(2).comment == comment1)

      assert(list.size == 3) // +1 for README file added when creating the bare rep
      for (info ← list) {
        info.path match {
          case this.path1 ⇒ assert(info.comment == this.comment3)
          case this.path2 ⇒ assert(info.comment == this.comment1)
          case x          ⇒ if (x.getName != "README") sys.error("Test failed for " + info)
        }
      }

      logger.info(s"\n\n runTests2 passed\n\n")
    }
    result.onComplete {
      case Success(_) ⇒
        logger.info("runTest done")
      case Failure(ex) ⇒
        logger.error("runTest failed", ex)
    }
    result
  }
}