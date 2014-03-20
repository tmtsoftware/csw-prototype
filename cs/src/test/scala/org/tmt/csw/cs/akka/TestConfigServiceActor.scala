package org.tmt.csw.cs.akka

import org.scalatest.{FunSuiteLike, BeforeAndAfterAll}
import java.io.{File, IOException}
import org.tmt.csw.cs.core.ConfigString
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import scala.Some
import org.tmt.csw.cs.api._
import ConfigServiceActor._

/**
 * Tests the Config Service actor
 */
class TestConfigServiceActor extends TestKit(ActorSystem("testsys")) with ImplicitSender with FunSuiteLike with BeforeAndAfterAll {

  val path1 = new File("some/test1/TestConfig1")
  val path2 = new File("some/test2/TestConfig2")

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  val duration = 5.seconds
  implicit val timeout = Timeout(5.seconds)

  // Note: Using blocking (Await) for this test, so we can compare return values easily.
  // Applications should not need to block while waiting. See TestConfigServiceClient in this directory
  // for a different way of doing it.
  test("Test the ConfigServiceActor, storing and retrieving some files") {
    // create a test repository and use it to create the actor
    val manager = TestRepo.getConfigManager("test1")

    // Create the actor
    val configServiceActor = system.actorOf(ConfigServiceActor.props(manager), name = "configService")

    // Should throw exception if we try to update a file that does not exist
    intercept[IOException] {
      Await.result(configServiceActor ?
        UpdateRequest(path1, new ConfigString(contents2), comment2),
        duration).asInstanceOf[ConfigId]
    }

    // Add, then update the file twice
    val createId1 = Await.result(configServiceActor ?
      CreateRequest(path1, new ConfigString(contents1), comment1),
      duration).asInstanceOf[ConfigId]

    val createId2 = Await.result(configServiceActor ?
      CreateRequest(path2, new ConfigString(contents1), comment1),
      duration).asInstanceOf[ConfigId]

    val updateId1 = Await.result(configServiceActor ?
      UpdateRequest(path1, new ConfigString(contents2), comment2),
      duration).asInstanceOf[ConfigId]

    val updateId2 = Await.result(configServiceActor ?
      UpdateRequest(path1, new ConfigString(contents3), comment3),
      duration).asInstanceOf[ConfigId]

    // Should throw exception if we try to create a file that already exists
    intercept[IOException] {
      Await.result(configServiceActor ?
        CreateRequest(path1, new ConfigString(contents2), comment2),
        duration).asInstanceOf[ConfigId]
    }

    // Check that we can access each version
    val option1 = Await.result(configServiceActor ?
      GetRequest(path1),
      duration).asInstanceOf[Option[ConfigData]]
    assert(!option1.isEmpty)
    assert(option1.get.toString == contents3)

    val option2 = Await.result(configServiceActor ?
      GetRequest(path1, Some(createId1)),
      duration).asInstanceOf[Option[ConfigData]]
    assert(!option2.isEmpty)
    assert(option2.get.toString == contents1)

    val option3 = Await.result(configServiceActor ?
      GetRequest(path1, Some(updateId1)),
      duration).asInstanceOf[Option[ConfigData]]
    assert(!option3.isEmpty)
    assert(option3.get.toString == contents2)

    val option4 = Await.result(configServiceActor ?
      GetRequest(path1, Some(updateId2)),
      duration).asInstanceOf[Option[ConfigData]]
    assert(!option4.isEmpty)
    assert(option4.get.toString == contents3)

    val option5 = Await.result(configServiceActor ?
      GetRequest(path2),
      duration).asInstanceOf[Option[ConfigData]]
    assert(!option5.isEmpty)
    assert(option5.get.toString == contents1)

    val option6 = Await.result(configServiceActor ?
      GetRequest(path2, Some(createId2)),
      duration).asInstanceOf[Option[ConfigData]]
    assert(!option6.isEmpty)
    assert(option6.get.toString == contents1)

    // test history()
    val historyList1 = Await.result(configServiceActor ?
      HistoryRequest(path1),
      duration).asInstanceOf[List[ConfigFileHistory]]
    val historyList2 = Await.result(configServiceActor ?
      HistoryRequest(path2),
      duration).asInstanceOf[List[ConfigFileHistory]]

    assert(historyList1.size >= 3)
    assert(historyList2.size >= 1)

    assert(historyList1(0).comment == comment1)
    assert(historyList2(0).comment == comment1)
    assert(historyList1(1).comment == comment2)
    assert(historyList1(2).comment == comment3)

    // Test list()
    val list = Await.result(configServiceActor ?
      ListRequest,
      duration).asInstanceOf[List[ConfigFileInfo]]
    assert(list.size == 2)
    for (info <- list) {
      info.path match {
        case this.path1 => {
          assert(info.comment == this.comment3)
        }
        case this.path2 => {
          assert(info.comment == this.comment1)
        }
        case _ => sys.error("Test failed for " + info)
      }
    }

    // Test getting history of document that has been deleted
    Await.result(configServiceActor ? DeleteRequest(path1, "test delete"), duration)
    assert(!Await.result(configServiceActor ? ExistsRequest(path1), duration).asInstanceOf[Boolean])

    Await.result(configServiceActor ? DeleteRequest(path2), duration)
    assert(!Await.result(configServiceActor ? ExistsRequest(path2), duration).asInstanceOf[Boolean])

    val historyList1d = Await.result(configServiceActor ?
      HistoryRequest(path1),
      duration).asInstanceOf[List[ConfigFileHistory]]
    val historyList2d = Await.result(configServiceActor ?
      HistoryRequest(path2),
      duration).asInstanceOf[List[ConfigFileHistory]]

    assert(historyList1d.size == 3)
    assert(historyList2d.size == 1)

    assert(historyList1d(0).comment == comment1)
    assert(historyList2d(0).comment == comment1)
    assert(historyList1d(1).comment == comment2)
    assert(historyList1d(2).comment == comment3)
  }


//  test("Test updating files in default repo") {
//    val contents = "Other contents of some file...\n"
//    val comment = "Other create comment"
//
//    // Create the actor
//    val configServiceActor = system.actorOf(ConfigServiceActor.props("configManager"))
//
//    val exists1 = Await.result(configServiceActor ?
//      ExistsRequest(path1),
//      duration).asInstanceOf[ExistsResult]
//
//    if (!exists1) {
//      val createId1 = Await.result(configServiceActor ?
//        CreateRequest(path1, new ConfigString(contents), comment),
//        duration).asInstanceOf[CreateResult]
//    } else {
//      val updateId1 = Await.result(configServiceActor ?
//        UpdateRequest(path1, new ConfigString(contents), comment),
//        duration).asInstanceOf[UpdateResult]
//    }
//    val option = Await.result(configServiceActor ?
//      GetRequest(path1),
//      duration).asInstanceOf[GetResult]
//    assert(!option.isEmpty)
//    assert(option.get.toString == contents)
//  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}
