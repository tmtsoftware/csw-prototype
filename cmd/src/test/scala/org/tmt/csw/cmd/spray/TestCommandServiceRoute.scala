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
 * Tests the command service HTTP route by overriding the implementation to run without using actors.
 */
class TestCommandServiceRoute extends Specification with Specs2RouteTest with CommandServiceRoute with NoTimeConversions {

  // Required by HttpService
  def actorRefFactory: ActorSystem = system

  // The Configuration used in the tests below
  val config = Configuration(TestConfig.testConfig)

  // Polls the command status for the given runId until the command completes
  def getStatus(runId: RunId): CommandStatus = {
    Get(s"/config/$runId/status") ~> route ~> check {
      val status = entityAs[CommandStatus]
      println(s"Command status is $status")
      if (status.done) {
        status
      } else {
        getStatus(runId)
      }
    }
  }

  // -- Tests --

  "The command service" should {
    "return a runId for a POST /queue/submit and return the command status for GET /$runId/status" in {
      val runId = Post("/queue/submit", config) ~> route ~> check {
        entityAs[RunId]
      }

      val commandStatus = getStatus(runId)
      assert(commandStatus.isInstanceOf[CommandStatus.Complete])
    }
  }

//  "The command service" should {
//    "return a runId for a POST /request and return the command status for GET /$runId/status" in {
//      val runId = Post("/request", config) ~> route ~> check {
//        entityAs[RunId]
//      }
//
//      val commandStatus = getStatus(runId)
//      assert(commandStatus.isInstanceOf[CommandStatus.Complete])
//    }
//  }


  // -- These methods are used by the command service HTTP route (Dummy versions are provided for testing) --

  override def submitCommand(config: Configuration): RunId = RunId()

  override def requestCommand(config: Configuration): RunId = RunId()

  override def checkCommandStatus(runId: RunId, completer: CommandService.Completer): Unit = {
    completer(Some(CommandStatus.Complete(runId)))
  }

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
