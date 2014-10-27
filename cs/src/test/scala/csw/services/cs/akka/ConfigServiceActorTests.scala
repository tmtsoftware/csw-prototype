package csw.services.cs.akka

import org.scalatest.{FunSuiteLike, BeforeAndAfterAll}
import java.io.{File, IOException}
import csw.services.cs.core._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import scala.concurrent.duration._
import csw.services.cs.akka.ConfigServiceActor._
import csw.services.cs.core.ConfigString
import scala.concurrent.Await
import akka.pattern.ask
import akka.util.Timeout
import scala.language.postfixOps

/**
 * Tests the Config Service actor
 */
class ConfigServiceActorTests extends TestKit(ActorSystem("testsys"))
with ImplicitSender with FunSuiteLike with BeforeAndAfterAll {

  // Create temporary main (bare) and local git repos for testing
  val gitRepoPrefix = "test1"
  TestRepo.getConfigManager(gitRepoPrefix, create = true)(system.dispatcher)

  val path1 = new File("some/test1/TestConfig1")
  val path2 = new File("some/test2/TestConfig2")

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  test("Test the ConfigServiceActor, storing and retrieving some files") {
    // Use the test repository created above
    val manager = TestRepo.getConfigManager(gitRepoPrefix, create = false)(system.dispatcher)

    // Create the actor
    val configServiceActor = system.actorOf(ConfigServiceActor.props(manager), name = "configService")

    within(10 seconds) {
      // Should throw exception if we try to update a file that does not exist
      configServiceActor ! UpdateRequest(path1, new ConfigString(contents2), comment2)
      checkUpdateResultFailed(path1)

      // Add two files, then update the first file twice
      configServiceActor ! CreateRequest(path1, new ConfigString(contents1), comment1)
      val createId1 = checkCreateResult(path1)

      configServiceActor ! CreateRequest(path2, new ConfigString(contents1), comment1)
      val createId2 = checkCreateResult(path2)

      configServiceActor ! UpdateRequest(path1, new ConfigString(contents2), comment2)
      val updateId1 = checkUpdateResult(path1)

      configServiceActor ! UpdateRequest(path1, new ConfigString(contents3), comment3)
      val updateId2 = checkUpdateResult(path1)

      // Should throw exception if we try to create a file that already exists
      configServiceActor ! CreateRequest(path1, new ConfigString(contents2), comment2)
      checkCreateResultFailed(path1)

      // Check that we can access each version
      configServiceActor ! GetRequest(path1)
      checkGetResult(path1, contents3)

      configServiceActor ! GetRequest(path1, Some(createId1))
      checkGetResult(path1, contents1)

      configServiceActor ! GetRequest(path1, Some(updateId1))
      checkGetResult(path1, contents2)

      configServiceActor ! GetRequest(path1, Some(updateId2))
      checkGetResult(path1, contents3)

      configServiceActor ! GetRequest(path2)
      checkGetResult(path2, contents1)

      configServiceActor ! GetRequest(path2, Some(createId2))
      checkGetResult(path2, contents1)

      // Test using '?' instead of '!'
      implicit val timeout = Timeout(2.seconds)
      val result = Await.result(configServiceActor ? GetRequest(path2, Some(createId2)),
        2.seconds).asInstanceOf[GetResult]
      checkGetResult(result, path2, contents1)

      // test history()
      configServiceActor ! HistoryRequest(path1)
      checkHistoryResult(path1, 3, List(comment1, comment2, comment3))

      configServiceActor ! HistoryRequest(path2)
      checkHistoryResult(path2, 1, List(comment1))

      // Test list()
      configServiceActor ! ListRequest
      checkListResult(2, Map(path1 -> comment3, path2 -> comment1))

      // Test getting history of document that has been deleted
      configServiceActor ! DeleteRequest(path1, "test delete")
      checkDeleteResult(path1)

      configServiceActor ! ExistsRequest(path1)
      checkExistsResult(path1, exists = false)

      configServiceActor ! DeleteRequest(path2)
      checkDeleteResult(path2)

      configServiceActor ! ExistsRequest(path2)
      checkExistsResult(path2, exists = false)

      configServiceActor ! HistoryRequest(path1)
      checkHistoryResult(path1, 3, List(comment1, comment2, comment3))

      configServiceActor ! HistoryRequest(path2)
      checkHistoryResult(path2, 1, List(comment1))
    }
  }

  def checkCreateResult(path: File): ConfigId = {
    val result = expectMsgType[CreateResult]
    assert(result.path == path)
    assert(result.configId.isSuccess)
    result.configId.get
  }

  def checkCreateResultFailed(path: File): Unit = {
    val result = expectMsgType[CreateResult]
    assert(result.path == path)
    assert(result.configId.isFailure)
    assert(result.configId.failed.get.isInstanceOf[IOException])
  }

  def checkUpdateResult(path: File): ConfigId = {
    val result = expectMsgType[UpdateResult]
    assert(result.path == path)
    assert(result.configId.isSuccess)
    result.configId.get
  }

  def checkUpdateResultFailed(path: File): Unit = {
    val result = expectMsgType[UpdateResult]
    assert(result.path == path)
    assert(result.configId.isFailure)
    assert(result.configId.failed.get.isInstanceOf[IOException])
  }

  def checkGetResult(path: File, contents: String): Unit = {
    val result = expectMsgType[GetResult]
    checkGetResult(result, path, contents)
  }

  def checkGetResult(result: GetResult, path: File, contents: String): Unit = {
    assert(result.path == path)
    assert(result.configData.isSuccess)
    val option = result.configData.get
    assert(option.isDefined)
    assert(option.get.toString == contents)
  }

  def checkHistoryResult(path: File, count: Int, comments: List[String]): Unit = {
    val result = expectMsgType[HistoryResult]
    assert(result.path == path)
    assert(comments.size == count)
    assert(result.history.isSuccess)
    assert(result.history.get.map(_.comment) == comments)
  }

  def checkListResult(size: Int, comments: Map[File, String]): Unit = {
    val result = expectMsgType[ListResult]
    assert(result.list.isSuccess)
    val list = result.list.get
    assert(list.size == size + 1) // plus 1 for README file added when creating the bare repo
    for (info <- list) {
      if (info.path.getName != "README")
        assert(info.comment == comments(info.path))
    }
  }

  def checkDeleteResult(path: File): Unit = {
    val result = expectMsgType[DeleteResult]
    assert(result.path == path)
    assert(result.status.isSuccess)
  }

  def checkExistsResult(path: File, exists: Boolean): Unit = {
    val result = expectMsgType[ExistsResult]
    assert(result.path == path)
    assert(result.exists.isSuccess)
    assert(result.exists.get == exists)
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}
