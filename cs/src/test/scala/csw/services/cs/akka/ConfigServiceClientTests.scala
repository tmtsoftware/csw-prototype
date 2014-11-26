package csw.services.cs.akka

import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer
import org.scalatest.{FunSuiteLike, BeforeAndAfterAll}
import java.io.{File, FileNotFoundException, IOException}
import csw.services.cs.core.ConfigData
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import scala.concurrent.duration._
import scala.concurrent.Await


/**
 * Tests the Config Service actor
 */
class ConfigServiceClientTests extends TestKit(ActorSystem("mySystem"))
with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {

  import system.dispatcher
  implicit val timeout: Timeout = 30.seconds

  val path1 = new File("some/test1/TestConfig1")
  val path2 = new File("some/test2/TestConfig2")

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  test("Test the ConfigServiceClent, storing and retrieving some files") {
    runTests(None, oversize = false)

    // Start the config service annex http server and wait for it to be ready for connections
    // (In normal operations, this server would already be running)
    val server = Await.result(ConfigServiceAnnexServer.startup(), 5.seconds)
    runTests(Some(server), oversize = true)
  }

  // Runs the tests for the config service, using the given oversize option.
  def runTests(annexServer: Option[ConfigServiceAnnexServer], oversize: Boolean) : Unit = {
    logger.info(s"--- Testing config service: oversize = $oversize ---")

    // create a test repository and use it to create the actor
    val manager = TestRepo.getConfigManager()

    // Create the actor
    val csActor = system.actorOf(ConfigServiceActor.props(manager), name = "configService")
    val csClient = ConfigServiceClient(system, csActor, manager.name)

    // Sequential, non-blocking for-comprehension
    val result = for {
    // Try to update a file that does not exist (should fail)
      updateIdNull <- csClient.update(path1, ConfigData(contents2), comment2) recover {
        case e: FileNotFoundException => null
      }

      // Add, then update the file twice
      createId1 <- csClient.create(path1, ConfigData(contents1), oversize, comment1)
      createId2 <- csClient.create(path2, ConfigData(contents1), oversize, comment1)
      updateId1 <- csClient.update(path1, ConfigData(contents2), comment2)
      updateId2 <- csClient.update(path1, ConfigData(contents3), comment3)

      // Check that we can access each version
      result1 <- manager.get(path1).flatMap(_.get.toFutureString)
      result2 <- manager.get(path1, Some(createId1)).flatMap(_.get.toFutureString)
      result3 <- manager.get(path1, Some(updateId1)).flatMap(_.get.toFutureString)
      result4 <- manager.get(path1, Some(updateId2)).flatMap(_.get.toFutureString)
      result5 <- manager.get(path2).flatMap(_.get.toFutureString)
      result6 <- manager.get(path2, Some(createId2)).flatMap(_.get.toFutureString)

      // test history()
      historyList1 <- csClient.history(path1)
      historyList2 <- csClient.history(path2)

      // test list()
      list <- csClient.list()

      // Should throw exception if we try to create a file that already exists
      createIdNull <- csClient.create(path1, ConfigData(contents2), oversize, comment2) recover {
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
      system.stop(csActor)

      if (annexServer.isDefined) {
        logger.info("Shutting down annex server")
        annexServer.get.shutdown()
      }
    }

    Await.result(result, 30.seconds)
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}
