package org.tmt.csw.cmd.akka

import akka.actor._
import scala.collection.mutable
import org.tmt.csw.cmd.core.Configuration
import akka.pattern.ask
import akka.util.Timeout
import org.tmt.csw.cmd.akka.QueueActor._
import scala.concurrent.duration._
import scala.Some

object QueueActor {
  // Actor messages received
  sealed trait QueueActorMessage
  case class QueueSubmit(queueConfig : QueueConfig) extends QueueActorMessage
  case class QueueBypassRequest(queueConfig : QueueConfig, t: Timeout) extends QueueActorMessage
  case class QueueStop() extends QueueActorMessage
  case class QueuePause() extends QueueActorMessage
  case class QueueStart() extends QueueActorMessage
  case class QueueDelete(runId : RunId) extends QueueActorMessage

  // Queue states
  sealed trait QueueState
  case class Started() extends QueueState
  case class Stopped() extends QueueState
  case class Paused() extends QueueState
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

  // Maps runId to the actor that is executing it
  private val configMap = mutable.LinkedHashMap[RunId, ActorRef]()

  private var queueState : QueueState = Started()

  def receive = {
    case QueueSubmit(queueConfig) => queueSubmit(queueConfig)
    case QueueBypassRequest(queueConfig, timeout) => queueBypassRequest(queueConfig, timeout)
    case QueueStop() => queueStop()
    case QueuePause() => queuePause(None)
    case QueueStart() => queueStart()
    case QueueDelete(runId) => queueDelete(runId)
    case x => log.error(s"Unknown QueueActor message: $x"); sender ! Status.Failure(new IllegalArgumentException)

  }

  // Queue the given config for later execution and return the runId to the sender
  private def queueSubmit(qc: QueueConfig) {
    if (queueState != Stopped()) {
      queueMap(qc.runId) = qc.config
      log.debug(s"Queued config with runId: ${qc.runId}")
      sender ! CommandStatus.StatusQueued(qc.runId)
      if (queueState != Paused()) {
        checkQueue()
      }
    }
  }

  // Execute any configs in the queue, unless paused or stopped
  private def checkQueue() {
    log.debug("Check Queue")
    while (queueState == Started() && !queueMap.isEmpty) {
      val (runId, config) = queueMap.iterator.next()
      queueMap.remove(runId)
      sender ! CommandStatus.StatusBusy(runId)
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
      sender ! CommandStatus.StatusComplete(qc.runId)
    } else {
      log.debug(s"Queue bypass request: ${qc.runId}")
      submitConfig(qc.runId, qc.config, t)
    }
  }

  // Submit the config to a new config actor for execution.
  // The config actor is only used once for this config and then killed.
  // The sender is notified of the status when done.
  private def submitConfig(runId: RunId, config: Configuration, t: Timeout) {
    implicit val timeout = t
    implicit val execContext = context.dispatcher
    val configActor = context.actorOf(configActorProps)
    configMap(runId) = configActor
    val f = configActor ? ConfigActor.ConfigSubmit(config)
    f onSuccess {
      case _ => sender ! CommandStatus.StatusComplete(runId); stopActor(runId, configActor)
    }
    f onFailure {
      case e: Exception => sender ! CommandStatus.StatusError(runId, e); stopActor(runId, configActor)
    }
  }

  // Clean up after an actor is done
  private def stopActor(runId: RunId, configActor: ActorRef) {
    configActor ! PoisonPill
    configMap.remove(runId)
  }

  // Processing of Configurations in a components queue is stopped.
  // All Configurations currently in the queue are removed.
  // No components are accepted or processed while stopped.
  private def queueStop() {
    log.debug("Queue stopped")
    queueState = Stopped()
    queueMap.clear()
  }

  // Pause the processing of a component’s queue after the completion
  // of the current Configuration. No changes are made to the queue.
  private def queuePause(optionalWaitConfig: Option[Configuration]) {
    // XXX TODO: handle different types of wait configs
    log.debug("Queue paused")
    queueState = Paused()
  }

  // Processing of component’s queue is started.
  private def queueStart() {
    log.debug("Queue started")
    queueState = Started()
    checkQueue()
  }

  // Delete a config from the queue
  private def queueDelete(runId : RunId) {
    log.debug(s"Queue delete: $runId")
    queueMap.remove(runId)
  }
}

