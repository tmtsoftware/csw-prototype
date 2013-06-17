package org.tmt.csw.cmd.akka

import akka.actor._
import scala.collection.mutable
import org.tmt.csw.cmd.core.Configuration
import akka.pattern._
import akka.util.Timeout
import org.tmt.csw.cmd.akka.QueueActor._
import scala.concurrent.duration._
import scala.Some
import java.util.concurrent.atomic.AtomicReference
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

  // The queue for this component: maps runId to the configuration being executed
  private val queueMap = mutable.LinkedHashMap[RunId, Configuration]()

  // Stores information about a currently executing config
  private case class ConfigInfo(config: Configuration, actor: ActorRef, state: AtomicReference[ConfigState])
  private var configInfoMap = Map[RunId, ConfigInfo]()

  // Links the actor to the runId for the config it is currently executing
  private var runIdForActorRef = Map[ActorRef, RunId]()

  // The current state of the queue
  private var queueState : QueueState = Started

  // Needed for "ask"
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

    // Results from ConfigActor
    case state: ConfigState =>
      runIdForActorRef.get(sender) match {
        case Some(actorRef) => returnStatus(state, actorRef)
        case None => log.error(s"Received status from unknown actor: $state")
      }

    // An actor was terminated (normal when done)
    case Terminated(configActor) => cleanupConfigActor(configActor)

    case x => log.error(s"Unknown QueueActor message: $x")
  }

  // Clean up after an config actor is done
  private def cleanupConfigActor(configActor: ActorRef) {
    runIdForActorRef.get(configActor) match {
      case Some(runId) =>
        configInfoMap -= runId
        runIdForActorRef -= configActor
        log.debug(s"Cleanup up after runId: $runId")
      case None => log.error("Unknown configActor terminated")
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
        submitConfig(runId, config, None)
      }
    }
  }

  // Request immediate execution of the given config
  private def queueBypassRequest(qc: QueueConfig, timeout: Timeout) {
    if (qc.config.isWaitConfig) {
      log.debug(s"Queue bypass request: wait config: ${qc.runId}")
      queuePause(Some(qc.config))
      sender ! CommandStatus.Complete(qc.runId)
    } else {
      log.debug(s"Queue bypass request: ${qc.runId}")
      submitConfig(qc.runId, qc.config, Some(timeout))
    }
  }

  // Returns a CommandStatus for the given ConfigState
  private def returnStatus(state: ConfigState, runId: RunId): CommandStatus = {
    state match {
      case ConfigState.Completed() => CommandStatus.Complete(runId)
      case ConfigState.Canceled() => CommandStatus.Aborted(runId)
      case ConfigState.Aborted() => CommandStatus.Aborted(runId)
      case x => CommandStatus.Error(runId, new RuntimeException(s"Unexpected message: $x"))
    }
  }

  // Submit the config to a new config actor for execution.
  // The config actor is only used once for this config and then killed.
  // The sender is notified of the status when done.
  private def submitConfig(runId: RunId, config: Configuration, timeoutOpt: Option[Timeout]) {
    val configActor = context.actorOf(configActorProps)
    runIdForActorRef += (configActor -> runId)
    context.watch(configActor)
    val state: AtomicReference[ConfigState] = new AtomicReference(ConfigState.Submitted())
    configInfoMap += (runId -> ConfigInfo(config, configActor, state))
    timeoutOpt match {
      case Some(timeout) =>
        implicit val t = timeout
        configActor ? ConfigActor.ConfigSubmit(config, state) map {
          case status: ConfigState => returnStatus(status, runId)
        } recover {
          case ex: Exception => CommandStatus.Error(runId, ex)
        } pipeTo sender
      case None =>
        configActor ! ConfigActor.ConfigSubmit(config, state)
    }
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
    configInfoMap.get(runId) match {
      case Some(configInfo) =>
        configInfo.state.set(ConfigState.Canceled())
        configInfo.actor ! ConfigActor.ConfigCancel
      case None => log.info(s"Can't cancel (may be done already): RunId = $runId")
    }
  }

  /**
   * Actions due to a previous request should be stopped immediately without completing
   */
  private def configAbort(runId: RunId) {
    log.debug(s"Config Abort: runId = $runId")
    configInfoMap.get(runId) match {
      case Some(configInfo) =>
        configInfo.state.set(ConfigState.Aborted())
        configInfo.actor ! ConfigActor.ConfigAbort
      case None => log.info(s"Can't abort (may be done already): RunId = $runId")
    }
  }

  /**
   * Pause the actions associated with a specific Configuration
   */
  private def configPause(runId: RunId) {
    log.debug(s"Config Pause: runId = $runId")
    configInfoMap.get(runId) match {
      case Some(configInfo) =>
        configInfo.state.set(ConfigState.Paused())
        configInfo.actor ! ConfigActor.ConfigPause
      case None => log.info(s"Can't pause (may be done already): RunId = $runId")
    }
  }

  /**
   * Resume the paused actions associated with a specific Configuration
   */
  private def configResume(runId: RunId) {
    log.debug(s"Config Resume: runId = $runId")
    configInfoMap.get(runId) match {
      case Some(configInfo) =>
        configInfo.state.set(ConfigState.Resumed())
        configInfo.actor ! ConfigActor.ConfigResume(configInfo.config, configInfo.state)
      case None => log.info(s"Can't resume (may be done already): RunId = $runId")
    }
  }
}

