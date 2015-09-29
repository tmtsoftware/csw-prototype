package csw.services.cmd_old.akka

import akka.testkit.{ ImplicitSender, TestKit }
import akka.actor.ActorSystem
import csw.shared.cmd.CommandStatus
import csw.util.cfg_old.{ TestConfig, ConfigJsonFormats }
import org.scalatest.{ FunSuiteLike, BeforeAndAfterAll }
import akka.util.Timeout
import scala.concurrent.duration._
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.cmd_old.akka.CommandServiceActor._
import csw.services.cmd_old.akka.ConfigActor._
import csw.services.cmd_old.akka.CommandQueueActor._
import scala.util._

/**
 * Tests the Command Service actor
 *
 * Note: The build settings include "parallelExecution in Test := false", so the
 * tests run one after the other.
 */
class CommandServiceActorTests extends TestKit(ActorSystem("test")) with TestHelper
    with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging with ConfigJsonFormats {

  // The Configuration used in the tests below
  val config = TestConfig.testConfig

  // Note: Adjust this value and the one used by TestConfigActor
  // to match the time needed for the tests and avoid timeouts
  val duration: FiniteDuration = 10.seconds

  implicit val timeout = Timeout(duration)
  implicit val dispatcher = system.dispatcher

  val commandServiceActor = getCommandServiceActor()

  // -- Tests --

  test("Test simple queue submit") {
    within(duration) {
      commandServiceActor ! Submit(config)
      val s1 = expectMsgType[CommandStatus.Queued]
      val s2 = expectMsgType[CommandStatus.Busy]
      expectMsgType[CommandStatus.PartiallyCompleted]
      val s3 = expectMsgType[CommandStatus.Completed]
      assert(s1.runId == s2.runId)
      assert(s3.runId == s2.runId)
    }
  }

  test("Test simple queue (bypass) request") {
    commandServiceActor ! QueueBypassRequest(config)
    val s1 = expectMsgType[CommandStatus.Busy]
    val s2a = expectMsgType[CommandStatus.PartiallyCompleted]
    val s2 = expectMsgType[CommandStatus.Completed]
    assert(s1.runId == s2.runId)
    assert(s2.runId == s2a.runId)
  }

  test("Test queue submit with config abort") {
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
    within(duration) {
      commandServiceActor ! QueuePause
      commandServiceActor ! Submit(config)
      val s1 = expectMsgType[CommandStatus.Queued]
      expectNoMsg(1.second)
      commandServiceActor ! QueueStart
      val s2 = expectMsgType[CommandStatus.Busy]
      expectMsgType[CommandStatus.PartiallyCompleted]
      val s3 = expectMsgType[CommandStatus.Completed]
      assert(s1.runId == s2.runId)
      assert(s3.runId == s2.runId)
    }
  }

  test("Test queue stop and start") {
    within(duration) {
      // Start with the queue paused, so that the config stays in the queue
      commandServiceActor ! QueuePause
      // Add a config to the queue
      commandServiceActor ! Submit(config)
      val s1 = expectMsgType[CommandStatus.Queued]
      // Stop the queue (all configs should be removed from the queue and no new ones added)
      commandServiceActor ! QueueStop
      expectNoMsg(1.second)
      // try adding a new config to the queue while the queue is stopped (it should not be added to the queue)
      commandServiceActor ! Submit(config)
      expectMsgType[CommandStatus.Error]
      // Restart the queue (it should still be empty)
      commandServiceActor ! QueueStart
      expectNoMsg(1.second)
      // Queue a new config: This time it should be executed normally
      commandServiceActor ! Submit(config)
      val s2 = expectMsgType[CommandStatus.Queued]
      val s3 = expectMsgType[CommandStatus.Busy]
      expectMsgType[CommandStatus.PartiallyCompleted]
      val s4 = expectMsgType[CommandStatus.Completed]
      assert(s1.runId != s2.runId)
      assert(s3.runId == s2.runId)
      assert(s4.runId == s2.runId)
    }
  }

  test("Test queue delete") {
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
    val emptyConfig = TestConfig.refConfig
    commandServiceActor ! ConfigGet(emptyConfig)
    val resp = expectMsgType[ConfigResponse]
    resp.tryConfig match {
      case Success(c) ⇒
        logger.info(s"GET returns: $c")
        assert(c.size == emptyConfig.size)
        assert(c.head.obsId == config.head.obsId)
        assert(c.head.prefix == config.head.prefix)
      case Failure(ex) ⇒ fail(ex)
    }
  }

  override protected def afterAll(): Unit = {
    logger.info("Shutting down test actor system")
    TestKit.shutdownActorSystem(system)
  }
}
