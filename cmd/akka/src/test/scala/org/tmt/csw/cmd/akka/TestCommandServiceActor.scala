package org.tmt.csw.cmd.akka

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorRef, Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuite}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import org.tmt.csw.cmd.core.Configuration
import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.Await
import scala.util.Random

object TestConfig {
  val testConfig =
    """
      |      config {
      |        info {
      |          obsId = TMT-2021A-C-2-1
      |        }
      |        tmt.tel.base.pos {
      |          posName = NGC738B
      |          c1 = "22:35:58.530"
      |          c2 = "33:57:55.40"
      |          equinox = J2000
      |        }
      |        tmt.tel.ao.pos.one {
      |          c1 = "22:356:01.066"
      |          c2 = "33:58:21.69"
      |          equinox = J2000
      |        }
      |      }
      |
    """.stripMargin
}

/**
 * Tests the Command Service actor
 */
class TestCommandServiceActor extends TestKit(ActorSystem("test"))
    with ImplicitSender with FunSuite with BeforeAndAfterEach with BeforeAndAfterAll with Logging {

//  var commandServiceActor: ActorRef = null
//  var configActor: ActorRef = null
  val config = Configuration(TestConfig.testConfig)

  val duration : FiniteDuration = 5.seconds
  implicit val timeout = Timeout(duration)
  implicit val dispatcher = system.dispatcher

//  // Creates the actors before each test.
//  // Tests run concurrently, so give each test actor a unique name.
//  override protected def beforeEach() {
//    val n = Random.nextInt()
//    commandServiceActor = system.actorOf(Props[CommandServiceActor], name = s"testCommandServiceActor$n")
//    configActor = system.actorOf(TestConfigActor.props(n), name = s"TestConfigActor$n")
//  }
//
//  // Kills the actors after each test.
//  override protected def afterEach() {
//    system.stop(commandServiceActor)
//    system.stop(configActor)
//  }


  // -- Tests --

  def getCommandService(n: Int) : ActorRef = {
    val commandServiceActor = system.actorOf(Props[CommandServiceActor], name = s"testCommandServiceActor$n")
    system.actorOf(TestConfigActor.props(n), name = s"TestConfigActor$n")
    Thread.sleep(100) // XXX wait for register: FIXME: Send a message when registered...
    commandServiceActor
  }

  test("Test simple queue (bypass) request") {
    val commandServiceActor = getCommandService(1)
    // Request a command without being queued
    val status =  Await.result(commandServiceActor ? CommandServiceMessage.QueueBypassRequest(config),
      duration).asInstanceOf[CommandStatus.Complete]
    logger.info(s"Received command status: $status")
  }


  test("Test simple queue submit") {
    val commandServiceActor = getCommandService(2)
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
    val commandServiceActor = getCommandService(3)
    within(duration) {
      commandServiceActor ! CommandServiceMessage.Submit(config)
      val s = expectMsgType[CommandStatus.Queued]
      assert(expectMsgType[CommandStatus.Busy].runId == s.runId)
      commandServiceActor ! CommandServiceMessage.ConfigAbort(s.runId)
      assert(expectMsgType[CommandStatus.Aborted].runId == s.runId)
    }
  }


  test("Test queue submit followed by config pause and resume") {
    val commandServiceActor = getCommandService(4)
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
    val commandServiceActor = getCommandService(5)
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
    val commandServiceActor = getCommandService(6)
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
    val commandServiceActor = getCommandService(7)
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
    val commandServiceActor = getCommandService(8)
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
    val commandServiceActor = getCommandService(9)
    val waitConfig = Configuration.waitConfig(forResume=true, obsId="TMT-2021A-C-2-1")

    // Sending the wait config is like sending a Queue Pause command (in this case we bypass the queue)
    val status =  Await.result(commandServiceActor ? CommandServiceMessage.QueueBypassRequest(waitConfig),
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

  override protected def afterAll() {
    logger.info("Shutting down test actor system")
    TestKit.shutdownActorSystem(system)
  }
}
