package csw.services.cs.akka

import org.scalatest.{FunSuiteLike, BeforeAndAfterAll}
import java.io.{File, FileNotFoundException, IOException}
import csw.services.cs.core.ConfigString
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import scala.Some
import scala.concurrent.duration._
import scala.concurrent.Await
import akka.util.Timeout


/**
 * Tests the Config Service actor
 */
class ConfigServiceClientTests extends TestKit(ActorSystem("mySystem"))
with ImplicitSender with FunSuiteLike with BeforeAndAfterAll {

  import system.dispatcher

  val path1 = new File("some/test1/TestConfig1")
  val path2 = new File("some/test2/TestConfig2")

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  test("Test the ConfigServiceClent, storing and retrieving some files") {
    implicit val timeout = Timeout(5.seconds)

    // create a test repository and use it to create the actor
    val manager = TestRepo.getConfigManager("test2", create = true)(system.dispatcher)

    // Create the actor
    val csActor = system.actorOf(ConfigServiceActor.props(manager), name = "configService")
    val csClient = ConfigServiceClient(system, csActor)

    // Sequential, non-blocking for comprehension
    Await.result(for {
    // Try to update a file that does not exist (should fail)
      updateIdNull <- csClient.update(path1, new ConfigString(contents2), comment2) recover {
        case e: FileNotFoundException => null
      }

      // Add, then update the file twice
      createId1 <- csClient.create(path1, new ConfigString(contents1), comment1)
      createId2 <- csClient.create(path2, new ConfigString(contents1), comment1)
      updateId1 <- csClient.update(path1, new ConfigString(contents2), comment2)
      updateId2 <- csClient.update(path1, new ConfigString(contents3), comment3)

      // Check that we can access each version
      option1 <- csClient.get(path1)
      option2 <- csClient.get(path1, Some(createId1))
      option3 <- csClient.get(path1, Some(updateId1))
      option4 <- csClient.get(path1, Some(updateId2))
      option5 <- csClient.get(path2)
      option6 <- csClient.get(path2, Some(createId2))

      // test history()
      historyList1 <- csClient.history(path1)
      historyList2 <- csClient.history(path2)

      // test list()
      list <- csClient.list()

      // Should throw exception if we try to create a file that already exists
      createIdNull <- csClient.create(path1, new ConfigString(contents2), comment2) recover {
        case e: IOException => null
      }
    } yield {
      // At this point all of the above Futures have completed,so we can do some tests
      assert(updateIdNull == null)
      assert(!option1.isEmpty && option1.get.toString == contents3)
      assert(!option2.isEmpty && option2.get.toString == contents1)
      assert(!option3.isEmpty && option3.get.toString == contents2)
      assert(!option4.isEmpty && option4.get.toString == contents3)
      assert(!option5.isEmpty && option5.get.toString == contents1)
      assert(!option6.isEmpty && option6.get.toString == contents1)
      assert(createIdNull == null)

      assert(historyList1.size == 3)
      assert(historyList2.size == 1)
      assert(historyList1(0).comment == comment1)
      assert(historyList2(0).comment == comment1)
      assert(historyList1(1).comment == comment2)
      assert(historyList1(2).comment == comment3)

      assert(list.size == 2 + 1) // +1 for REAME file added when creating the bare rep
      for (info <- list) {
        info.path match {
          case this.path1 => assert(info.comment == this.comment3)
          case this.path2 => assert(info.comment == this.comment1)
          case x => if (x.getName != "README") sys.error("Test failed for " + info)
        }
      }
    }, 5.seconds)
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}
