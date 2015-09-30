package csw.services.cmd_old.spray

import akka.event.Logging
import akka.http.scaladsl.model._
import csw.shared.cmd_old.CommandStatus
import csw.shared.cmd_old.RunId
import csw.util.cfg_old.{ TestConfig, ConfigJsonFormats }
import csw.util.cfg_old.Configurations._
import csw.util.cfg_old.Configurations.SetupConfigList
import akka.actor.ActorSystem
import scala.util.Success
import csw.services.cmd_old.akka.ConfigActor.ConfigResponse
import scala.concurrent.Future
import akka.stream.scaladsl.Source
import org.scalatest.{ Matchers, WordSpec }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest

/**
 * Tests the command service HTTP route in isolation by overriding the CommandServiceRoute implementation to run
 * without using actors.
 */
class CommandServiceHttpRouteTests extends WordSpec
    with Matchers with ScalatestRouteTest with CommandServiceHttpRoute with ConfigJsonFormats {

  // Required by HttpService
  def actorRefFactory: ActorSystem = system

  override def log = Logging(system, this.getClass)

  // The Configuration used in the tests below
  val config = TestConfig.testConfig

  val httpRoute = route

  // -- Tests --

  "The command service" should {
    "return a runId for a POST /queue/submit [$config] and return the command status for GET /$runId/status" in {
      val xxx = Post("/queue/submit", config) ~> httpRoute ~> check {
        println(s"XXX status = $status, contentType = $contentType")
        status == StatusCodes.Accepted
        contentType == ContentTypes.`application/json`
        //        responseAs[RunId] // XXX How to test?
      }

      println(s"XXX result of post /queue/submit: $xxx")
    }
  }

  "The command service" should {
    "return an OK status for other commands" in {

      Post("/queue/stop") ~> httpRoute ~> check {
        status == StatusCodes.Accepted
      }
      Post("/queue/pause") ~> httpRoute ~> check {
        status == StatusCodes.Accepted
      }
      Post("/queue/start") ~> httpRoute ~> check {
        status == StatusCodes.Accepted
      }

      val runId = RunId()
      Delete(s"/queue/$runId") ~> httpRoute ~> check {
        status == StatusCodes.Accepted
      }

      Post(s"/config/$runId/cancel") ~> httpRoute ~> check {
        status == StatusCodes.Accepted
      }
      Post(s"/config/$runId/abort") ~> httpRoute ~> check {
        status == StatusCodes.Accepted
      }
      Post(s"/config/$runId/pause") ~> httpRoute ~> check {
        status == StatusCodes.Accepted
      }
      Post(s"/config/$runId/resume") ~> httpRoute ~> check {
        status == StatusCodes.Accepted
      }
    }
  }

  // -- Override CommandServiceRoute methods with stubs for testing --

  override def submitCommand(config: ConfigList): Source[CommandStatus, Unit] =
    Source(List(CommandStatus.Completed(RunId())))

  override def requestCommand(config: ConfigList): Source[CommandStatus, Unit] =
    Source(List(CommandStatus.Completed(RunId())))

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
