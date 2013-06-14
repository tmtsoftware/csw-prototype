package org.tmt.csw.cmd.akka

import akka.actor._
import akka.pattern.ask
import scala.concurrent.Future
import akka.util.Timeout
import org.tmt.csw.cmd.core.Configuration
import org.tmt.csw.cmd.akka.CommandServiceActor._

/**
 * Contains actor messages received
 */
object CommandServiceActor {

  def props(configActorProps: Props, componentName: String) =
    Props(classOf[CommandServiceActor], configActorProps, componentName)

  // TMT Standard Queue Interaction Commands
  sealed trait QueueInteractionCommand
  case class QueueSubmit(configs: Configuration) extends QueueInteractionCommand
  case class QueueBypassRequest(configs: Configuration, timeout: Timeout) extends QueueInteractionCommand
  case class QueueStop() extends QueueInteractionCommand
  case class QueuePause() extends QueueInteractionCommand
  case class QueueStart() extends QueueInteractionCommand
  case class QueueDelete(runId: RunId) extends QueueInteractionCommand
}

/**
 * Implements the TMT Command Service.
 * @param configActorProps used to create the target actor for the command
 */
class CommandServiceActor(configActorProps: Props, componentName: String) extends Actor with ActorLogging {

  // Create the actor that manages the queue for this component
  val queueActor = context.actorOf(QueueActor.props(configActorProps), name = componentName + "Actor")

  def receive = {
    case QueueSubmit(config) => queueSubmit(config)
    case QueueBypassRequest(config, timeout) => queueBypassRequest(config, timeout)
    case QueueStop() => queueStop()
    case QueuePause() => queuePause()
    case QueueStart() => queueStart()
    case QueueDelete(runId) => queueDelete(runId)

    // Status Messages (XXX TODO: send events for these? Or send them directly to the original sender?)
    case CommandStatus.Pending(runId) => log.debug(s"Status: Pending runId: $runId")
    case CommandStatus.Queued(runId) => log.debug(s"Status: Queued runId: $runId")
    case CommandStatus.Busy(runId) => log.debug(s"Status: Busy runId: $runId")
    case CommandStatus.Complete(runId) => log.debug(s"Status: Complete runId: $runId")
    case CommandStatus.Error(runId, ex) => log.error(ex, s"Received error for runId: $runId")
    case CommandStatus.Aborted(runId) => log.info(s"Status: Aborted runId: $runId")
    case x => log.error(s"Unknown CommandServiceActor message: $x"); sender ! Status.Failure(new IllegalArgumentException)
  }

  /**
   * Submit one or more configs to the component's command queue and return the run id.
   */
  private def queueSubmit(config: Configuration) {
    val runId = RunId()
    log.debug(s"Submit config with runId: $runId")
    queueActor ! QueueActor.QueueSubmit(QueueActor.QueueConfig(runId, config))
    sender ! runId
  }

  /**
   * Request immediate execution of one or more configs on the component and return a future with the status
   * (which should be
   */
  private def queueBypassRequest(config: Configuration, t: Timeout) {
    log.debug(s"Request config: $config")
    queueActor forward QueueActor.QueueBypassRequest(QueueActor.QueueConfig(RunId(), config), t)
  }

  /**
   * Processing of Configurations in a components queue is stopped. All Configurations currently in the
   * queue are removed. No components are accepted or processed while stopped.
   */
  private def queueStop() {
    log.debug("Stop")
    queueActor ! QueueActor.QueueStop()
    context.stop(self)
  }

  /**
   * Pause the processing of a component’s queue after the completion of the current config.
   * No changes are made to the queue.
   */
  private def queuePause() {
    log.debug("Pause")
    queueActor ! QueueActor.QueuePause()
  }

  /**
   * Processing of component’s queue is started.
   */
  private def queueStart() {
    log.debug("Start")
    queueActor ! QueueActor.QueueStart()
  }

  /**
   * Allows removal of a config in the queued execution state.
   */
  private def queueDelete(runId: RunId) {
    log.debug("Delete")
    queueActor ! QueueActor.QueueDelete(runId)
  }
}
