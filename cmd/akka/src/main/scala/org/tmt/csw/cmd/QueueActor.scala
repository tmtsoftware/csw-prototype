package org.tmt.csw.cmd

import akka.actor.{Status, Actor}
import scala.collection.mutable
import com.typesafe.config.Config
import QueueActor._

object QueueActor {
  // Actor messages received
  sealed trait QueueActorMessage
  case class QueueSubmit(queueConfig : QueueConfig) extends QueueActorMessage
  case class QueueRequest(queueConfig : QueueConfig) extends QueueActorMessage
  case class QueueStop() extends QueueActorMessage
  case class QueuePause() extends QueueActorMessage
  case class QueueStart() extends QueueActorMessage
  case class QueueDelete(runId : RunId) extends QueueActorMessage
  private case class CheckQueue() extends QueueActorMessage

  // Queue states
  sealed trait QueueState
  case class Started() extends QueueState
  case class Stopped() extends QueueState
  case class Paused() extends QueueState
}

/**
 * Manages the command queue for the given OMOA component.
 * QueueConfig objects are placed on the queue when received.
 * Later, each config is dequeued and passed to the component for processing.
 */
class QueueActor(component: OmoaComponent) extends Actor {

  // The queue for this OMOA component
  private val queueMap = mutable.LinkedHashMap[RunId, Seq[Config]]()

  private var queueState : QueueState = Started()

  def receive = {
    case QueueSubmit(queueConfig) => queueSubmit(queueConfig)
    case CheckQueue => checkQueue()
    case QueueRequest(queueConfig) => queueRequest(queueConfig)
    case QueueStop() => queueStop()
    case QueuePause() => queuePause()
    case QueueStart() => queueStart()
    case QueueDelete(runId) => queueDelete(runId)
    case _ => sender ! Status.Failure(new IllegalArgumentException)
  }

  // Queue the given config for later execution and return the runId to the sender
  private def queueSubmit(config: QueueConfig) {
    if (queueState != Stopped()) {
      queueMap(config.runId) = config.configs
      sender ! CommandStatus.StatusQueued(config.runId)
      if (queueState != Paused()) {
        self ! CheckQueue
      }
    }
  }

  // Execute any configs in the queue
  private def checkQueue() {
    if (queueState == Started()) {
      while (!queueMap.isEmpty) {
        val (runId, configs) = queueMap.iterator.next()
        queueMap.remove(runId)
        sender ! CommandStatus.StatusBusy(runId)
        matchConfigs(runId, configs)
      }
    }
  }

  // Request immediate execution of the given configs
  private def queueRequest(msg: QueueConfig) {
    matchConfigs(msg.runId, msg.configs)
  }

  // Request immediate execution of the given configs
  // XXX TODO: should this be done in an worker actor (so it can be killed)?
  private def matchConfigs(runId: RunId, configs: Seq[Config]) {
    try {
      configs.foreach {
        component.matchConfig(_)
      }
      sender ! CommandStatus.StatusComplete(runId)
    } catch {
      case e: Exception => {
        sender ! CommandStatus.StatusError(runId)
      }
    }
  }

  // Processing of Configurations in a components queue is stopped.
  // All Configurations currently in the queue are removed.
  // No components are accepted or processed while stopped.
  private def queueStop() {
    queueState = Stopped()
    queueMap.clear()
  }

  // Pause the processing of a component’s queue after the completion
  // of the current Configuration. No changes are made to the queue.
  private def queuePause() {
    queueState = Paused()
  }

  // Processing of component’s queue is started.
  private def queueStart() {
    queueState = Started()
    self ! CheckQueue
  }

  // Delete a config from the queue
  private def queueDelete(runId : RunId) {
      queueMap.remove(runId)
  }
}

