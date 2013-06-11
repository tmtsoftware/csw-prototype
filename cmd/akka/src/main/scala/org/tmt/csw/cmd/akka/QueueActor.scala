package org.tmt.csw.cmd.akka

import akka.actor.{ActorLogging, ActorRef, Status, Actor}
import scala.collection.mutable
import QueueActor._
import org.tmt.csw.cmd.core.Configuration
import _root_.akka.pattern.ask
import akka.util.Timeout

object QueueActor {
  // Actor messages received
  sealed trait QueueActorMessage
  case class QueueSubmit(queueConfig : QueueConfig) extends QueueActorMessage
  case class QueueRequest(queueConfig : QueueConfig, t: Timeout) extends QueueActorMessage
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
 * @param configActor the target actor for the commands
 */
class QueueActor(configActor: ActorRef) extends Actor with ActorLogging {

  // The queue for this OMOA component
  private val queueMap = mutable.LinkedHashMap[RunId, Configuration]()

  private var queueState : QueueState = Started()

  def receive = {
    case QueueSubmit(queueConfig) => queueSubmit(queueConfig)
    case QueueRequest(queueConfig, timeout) => queueRequest(queueConfig, timeout)
    case QueueStop() => queueStop()
    case QueuePause() => queuePause(None)
    case QueueStart() => queueStart()
    case QueueDelete(runId) => queueDelete(runId)
    case _ => sender ! Status.Failure(new IllegalArgumentException)
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

  // Execute any configs in the queue
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
        configActor ! ConfigActor.ConfigSubmit(runId, config)
      }
    }
  }

  // Request immediate execution of the given configs
  private def queueRequest(qc: QueueConfig, t: Timeout) {
    log.debug(s"Queue request: ${qc.runId}")
    if (qc.config.isWaitConfig) {
      queuePause(Some(qc.config))
    } else {
      implicit val timeout = t
      implicit val execContext = context.dispatcher
      val f = configActor ? ConfigActor.ConfigSubmit(qc.runId, qc.config)
      f onSuccess {
        case _ => sender ! CommandStatus.StatusComplete(qc.runId)
      }
      f onFailure {
        case e: Exception => sender ! CommandStatus.StatusError(qc.runId, e)
      }
    }
  }

  // Processing of Configurations in a components queue is stopped.
  // All Configurations currently in the queue are removed.
  // No components are accepted or processed while stopped.
  private def queueStop() {
    log.debug("Queue stopped")
    queueState = Stopped()
    queueMap.clear()
//    context.stop(self)
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

