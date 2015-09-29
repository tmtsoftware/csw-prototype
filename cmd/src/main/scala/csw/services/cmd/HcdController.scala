package csw.services.cmd

import akka.actor.{ ActorRef, ActorLogging, Actor }
import csw.shared.cmd.RunId
import csw.util.config.Configurations.{ SetupConfig, ControlConfig }

import scala.concurrent.Future
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.Try

object HcdController {

  /**
   * Base trait of all received command service controller methods
   */
  sealed trait HcdControllerMessage

  /**
   * Tells the controller to check its inputs and update its outputs
   */
  case object Process extends HcdControllerMessage

  /**
   * Message used to submit a configuration
   * @param config the configuration
   * @param submitter the actor submitting the config
   */
  case class Submit(config: ControlConfig)(implicit submitter: ActorRef) extends HcdControllerMessage {
    val runId = RunId()
  }

  /**
   * The sender of this message will receive as a reply a ConfigResponse object
   */
  case object ConfigGet extends HcdControllerMessage

  /**
   * The response from a ConfigGet message
   * @param tryConfig if the requested config could be retrieved, Success(config), otherwise Failure(ex)
   */
  case class ConfigResponse(tryConfig: Try[SetupConfig])

}

trait HcdController extends Actor with ActorLogging {

  import HcdController._
  import context.dispatcher

  /**
   * The controller update rate: The controller inputs and outputs are processed at this rate
   */
  def rate: FiniteDuration

  def process(): Future[Unit]

  // Sends the Update message at the specified rate
  context.system.scheduler.schedule(Duration.Zero, rate, self, Process)

  def receive: Receive = {
    case Process ⇒
      process()
  }

}
