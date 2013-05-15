package org.tmt.csw.cs.akka

import org.scalatest.{BeforeAndAfterAll, FunSuite}
import java.util.Date
import java.io.{IOException, File}
import org.tmt.csw.cs.core.git.GitConfigManager
import org.tmt.csw.cs.core.ConfigString
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout

/**
 * Tests the Config Service actor
 */
class TestConfigServiceActor extends TestKit(ActorSystem("mySystem")) with ImplicitSender with FunSuite with BeforeAndAfterAll {

  val path1 = "some/test1/TestConfig1"
  val path2 = "some/test2/TestConfig2"

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  val startTime = new Date().getTime

  val duration = 5.seconds
  implicit val timeout = Timeout(5.seconds)

  // Note: Using blocking (Await) for this test, so we can compare return values easily
  test("Test the ConfigServiceActor, storing and retrieving some files") {
    // Create the temporary Git repos for the test
    val tmpDir = System.getProperty("java.io.tmpdir")
    val gitDir = new File(tmpDir, "cstest")

    // val gitMainRepo = "git@localhost:project.git"
    val gitMainRepo = new File(tmpDir, "cstestMainRepo")
    println("Local repo = " + gitDir + ", remote = " + gitMainRepo)

    // Delete the main and local test repositories (Only use this in test cases!)
    GitConfigManager.deleteLocalRepo(gitMainRepo)
    GitConfigManager.initBareRepo(gitMainRepo)
    GitConfigManager.deleteLocalRepo(gitDir)

    // create a new repository and use it to create the actor
    val manager = GitConfigManager(gitDir, gitMainRepo.getPath)

    // Create the actor (TODO: Non-test usages should not need to provide the manager argument)
    val configServiceActor = system.actorOf(Props(new ConfigServiceActor(manager)), name = "configManager")

    // Should throw exception if we try to update a file that does not exist
    intercept[IOException] {
      Await.result(configServiceActor ?
        UpdateRequest(path1, new ConfigString(contents2), comment2),
        duration).asInstanceOf[UpdateResult].result
    }

//    // Add, then update the file twice
    val createId1 = Await.result(configServiceActor ?
      CreateRequest(path1, new ConfigString(contents1), comment1),
      duration).asInstanceOf[CreateResult].result

    val createId2 = Await.result(configServiceActor ?
      CreateRequest(path2, new ConfigString(contents1), comment1),
      duration).asInstanceOf[CreateResult].result

    val updateId1 = Await.result(configServiceActor ?
      UpdateRequest(path1, new ConfigString(contents2), comment2),
      duration).asInstanceOf[UpdateResult].result

    val updateId2 = Await.result(configServiceActor ?
      UpdateRequest(path1, new ConfigString(contents3), comment3),
      duration).asInstanceOf[UpdateResult].result

    // Should throw exception if we try to create a file that already exists
    intercept[IOException] {
      Await.result(configServiceActor ?
        CreateRequest(path1, new ConfigString(contents2), comment2),
        duration).asInstanceOf[CreateResult].result
    }

    // Check that we can access each version
    val option1 = Await.result(configServiceActor ?
      GetRequest(path1),
      duration).asInstanceOf[GetResult].result
    assert(!option1.isEmpty)
    assert(option1.get.toString == contents3)

    val option2 = Await.result(configServiceActor ?
      GetRequest(path1, Some(createId1)),
      duration).asInstanceOf[GetResult].result
    assert(!option2.isEmpty)
    assert(option2.get.toString == contents1)

    val option3 = Await.result(configServiceActor ?
      GetRequest(path1, Some(updateId1)),
      duration).asInstanceOf[GetResult].result
    assert(!option3.isEmpty)
    assert(option3.get.toString == contents2)

    val option4 = Await.result(configServiceActor ?
      GetRequest(path1, Some(updateId2)),
      duration).asInstanceOf[GetResult].result
    assert(!option4.isEmpty)
    assert(option4.get.toString == contents3)

    val option5 = Await.result(configServiceActor ?
      GetRequest(path2),
      duration).asInstanceOf[GetResult].result
    assert(!option5.isEmpty)
    assert(option5.get.toString == contents1)

    val option6 = Await.result(configServiceActor ?
      GetRequest(path2, Some(createId2)),
      duration).asInstanceOf[GetResult].result
    assert(!option6.isEmpty)
    assert(option6.get.toString == contents1)

    // test history()
    val historyList1 = Await.result(configServiceActor ?
      HistoryRequest(path1),
      duration).asInstanceOf[HistoryResult].result
    val historyList2 = Await.result(configServiceActor ?
      HistoryRequest(path2),
      duration).asInstanceOf[HistoryResult].result

    assert(historyList1.size == 3)
    assert(historyList2.size == 1)

    assert(historyList1(0).comment == comment1)
    assert(historyList2(0).comment == comment1)
    assert(historyList1(1).comment == comment2)
    assert(historyList1(2).comment == comment3)

    // Test list()
    val list = Await.result(configServiceActor ?
      ListRequest(),
      duration).asInstanceOf[ListResult].result
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
    assert(!Await.result(configServiceActor ? ExistsRequest(path1), duration).asInstanceOf[ExistsResult].result)

    Await.result(configServiceActor ? DeleteRequest(path2), duration)
    assert(!Await.result(configServiceActor ? ExistsRequest(path2), duration).asInstanceOf[ExistsResult].result)

    val historyList1d = Await.result(configServiceActor ?
      HistoryRequest(path1),
      duration).asInstanceOf[HistoryResult].result
    val historyList2d = Await.result(configServiceActor ?
      HistoryRequest(path2),
      duration).asInstanceOf[HistoryResult].result

    assert(historyList1d.size == 3)
    assert(historyList2d.size == 1)

    assert(historyList1d(0).comment == comment1)
    assert(historyList2d(0).comment == comment1)
    assert(historyList1d(1).comment == comment2)
    assert(historyList1d(2).comment == comment3)
  }
}
