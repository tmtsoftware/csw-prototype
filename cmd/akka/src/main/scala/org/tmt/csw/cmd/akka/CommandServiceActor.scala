package org.tmt.csw.cmd.akka

import akka.actor._
import akka.util.Timeout
import org.tmt.csw.cmd.core.Configuration
import org.tmt.csw.cmd.akka.CommandServiceActor._
import org.tmt.csw.cmd.akka.CommandStatus.CommandStatus

/**
 * Contains actor messages received
 */
object CommandServiceActor {

  def props(configActorProps: Props, componentName: String) =
    Props(classOf[CommandServiceActor], configActorProps, componentName)

  // TMT Standard Queue Interaction Commands
  sealed trait QueueInteractionCommand
  case class QueueSubmit(configs: Configuration, sender: ActorRef) extends QueueInteractionCommand
  case class QueueBypassRequest(configs: Configuration, timeout: Timeout) extends QueueInteractionCommand
  case object QueueStop extends QueueInteractionCommand
  case object QueuePause extends QueueInteractionCommand
  case object QueueStart extends QueueInteractionCommand
  case class QueueDelete(runId: RunId) extends QueueInteractionCommand

  // Messages that deal with running configs
  sealed trait ConfigInteractionCommand
  case class ConfigCancel(runId : RunId) extends ConfigInteractionCommand
  case class ConfigAbort(runId : RunId) extends ConfigInteractionCommand
  case class ConfigPause(runId : RunId) extends ConfigInteractionCommand
  case class ConfigResume(runId : RunId) extends ConfigInteractionCommand
}

/**
 * Implements the TMT Command Service.
 * @param configActorProps used to create the target actor for the command
 */
class CommandServiceActor(configActorProps: Props, componentName: String) extends Actor with ActorLogging {

  // Create the actor that manages the queue for this component
  private val queueActor = context.actorOf(QueueActor.props(configActorProps, self), name = componentName + "Actor")

  // Maps RunId for a command to the actor that submitted or requested it
  private var senderMap = Map[RunId, ActorRef]()

  def receive = {
    // Queue related commands
    case QueueSubmit(config, actorRef) => queueSubmit(config, actorRef)
    case QueueBypassRequest(config, timeout) => queueBypassRequest(config, timeout)
    case QueueStop => queueStop()
    case QueuePause => queuePause()
    case QueueStart => queueStart()
    case QueueDelete(runId) => queueDelete(runId)

    // Commands that act on a running config
    case ConfigAbort(runId) => configAbort(runId)
    case ConfigCancel(runId) => configCancel(runId)
    case ConfigPause(runId) => configPause(runId)
    case ConfigResume(runId) => configResume(runId)

    // Status Messages
    case status: CommandStatus => replyToSender(status)

//    case CommandStatus.Pending(runId) => log.debug(s"Status: Pending runId: $runId")
//    case CommandStatus.Queued(runId) => log.debug(s"Status: Queued runId: $runId")
//    case CommandStatus.Busy(runId) => log.debug(s"Status: Busy runId: $runId")
//    case CommandStatus.Complete(runId) => log.debug(s"XXX Status: Complete runId: $runId")
//    case CommandStatus.Error(runId, ex) => log.error(ex, s"Received error for runId: $runId")
//    case CommandStatus.Aborted(runId) => log.info(s"Status: Aborted runId: $runId")

    case x => log.error(s"Unknown CommandServiceActor message: $x"); sender ! Status.Failure(new IllegalArgumentException)
  }

  /**
   * Submit the given config to the component's command queue and return the run id.
   * @param config the config to queue for execution
   * @param actorRef the actor that should receive status messages for this runId
   */
  private def queueSubmit(config: Configuration, actorRef: ActorRef) {
    val runId = RunId()
    senderMap += (runId -> actorRef)
    log.debug(s"Submit config with runId: $runId from sender $sender")
    sender ! runId
    queueActor ! QueueActor.QueueSubmit(QueueActor.QueueConfig(runId, config))
  }

  /**
   * Request immediate execution of the given config and return a future with the status
   */
  private def queueBypassRequest(config: Configuration, t: Timeout) {
    log.debug(s"Request config: $config")
    queueActor forward QueueActor.QueueBypassRequest(QueueActor.QueueConfig(RunId(), config), t)
  }

  /**
   * Reply to the original sender of the command with the command status
   */
  private def replyToSender(status: CommandStatus) {
    val senderOpt = senderMap.get(status.runId)
    senderOpt match {
      case Some(actorRef) =>
        log.debug(s"Reply to sender: $actorRef with status: $status")
        actorRef ! status
      case None => log.error("Unknown orginal sender")
    }
    if (status.done) {
      senderMap -= status.runId
    }
  }

  /**
   * Processing of Configurations in a components queue is stopped. All Configurations currently in the
   * queue are removed. No components are accepted or processed while stopped.
   */
  private def queueStop() {
    log.debug("Queue Stop")
    queueActor ! QueueActor.QueueStop
    context.stop(self)
  }

  /**
   * Pause the processing of a component’s queue after the completion of the current config.
   * No changes are made to the queue.
   */
  private def queuePause() {
    log.debug("Queue Pause")
    queueActor ! QueueActor.QueuePause
  }

  /**
   * Processing of component’s queue is started.
   */
  private def queueStart() {
    log.debug("Queue Start")
    queueActor ! QueueActor.QueueStart
  }

  /**
   * Allows removal of a config in the queued execution state.
   */
  private def queueDelete(runId: RunId) {
    log.debug(s"Queue Delete: runId = $runId")
    queueActor ! QueueActor.QueueDelete(runId)
  }

  /**
   * Actions due to a Configuration should be stopped cleanly as soon
   * as convenient without necessarily completing
   */
  private def configCancel(runId: RunId) {
    log.debug(s"Config Cancel: runId = $runId")
    queueActor ! QueueActor.ConfigCancel(runId)
  }

  /**
   * Actions due to a previous request should be stopped immediately without completing
   */
  private def configAbort(runId: RunId) {
    log.debug(s"Config Abort: runId = $runId")
    queueActor ! QueueActor.ConfigAbort(runId)
  }

  /**
   * Pause the actions associated with a specific Configuration
   */
  private def configPause(runId: RunId) {
    log.debug(s"Config Pause: runId = $runId")
    queueActor ! QueueActor.ConfigPause(runId)
  }

  /**
   * Resume the paused actions associated with a specific Configuration
   */
  private def configResume(runId: RunId) {
    log.debug(s"Config Resume: runId = $runId")
    queueActor ! QueueActor.ConfigResume(runId)
  }
}
