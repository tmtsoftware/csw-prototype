package csw.services.cmd.spray

import akka.event.{ Logging, LoggingAdapter }
import csw.util.cfg.{ TestConfig, ConfigValues, ConfigJsonFormats }
import csw.util.cfg.Configurations._
import csw.util.cfg.Configurations.{ SetupConfigList, SetupConfig }
import ConfigValues.ValueData._
import spray.json._
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import org.specs2.time.NoTimeConversions
import csw.services.cmd.akka.{ CommandStatus, RunId }
import spray.http.{ ContentTypes, StatusCodes }
import scala.Some
import akka.actor.ActorSystem
import scala.util.Success
import csw.services.cmd.akka.ConfigActor.ConfigResponse
import csw.services.cmd.akka.CommandServiceClientHelper._
import scala.concurrent.Future

/**
 * Tests the command service HTTP route in isolation by overriding the CommandServiceRoute implementation to run
 * without using actors.
 */
class CommandServiceHttpRouteTests extends Specification
    with Specs2RouteTest with CommandServiceHttpRoute with NoTimeConversions with ConfigJsonFormats {

  // Required by HttpService
  def actorRefFactory: ActorSystem = system

  override def log = Logging(system, this.getClass)

  // The Configuration used in the tests below
  val config = TestConfig.testConfig

  //  // The config in JSON format
  //  val configJson = config.toJson.prettyPrint

  // Polls the command status for the given runId until the command completes
  def getCommandStatus(runId: RunId): CommandStatus = {
    Get(s"/config/$runId/status") ~> route ~> check {
      assert(status == StatusCodes.OK)
      assert(contentType == ContentTypes.`application/json`)
      responseAs[CommandStatus]
    }
  }

  // -- Tests --

  "The command service" should {
    "return a runId for a POST /queue/submit [$config] and return the command status for GET /$runId/status" in {
      val runId = Post("/queue/submit", config) ~> route ~> check {
        status == StatusCodes.Accepted
        contentType == ContentTypes.`application/json`
        responseAs[RunId]
      }

      val commandStatus = getCommandStatus(runId)
      commandStatus.isInstanceOf[CommandStatus.Completed]
    }
  }

  "The command service" should {
    "return a runId for a POST /request [$config] and return the command status for GET /$runId/status" in {
      val runId = Post("/request", config) ~> route ~> check {
        status == StatusCodes.Accepted
        contentType == ContentTypes.`application/json`
        responseAs[RunId]
      }

      val commandStatus = getCommandStatus(runId)
      commandStatus.isInstanceOf[CommandStatus.Completed]

    }
  }

  "The command service" should {
    "return an OK status for other commands" in {

      Post("/queue/stop") ~> route ~> check {
        status == StatusCodes.Accepted
      }
      Post("/queue/pause") ~> route ~> check {
        status == StatusCodes.Accepted
      }
      Post("/queue/start") ~> route ~> check {
        status == StatusCodes.Accepted
      }

      val runId = RunId()
      Delete(s"/queue/$runId") ~> route ~> check {
        status == StatusCodes.Accepted
      }

      Post(s"/config/$runId/cancel") ~> route ~> check {
        status == StatusCodes.Accepted
      }
      Post(s"/config/$runId/abort") ~> route ~> check {
        status == StatusCodes.Accepted
      }
      Post(s"/config/$runId/pause") ~> route ~> check {
        status == StatusCodes.Accepted
      }
      Post(s"/config/$runId/resume") ~> route ~> check {
        status == StatusCodes.Accepted
      }
    }
  }

  // Commented out for now since this leaves a stack trace in the output
  //  "The command service" should {
  //    "return an error status for unknown commands" in {
  //
  //      // Unknown path
  //      Post("/junk") ~> route ~> check {
  //        assert(status == StatusCodes.BadRequest)
  //      }
  //
  //      // Should be Post
  //      Get("/queue/start") ~> route ~> check {
  //        assert(status == StatusCodes.BadRequest)
  //      }
  //
  //      // When the server (http route code) throws an exception, we should get a InternalServerError
  //      Post("/test/error") ~> route ~> check {
  //        assert(status == StatusCodes.InternalServerError)
  //      }
  //    }
  //  }

  // -- Override CommandServiceRoute methods with stubs for testing --

  override def submitCommand(config: ConfigList): RunId = RunId()

  override def requestCommand(config: ConfigList): RunId = RunId()

  override def checkCommandStatus(runId: RunId, completer: CommandStatusCompleter): Unit =
    completer.complete(Some(CommandStatus.Completed(runId)))

  override def statusRequestTimedOut(runId: RunId): Boolean = false

  override def queueStop(): Unit = {}

  override def queuePause(): Unit = {}

  override def queueStart(): Unit = {}

  override def queueDelete(runId: RunId): Unit = {}

  override def configGet(config: SetupConfigList): Future[ConfigResponse] = {
    // dummy code, just returns the input config
    Future.successful(ConfigResponse(Success(config)))
  }

  override def configCancel(runId: RunId): Unit = {}

  override def configAbort(runId: RunId): Unit = {}

  override def configPause(runId: RunId): Unit = {}

  override def configResume(runId: RunId): Unit = {}

  override def commandServiceStatus(): Future[String] = {
    Future.successful("OK")
  }

}
