package csw.services.cs.akka

import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer
import org.scalatest.{FunSuiteLike, BeforeAndAfterAll}
import java.io.{File, IOException}
import csw.services.cs.core._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import scala.concurrent.duration._
import csw.services.cs.core.ConfigData
import scala.concurrent.Await
import akka.pattern.ask
import akka.util.Timeout
import scala.language.postfixOps
import csw.services.cs.akka.ConfigServiceActor._

import scala.util.{Failure, Success}

/**
 * Tests the Config Service actor
 */
class ConfigServiceActorTests extends TestKit(ActorSystem("testsys"))
    with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {

  val path1 = new File("some/test1/TestConfig1")
  val path2 = new File("some/test2/TestConfig2")

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  test("Test the ConfigServiceActor, storing and retrieving some files") {
    runTests(None, oversize = false)

    // Start the config service annex http server and wait for it to be ready for connections
    // (In normal operations, this server would already be running)
    val server = ConfigServiceAnnexServer()
    try {
      runTests(Some(server), oversize = true)
    } finally {
      server.shutdown()
    }
  }

  // Runs the tests for the config service, using the given oversize option.
  def runTests(annexServer: Option[ConfigServiceAnnexServer], oversize: Boolean): Unit = {
    logger.debug(s"\n\n--- Testing config service: oversize = $oversize ---\n")

    // create a test repository and use it to create the actor
    val manager = TestRepo.getTestRepoConfigManager()

    // Create the actor
    val configServiceActor = system.actorOf(ConfigServiceActor.props(manager), name = "configService")
    //    configServiceActor ! RegisterWithLocationService

    within(20 seconds) {
      // Should throw exception if we try to update a file that does not exist
      configServiceActor ! UpdateRequest(path1, ConfigData(contents2), comment2)
      checkUpdateResultFailed(path1)

      // Add two files, then update the first file twice
      configServiceActor ! CreateRequest(path1, ConfigData(contents1), oversize, comment1)
      val createId1 = checkCreateResult(path1)

      configServiceActor ! CreateRequest(path2, ConfigData(contents1), oversize, comment1)
      val createId2 = checkCreateResult(path2)

      configServiceActor ! UpdateRequest(path1, ConfigData(contents2), comment2)
      val updateId1 = checkUpdateResult(path1)

      configServiceActor ! UpdateRequest(path1, ConfigData(contents3), comment3)
      val updateId2 = checkUpdateResult(path1)

      // Should throw exception if we try to create a file that already exists
      configServiceActor ! CreateRequest(path1, ConfigData(contents2), oversize, comment2)
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
      val result = Await.result(
        configServiceActor ? GetRequest(path2, Some(createId2)),
        2.seconds
      ).asInstanceOf[GetResult]
      checkGetResult(result, path2, contents1)

      // test history()
      configServiceActor ! HistoryRequest(path1)
      checkHistoryResult(path1, 3, List(comment3, comment2, comment1))

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

      // Choice TODO FIXME: Doesn't work to get history of deleted file with svnkit
      // Choice TODO FIXME unless you specify an existing revision (works ok with jgit)

      //      configServiceActor ! HistoryRequest(path1)
      //      checkHistoryResult(path1, 3, List(comment3, comment2, comment1))
      //
      //      configServiceActor ! HistoryRequest(path2)
      //      checkHistoryResult(path2, 1, List(comment1))

      system.stop(configServiceActor)

      if (annexServer.isDefined) {
        logger.debug("Shutting down annex server")
        annexServer.get.shutdown()
      }
    }
  }

  def checkCreateResult(path: File): ConfigId = {
    val result = expectMsgType[CreateOrUpdateResult]
    assert(result.path == path)
    assert(result.configId.isSuccess)
    result.configId.get
  }

  def checkCreateResultFailed(path: File): Unit = {
    val result = expectMsgType[CreateOrUpdateResult]
    assert(result.path == path)
    assert(result.configId.isFailure)
    assert(result.configId.failed.get.isInstanceOf[IOException])
  }

  def checkUpdateResult(path: File): ConfigId = {
    val result = expectMsgType[CreateOrUpdateResult]
    assert(result.path == path)
    assert(result.configId.isSuccess)
    result.configId.get
  }

  def checkUpdateResultFailed(path: File): Unit = {
    val result = expectMsgType[CreateOrUpdateResult]
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
    assert(Await.result(option.get.toFutureString, 5.seconds) == contents)
  }

  def checkHistoryResult(path: File, count: Int, comments: List[String]): Unit = {
    val result = expectMsgType[HistoryResult]
    result.history match {
      case Success(v)  => assert(v.map(_.comment) == comments)
      case Failure(ex) => throw ex
    }
    assert(result.path == path)
    assert(comments.size == count)
  }

  def checkListResult(size: Int, comments: Map[File, String]): Unit = {
    val result = expectMsgType[ListResult]
    assert(result.list.isSuccess)
    val list = result.list.get
    assert(list.size >= size) // plus 1 for README file (git) or *.default file, etc.
    for (info <- list) {
      if (comments.contains(info.path))
        assert(info.comment == comments(info.path))
    }
  }

  def checkDeleteResult(path: File): Unit = {
    val result = expectMsgType[UnitResult]
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
    system.terminate()
  }
}
