package org.tmt.csw.cmd.akka

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import org.tmt.csw.cmd.core.Configuration
import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.Await

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
class TestCommandServiceActor extends TestKit(ActorSystem("testsys"))
with ImplicitSender with FunSuite with BeforeAndAfterAll with Logging {

  // Used for waiting below: needs to be longer than the number of seconds passed to TestConfigActor.props()
  val duration : FiniteDuration = 5.seconds

  implicit val timeout = Timeout(duration)
  implicit val dispatcher = system.dispatcher


  test("Test simple queue (bypass) request") {
    val configActorProps = TestConfigActor.props(3)
    val commandServiceActor = system.actorOf(CommandServiceActor.props(configActorProps, "test"), name = "commandServiceActor1")
    val config = Configuration(TestConfig.testConfig)

    // Request a command without being queued
    val status =  Await.result(commandServiceActor ? CommandServiceActor.QueueBypassRequest(config, timeout),
      duration).asInstanceOf[CommandStatus.Complete]
    logger.info(s"Received command status: $status")
  }


  test("Test simple queue submit") {
    val configActorProps = TestConfigActor.props(3)
    val commandServiceActor = system.actorOf(CommandServiceActor.props(configActorProps, "test"), name = "commandServiceActor2")
    val config = Configuration(TestConfig.testConfig)

    // Queue a command
    val f = commandServiceActor ? CommandServiceActor.QueueSubmit(config, self)
    val runId = Await.result(f, duration).asInstanceOf[RunId]
    logger.info(s"Received runId for command: $runId")
    assert(expectMsgType[CommandStatus.Queued](duration).runId == runId)
    assert(expectMsgType[CommandStatus.Busy](duration).runId == runId)
    assert(expectMsgType[CommandStatus.Complete](duration).runId == runId)
  }


  test("Test queue submit with config abort") {
    val configActorProps = TestConfigActor.props(3)
    val commandServiceActor = system.actorOf(CommandServiceActor.props(configActorProps, "test"), name = "commandServiceActor3")
    val config = Configuration(TestConfig.testConfig)

    // Queue a command
    val f = commandServiceActor ? CommandServiceActor.QueueSubmit(config, self)
    val runId = Await.result(f, duration).asInstanceOf[RunId]
    logger.info(s"Received runId for command: $runId")
    commandServiceActor ! CommandServiceActor.ConfigAbort(runId)
    assert(expectMsgType[CommandStatus.Queued](duration).runId == runId)
    assert(expectMsgType[CommandStatus.Busy](duration).runId == runId)
    assert(expectMsgType[CommandStatus.Aborted](duration).runId == runId)
  }


  test("Test queue submit followed by config pause and resume") {
    val configActorProps = TestConfigActor.props(3)
    val commandServiceActor = system.actorOf(CommandServiceActor.props(configActorProps, "test"), name = "commandServiceActor4")
    val config = Configuration(TestConfig.testConfig)

    // Queue a command
    val f = commandServiceActor ? CommandServiceActor.QueueSubmit(config, self)
    val runId = Await.result(f, duration).asInstanceOf[RunId]
    logger.info(s"Received runId for command: $runId")
    assert(expectMsgType[CommandStatus.Queued](duration).runId == runId)
    assert(expectMsgType[CommandStatus.Busy](duration).runId == runId)
    commandServiceActor ! CommandServiceActor.ConfigPause(runId)
    expectNoMsg(duration)
    commandServiceActor ! CommandServiceActor.ConfigResume(runId)
    assert(expectMsgType[CommandStatus.Complete](duration).runId == runId)
  }


  test("Test queue pause and start") {
    val configActorProps = TestConfigActor.props(3)
    val commandServiceActor = system.actorOf(CommandServiceActor.props(configActorProps, "test"), name = "commandServiceActor5")
    val config = Configuration(TestConfig.testConfig)

    commandServiceActor ! CommandServiceActor.QueuePause
    val f = commandServiceActor ? CommandServiceActor.QueueSubmit(config, self)
    val runId = Await.result(f, duration).asInstanceOf[RunId]
    logger.info(s"Received runId for command: $runId")
    assert(expectMsgType[CommandStatus.Queued](duration).runId == runId)
    expectNoMsg(duration)
    commandServiceActor ! CommandServiceActor.QueueStart
    assert(expectMsgType[CommandStatus.Busy](duration).runId == runId)
    assert(expectMsgType[CommandStatus.Complete](duration).runId == runId)
  }


  test("Test queue stop and start") {
    val configActorProps = TestConfigActor.props(3)
    val commandServiceActor = system.actorOf(CommandServiceActor.props(configActorProps, "test"), name = "commandServiceActor6")
    val config = Configuration(TestConfig.testConfig)

    // Start with the queue paused, so that the config stays in the queue
    commandServiceActor ! CommandServiceActor.QueuePause
    // Add a config to the queue
    val runId = Await.result(commandServiceActor ? CommandServiceActor.QueueSubmit(config, self), duration).asInstanceOf[RunId]
    val s1 = expectMsgType[CommandStatus.Queued](duration)
    assert(s1.runId == runId)
    // Stop the queue (all configs should be removed from the queue and no new ones added)
    commandServiceActor ! CommandServiceActor.QueueStop
    expectNoMsg(1.second)
    // try adding a new config to the queue: A runId will be returned, but it will not be added to the queue
    Await.result(commandServiceActor ? CommandServiceActor.QueueSubmit(config, self), duration).asInstanceOf[RunId]
    expectNoMsg(1.second)
    // Restart the queue (it should still be empty)
    commandServiceActor ! CommandServiceActor.QueueStart
    expectNoMsg(1.second)
    // Queue a new config: This time it should be executed normally
    val runId2 = Await.result(commandServiceActor ? CommandServiceActor.QueueSubmit(config, self), duration).asInstanceOf[RunId]
    val s2 = expectMsgType[CommandStatus.Queued](duration)
    val s3 = expectMsgType[CommandStatus.Busy](duration)
    val s4 = expectMsgType[CommandStatus.Complete](duration)
    assert(s1.runId != s2.runId)
    assert(s2.runId == runId2)
    assert(s3.runId == runId2)
    assert(s4.runId == runId2)
  }


  test("Test queue delete") {
    val configActorProps = TestConfigActor.props(3)
    val commandServiceActor = system.actorOf(CommandServiceActor.props(configActorProps, "test"), name = "commandServiceActor7")
    val config = Configuration(TestConfig.testConfig)

    // Start with the queue paused, so that the config stays in the queue
    commandServiceActor ! CommandServiceActor.QueuePause
    // Add a config to the queue
    val runId1 = Await.result(commandServiceActor ? CommandServiceActor.QueueSubmit(config, self), duration).asInstanceOf[RunId]
    expectMsgType[CommandStatus.Queued](duration)
    // Add another config to the queue
    val runId2 = Await.result(commandServiceActor ? CommandServiceActor.QueueSubmit(config, self), duration).asInstanceOf[RunId]
    expectMsgType[CommandStatus.Queued](duration)
    // And another
    val runId3 = Await.result(commandServiceActor ? CommandServiceActor.QueueSubmit(config, self), duration).asInstanceOf[RunId]
    expectMsgType[CommandStatus.Queued](duration)

    // Delete 2 of the configs from the queue
    commandServiceActor ! CommandServiceActor.QueueDelete(runId1)
    commandServiceActor ! CommandServiceActor.QueueDelete(runId2)
    expectNoMsg(1.second)

    // Restart the queue (it should contain one config: runId3)
    commandServiceActor ! CommandServiceActor.QueueStart
    assert(expectMsgType[CommandStatus.Busy](duration).runId == runId3)
    assert(expectMsgType[CommandStatus.Complete](duration).runId == runId3)
  }


  test("Test submit with wait config") {
    val configActorProps = TestConfigActor.props(3)
    val commandServiceActor = system.actorOf(CommandServiceActor.props(configActorProps, "test"), name = "commandServiceActor8")
    val config = Configuration(TestConfig.testConfig)
    val waitConfig = Configuration.waitConfig(forResume=true, obsId="TMT-2021A-C-2-1")

    // Sending the wait config is like sending a Queue Pause command, except that it is also a command on the queue
    val waitRunId = Await.result(commandServiceActor ? CommandServiceActor.QueueSubmit(waitConfig, self), duration).asInstanceOf[RunId]
    assert(expectMsgType[CommandStatus.Queued](duration).runId == waitRunId)
    assert(expectMsgType[CommandStatus.Busy](duration).runId == waitRunId)

    // Send a config: should be put in the queue, but not executed, since the queue is paused
    val runId = Await.result(commandServiceActor ? CommandServiceActor.QueueSubmit(config, self), duration).asInstanceOf[RunId]
    expectMsgType[CommandStatus.Queued](duration)
    expectNoMsg(duration)
    // Restart the queue
    commandServiceActor ! CommandServiceActor.QueueStart
    assert(expectMsgType[CommandStatus.Busy](duration).runId == runId)
    assert(expectMsgType[CommandStatus.Complete](duration).runId == runId)
  }


  test("Test request with wait config") {
    val configActorProps = TestConfigActor.props(3)
    val commandServiceActor = system.actorOf(CommandServiceActor.props(configActorProps, "test"), name = "commandServiceActor9")
    val config = Configuration(TestConfig.testConfig)
    val waitConfig = Configuration.waitConfig(forResume=true, obsId="TMT-2021A-C-2-1")

    // Sending the wait config is like sending a Queue Pause command (in this case we bypass the queue)
    val status =  Await.result(commandServiceActor ? CommandServiceActor.QueueBypassRequest(waitConfig, timeout),
      duration).asInstanceOf[CommandStatus.Complete]
    expectNoMsg(duration)

    // Send a config: should be put in the queue, but not executed, since the queue is paused
    val runId = Await.result(commandServiceActor ? CommandServiceActor.QueueSubmit(config, self), duration).asInstanceOf[RunId]
    expectMsgType[CommandStatus.Queued](duration)
    expectNoMsg(duration)
    // Restart the queue
    commandServiceActor ! CommandServiceActor.QueueStart
    assert(expectMsgType[CommandStatus.Busy](duration).runId == runId)
    assert(expectMsgType[CommandStatus.Complete](duration).runId == runId)
  }


  test("Test error handling 1") {

  }

  override protected def afterAll() {
    logger.info("Shutting down test actor system")
    TestKit.shutdownActorSystem(system)
  }
}
