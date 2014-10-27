package csw.services.cs.akka

import java.net.URI

import org.scalatest.{FunSuiteLike, BeforeAndAfterAll}
import java.io.{File, IOException}
import csw.services.cs.core._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import scala.concurrent.duration._
import csw.services.cs.akka.ConfigServiceActor._
import scala.language.postfixOps

/**
 * Tests the Config Service actor usage for large binary files, where the content of the file checked in to
 * the repository is the URI pointing to the actual file.
 */
class ConfigServiceUriTests extends TestKit(ActorSystem("testsys"))
with ImplicitSender with FunSuiteLike with BeforeAndAfterAll {

  val uri1 = new URI("http://www.tmt.org/sites/default/files/documents/application/pdf/opsrd-ccr10.pdf")
  val path1 = new File("some/test1/" + new File(uri1.getPath).getName)
  val comment1 = "create comment 1"

  val uri2 = new URI("http://www.tmt.org/sites/default/files/documents/application/pdf/srd-ccr19.pdf")
  val path2 = new File("some/test2/" + new File(uri2.getPath).getName)
  val comment2 = "create comment 2"

  test("Test the ConfigServiceActor, storing and retrieving large/binary files with fixed URI") {
    // create a test repository and use it to create the actor
    val manager = TestRepo.getConfigManager("test3", create = true)(system.dispatcher)

    // Create the actor
    val configServiceActor = system.actorOf(ConfigServiceActor.props(manager), name = "configService")

    within(10 seconds) {
      configServiceActor ! CreateRequest(path1, new ConfigUri(uri1), comment1)
      val createId1 = checkCreateResult(path1)

      configServiceActor ! CreateRequest(path2, new ConfigUri(uri2), comment2)
      val createId2 = checkCreateResult(path2)

      // Should throw exception if we try to create a file that already exists
      configServiceActor ! CreateRequest(path1, new ConfigUri(uri1), comment1)
      checkCreateResultFailed(path1)

      // Check that we can access each version
      configServiceActor ! GetRequest(path1)
      checkGetResult(path1, uri1)

      configServiceActor ! GetRequest(path1, Some(createId1))
      checkGetResult(path1, uri1)

      configServiceActor ! GetRequest(path2)
      checkGetResult(path2, uri2)

      configServiceActor ! GetRequest(path2, Some(createId2))
      checkGetResult(path2, uri2)

      configServiceActor ! DeleteRequest(path1)
      checkDeleteResult(path1)

      configServiceActor ! ExistsRequest(path1)
      checkExistsResult(path1, exists = false)

      configServiceActor ! DeleteRequest(path2)
      checkDeleteResult(path2)

      configServiceActor ! ExistsRequest(path2)
      checkExistsResult(path2, exists = false)
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

//  def checkUpdateResult(path: File): ConfigId = {
//    val result = expectMsgType[UpdateResult]
//    assert(result.path == path)
//    assert(result.configId.isSuccess)
//    result.configId.get
//  }
//
//  def checkUpdateResultFailed(path: File): Unit = {
//    val result = expectMsgType[UpdateResult]
//    assert(result.path == path)
//    assert(result.configId.isFailure)
//    assert(result.configId.failed.get.isInstanceOf[IOException])
//  }

  def checkGetResult(path: File, contents: URI): Unit = {
    val result = expectMsgType[GetResult]
    checkGetResult(result, path, contents)
  }

  def checkGetResult(result: GetResult, path: File, contents: URI): Unit = {
    assert(result.path == path)
    assert(result.configData.isSuccess)
    val option = result.configData.get
    assert(option.isDefined)
    assert(new URI(option.get.toString) == contents)
  }

//  def checkHistoryResult(path: File, count: Int, comments: List[String]): Unit = {
//    val result = expectMsgType[HistoryResult]
//    assert(result.path == path)
//    assert(comments.size == count)
//    assert(result.history.isSuccess)
//    assert(result.history.get.map(_.comment) == comments)
//  }
//
//  def checkListResult(size: Int, comments: Map[File, String]): Unit = {
//    val result = expectMsgType[ListResult]
//    assert(result.list.isSuccess)
//    val list = result.list.get
//    assert(list.size == size + 1) // plus 1 for README file added when creating the bare repo
//    for (info <- list) {
//      if (info.path.getName != "README")
//        assert(info.comment == comments(info.path))
//    }
//  }

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
