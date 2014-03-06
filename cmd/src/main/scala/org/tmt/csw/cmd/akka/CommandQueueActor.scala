package org.tmt.csw.cmd.akka

import akka.actor._
import org.tmt.csw.cmd.core.Configuration
import org.tmt.csw.cmd.akka.CommandServiceActor.StatusRequest
import org.tmt.csw.cmd.akka.CommandStatusActor.StatusUpdate


object CommandQueueActor {

  /**
   * Used to create the CommandQueueActor.
   * @param commandStatusActor reference to the commandStatusActor
   */
  def props(commandStatusActor: ActorRef): Props = Props(classOf[CommandQueueActor], commandStatusActor)

  // Queue states
  sealed trait QueueState

  case object Started extends QueueState

  case object Stopped extends QueueState

  case object Paused extends QueueState

  // Messages received by this actor
  sealed trait QueueMessage

  /**
   * Submit a configuration with an assigned runId
   * @param config the configuration
   * @param submitter the actor submitting the config (will receive status messages)
   * @param runId the unique runId
   */
  case class SubmitWithRunId(config: Configuration, submitter: ActorRef, runId: RunId = RunId()) extends QueueMessage

  /**
   * Message to stop the command queue
   */
  case object QueueStop extends QueueMessage

  /**
   * Message to pause the command queue
   */
  case object QueuePause extends QueueMessage

  /**
   * Message to restart the command queue
   */
  case object QueueStart extends QueueMessage

  /**
   * Message to delete the given runId from the command queue
   */
  case class QueueDelete(runId: RunId) extends QueueMessage


  /**
   * The config at the front of the queue is removed and sent to the sender
   */
  case object Dequeue extends QueueMessage

  /**
   * Identifies the target client
   */
  case class QueueClient(client: ActorRef) extends QueueMessage

  /**
   * Identifies the queue controller
   */
  case class QueueController(controller: ActorRef) extends QueueMessage

  /**
   * Message to queue client that there is work to do in the queue
   */
  case object QueueWorkAvailable extends QueueMessage

  /**
   * Message to optional listener that there is work to do in the queue
   */
  case object QueueReady extends QueueMessage

  /**
   * Reply to StatusRequest message
   */
  case class ConfigQueueStatus(status: String, queueMap: Map[RunId, SubmitWithRunId], count: Int)
}

/**
 * Implements the queue for the command service.
 * @param commandStatusActor reference to the commandStatusActor
 */
class CommandQueueActor(commandStatusActor: ActorRef)
  extends Actor with ActorLogging with Stash {

  import CommandQueueActor._

  // The queue (indexed by RunId, so selected items can be removed)
  private var queueMap = Map[RunId, SubmitWithRunId]()

  // The actor receiving and processing items from the queue
  private var queueClient: ActorRef = Actor.noSender

  // The queue controller actor
  private var queueController: ActorRef = Actor.noSender

  // The number of commands submitted
  private var submitCount = 0

  // Needed for "ask"
  private implicit val execContext = context.dispatcher


  // Initial behavior while waiting for the queue client and controller actor references on startup.
  def waitingForInit: Receive = {
    case s: SubmitWithRunId => queueSubmit(s)
    case QueueClient(client) => initClient(client)
    case QueueController(controller) => initController(controller)
    case StatusRequest => sender ! ConfigQueueStatus("waiting for init", queueMap, submitCount)
    case m: QueueMessage => stash() // save other queue messages for later
    case x => unknownMessage(x, "waiting for queue client")
  }

  // Behavior while the queue is in the stopped state
  def queueStopped: Receive = {
    case QueuePause => queuePause()
    case QueueStart => queueStart()
    case QueueStop =>
    case Dequeue =>
    case StatusRequest => sender ! ConfigQueueStatus("stopped", queueMap, submitCount)
    case x => unknownMessage(x, "stopped")
  }

  // Behavior while the queue is in the paused state
  def queuePaused: Receive = {
    case s: SubmitWithRunId => queueSubmit(s)
    case QueueStop => queueStop()
    case QueuePause =>
    case QueueStart => queueStart()
    case QueueDelete(runId) => queueDelete(runId)
    case Dequeue =>
    case StatusRequest => sender ! ConfigQueueStatus("paused", queueMap, submitCount)
    case x => unknownMessage(x, "paused")
  }

  // Behavior while the queue is in the started state
  def queueStarted: Receive = {
    case s: SubmitWithRunId =>
      queueSubmit(s)
      notifyQueueController()
    case QueueStop => queueStop()
    case QueuePause => queuePause()
    case QueueStart =>
    case QueueDelete(runId) => queueDelete(runId)
    case Dequeue => dequeue()
    case StatusRequest => sender ! ConfigQueueStatus("started", queueMap, submitCount)
    case x => unknownMessage(x, "started")
  }

  override def receive: Receive = waitingForInit

  // Queue the given config for later execution and return the runId to the sender
  private def queueSubmit(submit: SubmitWithRunId): Unit = {
    queueMap = queueMap + (submit.runId -> submit)
    log.debug(s"Queued config with runId: ${submit.runId}")
    log.info(s"Queued config: ${submit.config}")
    commandStatusActor ! StatusUpdate(CommandStatus.Queued(submit.runId), submit.submitter)
    submitCount = submitCount + 1
  }

  // Processing of Configurations in a components queue is stopped.
  // All Configurations currently in the queue are removed.
  // No components are accepted or processed while stopped.
  private def queueStop(): Unit = {
    log.debug("Queue stopped")
    queueMap = Map.empty
    context become queueStopped
  }

  // Pause the processing of a component’s queue after the completion
  // of the current Configuration. No changes are made to the queue.
  private def queuePause(): Unit = {
    // XXX TODO: handle different types of wait configs
    log.debug("Queue paused")
    context become queuePaused
  }

  // Processing of component’s queue is started.
  private def queueStart(): Unit = {
    log.debug("Queue started")
    context become queueStarted
    notifyQueueController()
  }

  // Delete a config from the queue
  private def queueDelete(runId: RunId): Unit = {
    log.debug(s"Queue delete: $runId")
    queueMap = queueMap - runId
  }

  // Removes the config at the front of the queue and sends it to the config actor for execution.
  // Wait configs are treated specially (TODO: Implement wait configs)
  // The original submitter receives the command status.
  private def dequeue(): Unit = {
    log.debug(s"Dequeue and sent to: $sender")
    if (!queueMap.isEmpty) {
      val (runId, submit) = queueMap.iterator.next()
      queueMap = queueMap - runId
      queueClient ! submit
    }
  }

  // Sets the client actor
  private def initClient(client: ActorRef): Unit = {
    queueClient = client
    maybeStartQueue()
  }

  // Sets the client actor
  private def initController(controller: ActorRef): Unit = {
    queueController = controller
    maybeStartQueue()
  }

  private def maybeStartQueue(): Unit = {
    if (queueClient != Actor.noSender && queueController != Actor.noSender) {
      unstashAll()
      queueStart()
    }
  }

  // Notify the queue controller if there are messages in the queue.
  // The queue controller should then send a Dequeue message to remove a message from the queue when ready.
  private def notifyQueueController(): Unit = {
    if (!queueMap.isEmpty) {
      queueController ! QueueWorkAvailable
    }
  }

  private def unknownMessage(msg: Any, state: String): Unit =
    log.warning(s"Received unexpected queue message from $sender while $state: $msg")
}
