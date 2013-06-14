package org.tmt.csw.cmd.akka

import akka.actor._
import scala.collection.mutable
import org.tmt.csw.cmd.core.Configuration
import akka.pattern._
import akka.util.Timeout
import org.tmt.csw.cmd.akka.QueueActor._
import scala.concurrent.duration._
import scala.Some
import org.tmt.csw.cmd.akka.ConfigState.ConfigState
import org.tmt.csw.cmd.akka.CommandStatus.CommandStatus

object QueueActor {
  /**
   * Use this to create a queue actor
   * @param configActorProps Props used to create the actor to execute the config
   * @return the Props instance to use to create the queue actor
   */
  def props(configActorProps: Props)  = Props(classOf[QueueActor], configActorProps)

  /**
   * Combines a RunId with a Configuration object.
   * Objects of this type are placed in the command queue.
   */
  case class QueueConfig(runId: RunId, config: Configuration) extends Comparable[QueueConfig] {
    def compareTo(that: QueueConfig): Int = this.runId.id.compareTo(that.runId.id)
  }

  // Actor messages received
  sealed trait QueueActorMessage

  // Messages that operate on the queue
  case class QueueSubmit(queueConfig : QueueConfig) extends QueueActorMessage
  case class QueueBypassRequest(queueConfig : QueueConfig, t: Timeout) extends QueueActorMessage
  case object QueueStop extends QueueActorMessage
  case object QueuePause extends QueueActorMessage
  case object QueueStart extends QueueActorMessage
  case class QueueDelete(runId : RunId) extends QueueActorMessage

  case class ConfigCancel(runId : RunId) extends QueueActorMessage
  case class ConfigAbort(runId : RunId) extends QueueActorMessage
  case class ConfigPause(runId : RunId) extends QueueActorMessage
  case class ConfigResume(runId : RunId) extends QueueActorMessage

  // Queue states
  sealed trait QueueState
  case object Started extends QueueState
  case object Stopped extends QueueState
  case object Paused extends QueueState
}

/**
 * Manages the command queue for a component.
 * QueueConfig objects are placed on the queue when received.
 * Later, each config is dequeued and passed to the component for processing.
 *
 * @param configActorProps used to create the target actor for the command
 */
class QueueActor(configActorProps: Props) extends Actor with ActorLogging {

  // The queue for this OMOA component: maps runId to the configuration being executed
  private val queueMap = mutable.LinkedHashMap[RunId, Configuration]()

  // Links the runId and the actor that is executing it
  private val actorRefForRunId = mutable.LinkedHashMap[RunId, ActorRef]()
  private val runIdForActorRef = mutable.LinkedHashMap[ActorRef, RunId]()

  private var queueState : QueueState = Started

  implicit val execContext = context.dispatcher

  def receive = {
    // Queue related commands
    case QueueSubmit(queueConfig) => queueSubmit(queueConfig)
    case QueueBypassRequest(queueConfig, timeout) => queueBypassRequest(queueConfig, timeout)
    case QueueStop => queueStop()
    case QueuePause => queuePause(None)
    case QueueStart => queueStart()
    case QueueDelete(runId) => queueDelete(runId)

    // Commands that act on a running config
    case ConfigAbort(runId) => configAbort(runId)
    case ConfigCancel(runId) => configCancel(runId)
    case ConfigPause(runId) => configPause(runId)
    case ConfigResume(runId) => configResume(runId)

    case Terminated(configActor)  => cleanupConfigActor(configActor)
    case x => log.error(s"Unknown QueueActor message: $x")

  }

  // Clean up after an config actor is done
  private def cleanupConfigActor(configActor: ActorRef) {
    runIdForActorRef.get(configActor) match {
      case Some(runId) =>
        actorRefForRunId.remove(runId)
        runIdForActorRef.remove(configActor)
      case None => log.error("Unknown configActor died")
    }
  }

  // Queue the given config for later execution and return the runId to the sender
  private def queueSubmit(qc: QueueConfig) {
    if (queueState != Stopped) {
      queueMap(qc.runId) = qc.config
      log.debug(s"Queued config with runId: ${qc.runId}")
      sender ! CommandStatus.Queued(qc.runId)
      if (queueState != Paused) {
        checkQueue()
      }
    }
  }

  // Execute any configs in the queue, unless paused or stopped
  private def checkQueue() {
    log.debug("Check Queue")
    while (queueState == Started && !queueMap.isEmpty) {
      val (runId, config) = queueMap.iterator.next()
      queueMap.remove(runId)
      sender ! CommandStatus.Busy(runId)
      if (config.isWaitConfig) {
        log.debug("Pausing due to Wait config")
        queuePause(Some(config))
      } else {
        log.debug(s"Submitting config with runId: $runId")
        // We need to specify a timeout here, but don't have any idea how long, so just use a large value (24 hours)
        submitConfig(runId, config, 24.hours)
      }
    }
  }

  // Request immediate execution of the given config
  private def queueBypassRequest(qc: QueueConfig, t: Timeout) {
    if (qc.config.isWaitConfig) {
      log.debug(s"Queue bypass request: wait config: ${qc.runId}")
      queuePause(Some(qc.config))
      sender ! CommandStatus.Complete(qc.runId)
    } else {
      log.debug(s"Queue bypass request: ${qc.runId}")
      submitConfig(qc.runId, qc.config, t)
    }
  }

  // Returns a CommandStatus for the given ConfigState
  private def returnStatus(state: ConfigState, runId: RunId): CommandStatus = {
    state match {
      case ConfigState.Completed => CommandStatus.Complete(runId)
      case ConfigState.Canceled => CommandStatus.Aborted(runId)
      case ConfigState.Aborted => CommandStatus.Aborted(runId)
      case x => CommandStatus.Error(runId, new RuntimeException(s"Unexpected message: $x"))
    }
  }

  // Submit the config to a new config actor for execution.
  // The config actor is only used once for this config and then killed.
  // The sender is notified of the status when done.
  private def submitConfig(runId: RunId, config: Configuration, timeout: Timeout) {
    val configActor = context.actorOf(configActorProps)
    actorRefForRunId(runId) = configActor
    runIdForActorRef(configActor) = runId
    context.watch(configActor)
    implicit val t = timeout
    configActor ? ConfigActor.ConfigSubmit(config, t) map {
      case status: ConfigState => returnStatus(status, runId)
    } recover {
      case ex: Exception => CommandStatus.Error(runId, ex)
    } pipeTo sender
  }

  // Processing of Configurations in a components queue is stopped.
  // All Configurations currently in the queue are removed.
  // No components are accepted or processed while stopped.
  private def queueStop() {
    log.debug("Queue stopped")
    queueState = Stopped
    queueMap.clear()
  }

  // Pause the processing of a component’s queue after the completion
  // of the current Configuration. No changes are made to the queue.
  private def queuePause(optionalWaitConfig: Option[Configuration]) {
    // XXX TODO: handle different types of wait configs
    log.debug("Queue paused")
    queueState = Paused
  }

  // Processing of component’s queue is started.
  private def queueStart() {
    log.debug("Queue started")
    queueState = Started
    checkQueue()
  }

  // Delete a config from the queue
  private def queueDelete(runId : RunId) {
    log.debug(s"Queue delete: $runId")
    queueMap.remove(runId)
  }

  /**
   * Actions due to a Configuration should be stopped cleanly as soon
   * as convenient without necessarily completing
   */
  private def configCancel(runId: RunId) {
    log.debug(s"Config Cancel: runId = $runId")
    actorRefForRunId.get(runId) match {
      case Some(configActor) => configActor ! ConfigActor.ConfigCancel
      case None => log.info(s"Can't cancel (may be done already): RunId = $runId")
    }
  }

  /**
   * Actions due to a previous request should be stopped immediately without completing
   */
  private def configAbort(runId: RunId) {
    log.debug(s"Config Abort: runId = $runId")
    actorRefForRunId.get(runId) match {
      case Some(configActor) => configActor ! ConfigActor.ConfigAbort
      case None => log.info(s"Can't abort (may be done already): RunId = $runId")
    }
  }

  /**
   * Pause the actions associated with a specific Configuration
   */
  private def configPause(runId: RunId) {
    log.debug(s"Config Pause: runId = $runId")
    actorRefForRunId.get(runId) match {
      case Some(configActor) => configActor ! ConfigActor.ConfigPause
      case None => log.info(s"Can't pause (may be done already): RunId = $runId")
    }
  }

  /**
   * Resume the paused actions associated with a specific Configuration
   */
  private def configResume(runId: RunId) {
    log.debug(s"Config Resume: runId = $runId")
    actorRefForRunId.get(runId) match {
      case Some(configActor) => configActor ! ConfigActor.ConfigResume
      case None => log.info(s"Can't resume (may be done already): RunId = $runId")
    }
  }
}

