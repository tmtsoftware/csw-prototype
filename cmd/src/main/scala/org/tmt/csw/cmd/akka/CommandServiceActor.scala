package org.tmt.csw.cmd.akka

import akka.actor._
import org.tmt.csw.cmd.core._
import scala.Some
import org.tmt.csw.cmd.akka.CommandServiceActor._
import org.tmt.csw.cmd.akka.CommandServiceMessage._
import scala.annotation.tailrec

object CommandServiceActor {
  // Queue states
  sealed trait QueueState
  case object Started extends QueueState
  case object Stopped extends QueueState
  case object Paused extends QueueState
}

/**
 * Manages the command queue for a component.
 * QueueConfig objects are placed on the queue when received.
 * Later, each object is dequeued and the config is passed to the config actor for processing.
 */
class CommandServiceActor extends Actor with ActorLogging {

  // The queue for this component (indexed by RunId, so selected items can be removed)
  private var queueMap = Map[RunId, SubmitWithRunId]()

  // The current state of the queue
  private var queueState : QueueState = Started

  // Configurations are sent to this actor when removed from the queue
  private val configDistributorActor = context.actorOf(Props[ConfigDistributorActor], name = "configDistributorActor")

  // Needed for "ask"
  private implicit val execContext = context.dispatcher


  // Receive messages
  override def receive: Receive = {
    // Queue related commands
    case Submit(config, submitter) => queueSubmit(SubmitWithRunId(config, submitter))
    case QueueBypassRequest(config) => queueBypassRequest(SubmitWithRunId(config, sender))
    case QueueStop => queueStop()
    case QueuePause => queuePause(None)
    case QueueStart => queueStart()
    case QueueDelete(runId) => queueDelete(runId)

    // Commands that act on a running config: forward to config actor
    case configMessage: ConfigMessage => configDistributorActor forward configMessage

    // Status Results from ConfigActor: Send the command status with runId to statusActorRef
    case x => log.error(s"Unknown QueueActor message: $x")
  }

  // Queue the given config for later execution and return the runId to the sender
  private def queueSubmit(submit: SubmitWithRunId): Unit = {
    if (queueState != Stopped) {
      queueMap = queueMap + (submit.runId -> submit)
      log.debug(s"Queued config with runId: ${submit.runId}")
      sender ! CommandStatus.Queued(submit.runId)
      if (queueState != Paused) {
        checkQueue()
      }
    }
  }

  // Execute any configs in the queue, unless paused or stopped
  @tailrec
  private def checkQueue(): Unit = {
    log.debug("Check Queue")

    if (queueState == Started && !queueMap.isEmpty) {
      val (runId, submit) = queueMap.iterator.next()
      queueMap = queueMap - runId
      sender ! CommandStatus.Busy(runId)
      if (submit.config.isWaitConfig) {
        log.debug("Pausing due to Wait config")
        queuePause(Some(submit.config))
      } else {
        log.debug(s"Submitting config with runId: $runId")
        configDistributorActor ! submit
      }
      checkQueue()
    }
  }

  // Request immediate execution of the given config
  private def queueBypassRequest(request: SubmitWithRunId): Unit = {
    if (request.config.isWaitConfig) {
      log.debug(s"Queue bypass request: wait config: ${request.runId}")
      queuePause(Some(request.config))
      sender ! CommandStatus.Complete(request.runId)
    } else {
      log.debug(s"Queue bypass request: ${request.runId}")
      configDistributorActor ! request
    }
  }

  // Processing of Configurations in a components queue is stopped.
  // All Configurations currently in the queue are removed.
  // No components are accepted or processed while stopped.
  private def queueStop(): Unit = {
    log.debug("Queue stopped")
    queueState = Stopped
    queueMap = Map.empty
  }

  // Pause the processing of a component’s queue after the completion
  // of the current Configuration. No changes are made to the queue.
  private def queuePause(optionalWaitConfig: Option[Configuration]): Unit = {
    // XXX TODO: handle different types of wait configs
    log.debug("Queue paused")
    queueState = Paused
  }

  // Processing of component’s queue is started.
  private def queueStart(): Unit = {
    log.debug("Queue started")
    queueState = Started
    checkQueue()
  }

  // Delete a config from the queue
  private def queueDelete(runId : RunId): Unit = {
    log.debug(s"Queue delete: $runId")
    queueMap = queueMap - runId
  }
}

