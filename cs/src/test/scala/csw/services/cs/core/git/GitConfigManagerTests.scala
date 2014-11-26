package csw.services.cs.core.git

import java.io.{File, FileNotFoundException, IOException}

import akka.actor.ActorSystem
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer
import csw.services.cs.akka.TestRepo
import csw.services.cs.core.ConfigData
import org.scalatest.FunSuite

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Tests the GitConfigManager class
 */
class GitConfigManagerTests extends FunSuite with LazyLogging {
  val path1 = new File("some/test1/TestConfig1")
  val path2 = new File("some/test2/TestConfig2")

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  implicit val system = ActorSystem()
  import system.dispatcher


  test("Test creating a GitConfigManager, storing and retrieving some files") {
    runTests(None, oversize = false)

    // Start the config service annex http server and wait for it to be ready for connections
    // (In normal operations, this server would already be running)
    val server = Await.result(ConfigServiceAnnexServer.startup(), 5.seconds)
    runTests(Some(server), oversize = true)
  }

  def runTests(annexServer: Option[ConfigServiceAnnexServer], oversize: Boolean): Unit = {
    logger.info(s"\n\n--- Testing config service: oversize = $oversize ---\n")

    val manager = TestRepo.getConfigManager()

    val result = for {
    // Try to update a file that does not exist (should fail)
      updateIdNull <- manager.update(path1, ConfigData(contents2), comment2) recover {
        case e: FileNotFoundException => null
      }

      // Add, then update the file twice
      createId1 <- manager.create(path1, ConfigData(contents1), oversize, comment1)
      createId2 <- manager.create(path2, ConfigData(contents1), oversize, comment1)
      updateId1 <- manager.update(path1, ConfigData(contents2), comment2)
      updateId2 <- manager.update(path1, ConfigData(contents3), comment3)

      // Check that we can access each version
      result1 <- manager.get(path1).flatMap(_.get.toFutureString)
      result2 <- manager.get(path1, Some(createId1)).flatMap(_.get.toFutureString)
      result3 <- manager.get(path1, Some(updateId1)).flatMap(_.get.toFutureString)
      result4 <- manager.get(path1, Some(updateId2)).flatMap(_.get.toFutureString)
      result5 <- manager.get(path2).flatMap(_.get.toFutureString)
      result6 <- manager.get(path2, Some(createId2)).flatMap(_.get.toFutureString)

      // test history()
      historyList1 <- manager.history(path1)
      historyList2 <- manager.history(path2)

      // test list()
      list <- manager.list()

      // Should throw exception if we try to create a file that already exists
      createIdNull <- manager.create(path1, ConfigData(contents2), oversize, comment2) recover {
        case e: IOException => null
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
      assert(createIdNull == null)

      assert(historyList1.size == 3)
      assert(historyList2.size == 1)
      assert(historyList1(0).comment == comment1)
      assert(historyList2(0).comment == comment1)
      assert(historyList1(1).comment == comment2)
      assert(historyList1(2).comment == comment3)

      assert(list.size == 2 + 1) // +1 for RENAME file added when creating the bare rep
      for (info <- list) {
        info.path match {
          case this.path1 => assert(info.comment == this.comment3)
          case this.path2 => assert(info.comment == this.comment1)
          case x => if (x.getName != "README") sys.error("Test failed for " + info)
        }
      }
    }

    Await.result(result, 30.seconds)
    if (annexServer.isDefined) {
      logger.info("Shutting down annex server")
      annexServer.get.shutdown()
    }
  }
}
