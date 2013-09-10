package org.tmt.csw.cmd.spray

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import org.specs2.time.NoTimeConversions
import org.tmt.csw.cmd.core.{TestConfig, Configuration}
import org.tmt.csw.cmd.akka.{CommandStatus, RunId}
import spray.http.StatusCodes
import scala.Some
import akka.actor.ActorSystem

/**
 * Tests the command service HTTP route in isolation by overriding the CommandServiceRoute implementation to run
 * without using actors.
 */
class TestCommandServiceRoute extends Specification with Specs2RouteTest with CommandServiceRoute with NoTimeConversions {

  // Required by HttpService
  def actorRefFactory: ActorSystem = system

  // The Configuration used in the tests below
  val config = Configuration(TestConfig.testConfig)

  // Polls the command status for the given runId until the command completes
  def getCommandStatus(runId: RunId): CommandStatus = {
    Get(s"/config/$runId/status") ~> route ~> check {
      assert(status == StatusCodes.OK)
      entityAs[CommandStatus]
    }
  }

  // -- Tests --

  "The command service" should {
    "return a runId for a POST /queue/submit [$config] and return the command status for GET /$runId/status" in {
      val runId = Post("/queue/submit", config) ~> route ~> check {
        assert(status == StatusCodes.Accepted)
        entityAs[RunId]
      }

      val commandStatus = getCommandStatus(runId)
      assert(commandStatus.isInstanceOf[CommandStatus.Complete])
    }
  }

  "The command service" should {
    "return a runId for a POST /request [$config] and return the command status for GET /$runId/status" in {
      val runId = Post("/request", config) ~> route ~> check {
        assert(status == StatusCodes.Accepted)
        entityAs[RunId]
      }

      val commandStatus = getCommandStatus(runId)
      assert(commandStatus.isInstanceOf[CommandStatus.Complete])

    }
  }

  "The command service" should {
    "return an OK status for other commands" in {

      Post("/queue/stop") ~> route ~> check {
        assert(status == StatusCodes.Accepted)
      }
      Post("/queue/pause") ~> route ~> check {
        assert(status == StatusCodes.Accepted)
      }
      Post("/queue/start") ~> route ~> check {
        assert(status == StatusCodes.Accepted)
      }

      val runId = RunId()
      Delete(s"/queue/$runId") ~> route ~> check {
        assert(status == StatusCodes.Accepted)
      }

      Post(s"/config/$runId/cancel") ~> route ~> check {
        assert(status == StatusCodes.Accepted)
      }
      Post(s"/config/$runId/abort") ~> route ~> check {
        assert(status == StatusCodes.Accepted)
      }
      Post(s"/config/$runId/pause") ~> route ~> check {
        assert(status == StatusCodes.Accepted)
      }
      Post(s"/config/$runId/resume") ~> route ~> check {
        assert(status == StatusCodes.Accepted)
      }
    }
  }

  // -- Override CommandServiceRoute methods with stubs for testing --

  override def submitCommand(config: Configuration): RunId = RunId()

  override def requestCommand(config: Configuration): RunId = RunId()

  override def checkCommandStatus(runId: RunId, completer: CommandService.Completer): Unit =
    completer(Some(CommandStatus.Complete(runId)))

  override def statusRequestTimedOut(runId: RunId): Boolean = false

  override def queueStop(): StatusCodes.Success = StatusCodes.Accepted

  override def queuePause(): StatusCodes.Success = StatusCodes.Accepted

  override def queueStart(): StatusCodes.Success = StatusCodes.Accepted

  override def queueDelete(runId: RunId): StatusCodes.Success = StatusCodes.Accepted

  override def configCancel(runId: RunId): StatusCodes.Success = StatusCodes.Accepted

  override def configAbort(runId: RunId): StatusCodes.Success = StatusCodes.Accepted

  override def configPause(runId: RunId): StatusCodes.Success = StatusCodes.Accepted

  override def configResume(runId: RunId): StatusCodes.Success = StatusCodes.Accepted
}
