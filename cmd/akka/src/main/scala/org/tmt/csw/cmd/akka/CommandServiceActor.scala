package org.tmt.csw.cmd.akka

import _root_.akka.actor._
import _root_.akka.pattern.ask
import scala.concurrent.Future
import _root_.akka.util.Timeout
import org.tmt.csw.cmd.core.Configuration
import org.tmt.csw.cmd.akka.CommandServiceActor._

/**
 * Contains actor messages received
 */
object CommandServiceActor {
  // TMT Standard Queue Interaction Commands
  sealed trait QueueInteractionCommand
  case class QueueSubmit(configs: Configuration) extends QueueInteractionCommand
  case class QueueRequest(configs: Configuration, timeout: Timeout) extends QueueInteractionCommand
  case class QueueStop() extends QueueInteractionCommand
  case class QueuePause() extends QueueInteractionCommand
  case class QueueStart() extends QueueInteractionCommand
  case class QueueDelete(runId: RunId) extends QueueInteractionCommand
}

/**
 * Implements the TMT Command Service
 */

/**
 * Implements the TMT Command Service.
 * @param configActor the target actor for the command
 */
class CommandServiceActor(configActor: ActorRef, componentName: String) extends Actor {

  val queueActor = context.actorOf(Props(new QueueActor(configActor)), name = componentName)

  def receive = {
    case QueueSubmit(config) => sender ! queueSubmit(config)
    case QueueRequest(config, timeout) => sender ! queueRequest(config, timeout)
    case QueueStop() => queueStop()
    case QueuePause() => queuePause()
    case QueueStart() => queueStart()
    case QueueDelete(runId) => queueDelete(runId)

    // Status Messages (XXX TODO: send events for these? Or send them directly to the original sender?)
    case CommandStatus.StatusQueued =>
    case CommandStatus.StatusBusy =>
    case CommandStatus.StatusComplete =>
    case CommandStatus.StatusError(runId, ex) =>
    case CommandStatus.StatusAborted =>

    case _ => sender ! Status.Failure(new IllegalArgumentException)
  }

  /**
   * Submit one or more configs to the component's command queue and return the run id.
   */
  private def queueSubmit(config: Configuration): RunId = {
    val runId = RunId()
    queueActor ! QueueActor.QueueSubmit(QueueConfig(runId, config))
    runId
  }

  /**
   * Request immediate execution of one or more configs on the component and return a future with the status
   * (which should be
   */
  private def queueRequest(config: Configuration, t: Timeout): Future[CommandStatus] = {
    implicit val timeout = t
    (queueActor ? QueueActor.QueueRequest(QueueConfig(RunId(), config), t)).mapTo[CommandStatus]
  }

  /**
   * Processing of Configurations in a components queue is stopped. All Configurations currently in the
   * queue are removed. No components are accepted or processed while stopped.
   */
  private def queueStop() {
    queueActor ! QueueActor.QueueStop()
    context.stop(self)
  }

  /**
   * Pause the processing of a component’s queue after the completion of the current config.
   * No changes are made to the queue.
   */
  private def queuePause() {
    queueActor ! QueueActor.QueuePause()
  }

  /**
   * Processing of component’s queue is started.
   */
  private def queueStart() {
    queueActor ! QueueActor.QueueStart()
  }

  /**
   * Allows removal of a config in the queued execution state.
   */
  private def queueDelete(runId: RunId) {
    queueActor ! QueueActor.QueueDelete(runId)
  }
}
