package org.tmt.csw.cmd.akka

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import org.tmt.csw.cmd.core.{TestConfig, Configuration}
import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.Await


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
    // Request a command without being queued
    val status =  Await.result(commandServiceActor ? CommandServiceMessage.QueueBypassRequest(config),
      duration).asInstanceOf[CommandStatus.Complete]
    logger.info(s"Received command status: $status")
  }


  test("Test simple queue submit") {
    val commandServiceActor = getCommandServiceActor(2)
    within(duration) {
      commandServiceActor ! CommandServiceMessage.Submit(config)
      val s1 = expectMsgType[CommandStatus.Queued]
      val s2 = expectMsgType[CommandStatus.Busy]
      val s3 = expectMsgType[CommandStatus.Complete]
      assert(s1.runId == s2.runId)
      assert(s3.runId == s2.runId)
    }
  }


  test("Test queue submit with config abort") {
    val commandServiceActor = getCommandServiceActor(3)
    within(duration) {
      commandServiceActor ! CommandServiceMessage.Submit(config)
      val s = expectMsgType[CommandStatus.Queued]
      assert(expectMsgType[CommandStatus.Busy].runId == s.runId)
      commandServiceActor ! CommandServiceMessage.ConfigAbort(s.runId)
      assert(expectMsgType[CommandStatus.Aborted].runId == s.runId)
    }
  }


  test("Test queue submit followed by config pause and resume") {
    val commandServiceActor = getCommandServiceActor(4)
    within(duration) {
      commandServiceActor ! CommandServiceMessage.Submit(config)
      val s = expectMsgType[CommandStatus.Queued]
      logger.info(s"Received runId for command: ${s.runId}")
      assert(expectMsgType[CommandStatus.Busy].runId == s.runId)
      commandServiceActor ! CommandServiceMessage.ConfigPause(s.runId)
      expectNoMsg(1.second)
      commandServiceActor ! CommandServiceMessage.ConfigResume(s.runId)
      assert(expectMsgType[CommandStatus.Complete].runId == s.runId)
    }
  }


  test("Test queue pause and start") {
    val commandServiceActor = getCommandServiceActor(5)
    within(duration) {
      commandServiceActor ! CommandServiceMessage.QueuePause
      commandServiceActor ! CommandServiceMessage.Submit(config)
      val s1 = expectMsgType[CommandStatus.Queued]
      expectNoMsg(1.second)
      commandServiceActor ! CommandServiceMessage.QueueStart
      val s2 = expectMsgType[CommandStatus.Busy]
      val s3 = expectMsgType[CommandStatus.Complete]
      assert(s1.runId == s2.runId)
      assert(s3.runId == s2.runId)
    }
  }

  test("Test queue stop and start") {
    val commandServiceActor = getCommandServiceActor(6)
    within(duration) {
      // Start with the queue paused, so that the config stays in the queue
      commandServiceActor ! CommandServiceMessage.QueuePause
      // Add a config to the queue
      commandServiceActor ! CommandServiceMessage.Submit(config)
      val s1 = expectMsgType[CommandStatus.Queued]
      // Stop the queue (all configs should be removed from the queue and no new ones added)
      commandServiceActor ! CommandServiceMessage.QueueStop
      expectNoMsg(1.second)
      // try adding a new config to the queue: A runId will be returned, but it will not be added to the queue
      commandServiceActor ! CommandServiceMessage.Submit(config)
      expectNoMsg(1.second)
      // Restart the queue (it should still be empty)
      commandServiceActor ! CommandServiceMessage.QueueStart
      expectNoMsg(1.second)
      // Queue a new config: This time it should be executed normally
      commandServiceActor ! CommandServiceMessage.Submit(config)
      val s2 = expectMsgType[CommandStatus.Queued]
      val s3 = expectMsgType[CommandStatus.Busy]
      val s4 = expectMsgType[CommandStatus.Complete]
      assert(s1.runId != s2.runId)
      assert(s3.runId == s2.runId)
      assert(s4.runId == s2.runId)
    }
  }


  test("Test queue delete") {
    val commandServiceActor = getCommandServiceActor(7)
    within(duration) {
      // Start with the queue paused, so that the config stays in the queue
      commandServiceActor ! CommandServiceMessage.QueuePause
      // Add a config to the queue
      commandServiceActor ! CommandServiceMessage.Submit(config)
      val s1 = expectMsgType[CommandStatus.Queued]
      // Add another config to the queue
      commandServiceActor ! CommandServiceMessage.Submit(config)
      val s2 = expectMsgType[CommandStatus.Queued]
      // And another
      commandServiceActor ! CommandServiceMessage.Submit(config)
      val s3 = expectMsgType[CommandStatus.Queued]

      // Delete 2 of the configs from the queue
      commandServiceActor ! CommandServiceMessage.QueueDelete(s1.runId)
      commandServiceActor ! CommandServiceMessage.QueueDelete(s2.runId)
      expectNoMsg(1.second)

      // Restart the queue (it should contain one config: runId3)
      commandServiceActor ! CommandServiceMessage.QueueStart
      assert(expectMsgType[CommandStatus.Busy].runId == s3.runId)
      assert(expectMsgType[CommandStatus.Complete].runId == s3.runId)
    }
  }


  test("Test submit with wait config") {
    val commandServiceActor = getCommandServiceActor(8)
    within(duration) {
      val waitConfig = Configuration.waitConfig(forResume = true, obsId = "TMT-2021A-C-2-1")

      // Sending the wait config is like sending a Queue Pause command, except that it is also a command on the queue
      commandServiceActor ! CommandServiceMessage.Submit(waitConfig)
      val s1 = expectMsgType[CommandStatus.Queued]
      assert(expectMsgType[CommandStatus.Busy].runId == s1.runId)

      // Send a config: should be put in the queue, but not executed, since the queue is paused
      commandServiceActor ! CommandServiceMessage.Submit(config)
      val s2 = expectMsgType[CommandStatus.Queued]
      expectNoMsg(1.second)
      // Restart the queue
      commandServiceActor ! CommandServiceMessage.QueueStart
      assert(expectMsgType[CommandStatus.Busy].runId == s2.runId)
      assert(expectMsgType[CommandStatus.Complete].runId == s2.runId)
    }
  }


  test("Test request with wait config") {
    val commandServiceActor = getCommandServiceActor(9)
    val waitConfig = Configuration.waitConfig(forResume=true, obsId="TMT-2021A-C-2-1")

    // Sending the wait config is like sending a Queue Pause command (in this case we bypass the queue)
    Await.result(commandServiceActor ? CommandServiceMessage.QueueBypassRequest(waitConfig),
      duration).asInstanceOf[CommandStatus.Complete]
    expectNoMsg(1.second)

    within(duration) {
      // Send a config: should be put in the queue, but not executed, since the queue is paused
      commandServiceActor ! CommandServiceMessage.Submit(config)
      val s1 = expectMsgType[CommandStatus.Queued]
      expectNoMsg(1.second)
      // Restart the queue
      commandServiceActor ! CommandServiceMessage.QueueStart
      assert(expectMsgType[CommandStatus.Busy].runId == s1.runId)
      assert(expectMsgType[CommandStatus.Complete].runId == s1.runId)
    }
  }


//  test("Test error handling 1") {
//
//  }

  override protected def afterAll(): Unit = {
    logger.info("Shutting down test actor system")
    TestKit.shutdownActorSystem(system)
  }
}
