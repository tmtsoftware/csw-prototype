package org.tmt.csw.cmd.akka

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import akka.util.Timeout
import scala.concurrent.duration._
import org.tmt.csw.cmd.core.{TestConfig, Configuration}
import com.typesafe.scalalogging.slf4j.Logging
import org.tmt.csw.cmd.akka.CommandServiceActor._
import org.tmt.csw.cmd.akka.ConfigActor._
import org.tmt.csw.cmd.akka.CommandQueueActor._
import scala.util._


/**
 * Tests the Command Service actor
 */
class TestCommandServiceActor extends TestKit(ActorSystem("test")) with TestHelper
    with ImplicitSender with FunSuite with BeforeAndAfterAll with Logging {

  // The Configuration used in the tests below
  val config = Configuration(TestConfig.testConfig)

  // Note: Adjust this value and the one used by TestConfigActor
  // to match the time needed for the tests and avoid timeouts
  val duration : FiniteDuration = 10.seconds

  implicit val timeout = Timeout(duration)
  implicit val dispatcher = system.dispatcher

  // -- Tests --


  test("Test simple queue (bypass) request") {
    val commandServiceActor = getCommandServiceActor(1)
    commandServiceActor ! QueueBypassRequest(config)
    val s1 = expectMsgType[CommandStatus.Busy]
    val s2a = expectMsgType[CommandStatus.PartiallyCompleted]
    val s2 = expectMsgType[CommandStatus.Completed]
    assert(s1.runId == s2.runId)
    assert(s2.runId == s2a.runId)
  }

  test("Test simple queue submit") {
    val commandServiceActor = getCommandServiceActor(2)
    within(duration) {
      commandServiceActor ! Submit(config)
      val s1 = expectMsgType[CommandStatus.Queued]
      val s2 = expectMsgType[CommandStatus.Busy]
      val s3a = expectMsgType[CommandStatus.PartiallyCompleted]
      val s3 = expectMsgType[CommandStatus.Completed]
      assert(s1.runId == s2.runId)
      assert(s3.runId == s2.runId)
    }
  }


  test("Test queue submit with config abort") {
    val commandServiceActor = getCommandServiceActor(3)
    within(duration) {
      commandServiceActor ! Submit(config)
      val s = expectMsgType[CommandStatus.Queued]
      assert(expectMsgType[CommandStatus.Busy].runId == s.runId)
      commandServiceActor ! ConfigAbort(s.runId)
      val sa = expectMsgType[CommandStatus.PartiallyCompleted]
      assert(sa.status == "aborted")
      assert(expectMsgType[CommandStatus.Aborted].runId == s.runId)
    }
  }


  test("Test queue submit followed by config pause and resume") {
    val commandServiceActor = getCommandServiceActor(4)
    within(duration) {
      commandServiceActor ! Submit(config)
      val s = expectMsgType[CommandStatus.Queued]
      logger.info(s"Received runId for command: ${s.runId}")
      assert(expectMsgType[CommandStatus.Busy].runId == s.runId)
      commandServiceActor ! ConfigPause(s.runId)
      expectNoMsg(1.second)
      commandServiceActor ! ConfigResume(s.runId)
      assert(expectMsgType[CommandStatus.PartiallyCompleted].runId == s.runId)
      assert(expectMsgType[CommandStatus.Completed].runId == s.runId)
    }
  }


  test("Test queue pause and start") {
    val commandServiceActor = getCommandServiceActor(5)
    within(duration) {
      commandServiceActor ! QueuePause
      commandServiceActor ! Submit(config)
      val s1 = expectMsgType[CommandStatus.Queued]
      expectNoMsg(1.second)
      commandServiceActor ! QueueStart
      val s2 = expectMsgType[CommandStatus.Busy]
      val s3a = expectMsgType[CommandStatus.PartiallyCompleted]
      val s3 = expectMsgType[CommandStatus.Completed]
      assert(s1.runId == s2.runId)
      assert(s3.runId == s2.runId)
    }
  }

  test("Test queue stop and start") {
    val commandServiceActor = getCommandServiceActor(6)
    within(duration) {
      // Start with the queue paused, so that the config stays in the queue
      commandServiceActor ! QueuePause
      // Add a config to the queue
      commandServiceActor ! Submit(config)
      val s1 = expectMsgType[CommandStatus.Queued]
      // Stop the queue (all configs should be removed from the queue and no new ones added)
      commandServiceActor ! QueueStop
      expectNoMsg(1.second)
      // try adding a new config to the queue: A runId will be returned, but it will not be added to the queue
      commandServiceActor ! Submit(config)
      expectNoMsg(1.second)
      // Restart the queue (it should still be empty)
      commandServiceActor ! QueueStart
      expectNoMsg(1.second)
      // Queue a new config: This time it should be executed normally
      commandServiceActor ! Submit(config)
      val s2 = expectMsgType[CommandStatus.Queued]
      val s3 = expectMsgType[CommandStatus.Busy]
      val s4a = expectMsgType[CommandStatus.PartiallyCompleted]
      val s4 = expectMsgType[CommandStatus.Completed]
      assert(s1.runId != s2.runId)
      assert(s3.runId == s2.runId)
      assert(s4.runId == s2.runId)
    }
  }


  test("Test queue delete") {
    val commandServiceActor = getCommandServiceActor(7)
    within(duration) {
      // Start with the queue paused, so that the config stays in the queue
      commandServiceActor ! QueuePause
      // Add a config to the queue
      commandServiceActor ! Submit(config)
      val s1 = expectMsgType[CommandStatus.Queued]
      // Add another config to the queue
      commandServiceActor ! Submit(config)
      val s2 = expectMsgType[CommandStatus.Queued]
      // And another
      commandServiceActor ! Submit(config)
      val s3 = expectMsgType[CommandStatus.Queued]

      // Delete 2 of the configs from the queue
      commandServiceActor ! QueueDelete(s1.runId)
      commandServiceActor ! QueueDelete(s2.runId)
      expectNoMsg(1.second)

      // Restart the queue (it should contain one config: runId3)
      commandServiceActor ! QueueStart
      assert(expectMsgType[CommandStatus.Busy].runId == s3.runId)
      assert(expectMsgType[CommandStatus.PartiallyCompleted].runId == s3.runId)
      assert(expectMsgType[CommandStatus.Completed].runId == s3.runId)
    }
  }

  test("Test get (query) request") {
    val empty =
      """
        |      config {
        |        tmt.tel.base.pos {
        |          posName = ""
        |          c1 = ""
        |          c2 = ""
        |          equinox = ""
        |        }
        |        tmt.tel.ao.pos.one {
        |          c1 = ""
        |          c2 = ""
        |          equinox = ""
        |        }
        |      }
        |
      """.stripMargin
    val emptyConfig = Configuration(empty)
    val commandServiceActor = getCommandServiceActor(8)
    commandServiceActor ! ConfigGet(emptyConfig)
    val resp = expectMsgType[ConfigResponse]
    resp.tryConfig match {
      case Success(c) =>
        logger.info(s"XXX GET returns: ${c.toJson.toString}")
        for(s <- List(
          "config.tmt.tel.base.pos.posName",
          "config.tmt.tel.base.pos.c1",
          "config.tmt.tel.base.pos.c2",
          "config.tmt.tel.base.pos.equinox",
          "config.tmt.tel.ao.pos.one.c1",
          "config.tmt.tel.ao.pos.one.c2",
          "config.tmt.tel.ao.pos.one.equinox"
        )) {
          assert(c.getString(s) == config.getString(s), s"Failed to match config key: $s")
        }
      case Failure(ex) => fail(ex)
    }
  }


  // XXX TODO: Wait configs...

//  test("Test submit with wait config") {
//    val commandServiceActor = getCommandServiceActor(9)
//    within(duration) {
//      val waitConfig = Configuration.waitConfig(forResume = true, obsId = "TMT-2021A-C-2-1")
//
//      // Sending the wait config is like sending a Queue Pause command, except that it is also a command on the queue
//      commandServiceActor ! Submit(waitConfig)
//      val s1 = expectMsgType[CommandStatus.Queued]
//      assert(expectMsgType[CommandStatus.Completed].runId == s1.runId)
//
//      // Send a config: should be put in the queue, but not executed, since the queue is paused
//      commandServiceActor ! Submit(config)
//      val s2 = expectMsgType[CommandStatus.Queued]
//      expectNoMsg(1.second)
//      // Restart the queue
//      commandServiceActor ! QueueStart
//      assert(expectMsgType[CommandStatus.Busy].runId == s2.runId)
//      assert(expectMsgType[CommandStatus.Completed].runId == s2.runId)
//    }
//  }


//  test("Test request with wait config") {
//    val commandServiceActor = getCommandServiceActor(10)
//    val waitConfig = Configuration.waitConfig(forResume=true, obsId="TMT-2021A-C-2-1")
//
//    // Sending the wait config is like sending a Queue Pause command (in this case we bypass the queue)
//    commandServiceActor ! QueueBypassRequest(waitConfig)
//    expectMsgType[CommandStatus.Completed]
//    expectNoMsg(1.second)
//
//    within(duration) {
//      // Send a config: should be put in the queue, but not executed, since the queue is paused
//      commandServiceActor ! Submit(config)
//      val s1 = expectMsgType[CommandStatus.Queued]
//      expectNoMsg(1.second)
//      // Restart the queue
//      commandServiceActor ! QueueStart
//      assert(expectMsgType[CommandStatus.Busy].runId == s1.runId)
//      assert(expectMsgType[CommandStatus.Completed].runId == s1.runId)
//    }
//  }



  //  test("Test error handling 1") {
//
//  }

  override protected def afterAll(): Unit = {
    logger.info("Shutting down test actor system")
    TestKit.shutdownActorSystem(system)
  }
}
