package csw.services.cmd

import akka.actor.{ ActorRef, ActorLogging, Actor }
import csw.shared.cmd.CommandStatus
import csw.util.config.Configurations._

import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.{ Failure, Success, Try }

/**
 * Command service controller
 */
object Controller {

  /**
   * Base trait of all received messages
   */
  sealed trait HcdControllerMessage

  /**
   * Tells the controller to check its inputs and update its outputs
   */
  case object Process extends HcdControllerMessage

  /**
   * The sender of this message will receive as a reply a ConfigResponse object
   */
  case object ConfigGet extends HcdControllerMessage

  /**
   * The response from a ConfigGet message
   * @param tryConfig if the requested config could be retrieved, Success(config), otherwise Failure(ex)
   */
  case class ConfigResponse(tryConfig: Try[SetupConfig])

  // The items placed in the queue contain the config along with the original submitter/sender
  private case class QueueItem(configArg: ControlConfigArg, submitter: ActorRef)

}

/**
 * Base trait of command service controller actors.
 * This is the main actor that accepts setup or observe configurations to execute.
 */
trait Controller extends Actor with ActorLogging {

  import Controller._
  import context.dispatcher

  /**
   * The controller update rate: The controller inputs and outputs are processed at this rate
   */
  def rate: FiniteDuration

  context.become(running(Queue.empty[QueueItem]))

  // Sends the Update message at the specified rate
  context.system.scheduler.schedule(Duration.Zero, rate, self, Process)

  def receive: Receive = {
    case x ⇒ log.error(s"Received unexpected message $x")
  }

  def running(queue: Queue[QueueItem]): Receive = {
    case Process ⇒
      process(queue)

    case configArg: ControlConfigArg ⇒
      placeInQueue(queue, configArg)

    case ConfigGet ⇒
  }

  /**
   * Places the config arg in the queue
   */
  private def placeInQueue(queue: Queue[QueueItem], configArg: ControlConfigArg): Unit = {
    context.become(running(queue.enqueue(QueueItem(configArg, sender()))))
  }

  /**
   * First removes and processes any items in the queue, then updates the current values.
   */
  private def process(queue: Queue[QueueItem]): Unit = {
    if (queue.nonEmpty) {
      val (queueItem, q) = queue.dequeue
      context.become(running(q))
      val runId = queueItem.configArg.info.runId
      val f = processConfigArg(queueItem.configArg)
      f.onComplete {
        // XXX Send status to submitter here or wait for requested value to equal actual value?
        case Success(_) ⇒
          queueItem.submitter ! CommandStatus.Completed(runId)
          self ! Process
        case Failure(ex) ⇒
          queueItem.submitter ! CommandStatus.Error(runId, ex.getMessage)
          self ! Process
      }
    } else updateValues()
  }

  /**
   * Processes the given config arg and returns a future indicating when it is
   * ready for the next config arg.
   */
  protected def processConfigArg(configArg: ControlConfigArg): Future[Unit]

  /**
   * Gets the current device values and updates the key/value store
   */
  protected def updateValues(): Unit
}
