package csw.services.ccs

import akka.actor._
import csw.util.config.Configurations.SetupConfig

import scala.collection.immutable.Queue
import scala.concurrent.duration._

object PeriodicHcdController {

  /**
   * Base trait of received messages
   */
  sealed trait PeriodicHcdControllerMessage

  /**
   * Tells the controller to check its inputs and update its outputs
   *
   * @param interval the amount of time until the next update
   */
  case class Process(interval: FiniteDuration) extends PeriodicHcdControllerMessage

  /**
   * Tells the controller to stop periodic processing
   */
  case object EndProcess extends PeriodicHcdControllerMessage

}

/**
 * Base trait for an HCD controller actor that checks its queue for inputs and updates its
 * state variables at a given rate.
 *
 * @deprecated use [[HcdController]]
 */
trait PeriodicHcdController {
  this: Actor with ActorLogging =>

  import HcdController._
  import PeriodicHcdController._
  import context.dispatcher

  /**
   * The queue of incoming configs
   */
  private var queue = Queue.empty[SetupConfig]

  private var currentInterval = 1.second

  // This variable stores the current timer to allow cancelling when changing period
  private var timer = new Cancellable {
    override def isCancelled: Boolean = true

    override def cancel(): Boolean = true
  }

  /**
   * This should be used by the implementer actor's receive method.
   * For example: def receive: Receive = receiveCommands orElse receiveLifecycleCommands
   */
  def controllerReceive: Receive = {

    case msg @ Process(newInterval) =>

      try process() catch {
        case ex: Exception => log.error(s"Failed to process message", ex)
      }

      // May need to cancel current timer before starting new one, if new message received before existing timer fires
      if (currentInterval != newInterval) {
        if (!timer.isCancelled) {
          log.info(s"Cancelling current schedule timer of $currentInterval with new value of $newInterval")
          timer.cancel()
        }
        currentInterval = newInterval
      }

      // Schedule the next update
      timer = context.system.scheduler.scheduleOnce(currentInterval, self, msg)

    case EndProcess =>
      if (!timer.isCancelled) {
        log.info(s"Processing stopped")
        timer.cancel()
      }

    case Submit(config) => queue = queue.enqueue(config)

    //case x              => log.warning(s"Received unexpected message: $x")
  }

  /**
   * Removes and returns the next SetupConfig from the queue, or None if the queue is empty
   */
  protected def nextConfig: Option[SetupConfig] = {
    if (queue.nonEmpty) {
      val (config, q) = queue.dequeue
      queue = q
      Some(config)
    } else None
  }

  /**
   * Returns the next SetupConfig in the queue without removing it, or None if the queue is empty
   */
  protected def peekConfig: Option[SetupConfig] = {
    queue.headOption
  }

  /**
   * HCD should call this method once to start processing.
   *
   * The rate is available to the HCD,
   * It then sends a message to self to start the periodic processing.
   * Each time the Process message is received, a timer should be started again
   * for the given duration (A repeating timer would risk continuing after an actor crashes).
   *
   * @param rate the rate to use for process - default is 1 second
   */
  def processAt(rate: FiniteDuration = 1.second): Unit = {
    self ! Process(rate)
  }

  /**
   * Periodic method to be implemented by the HCD or assembly.
   * This method can use the nextConfig method to pop the next config from the queue
   * and the key/value store API (kvs) to set the demand and current values.
   */
  protected def process(): Unit

  /**
   * Derived classes and traits can extend this to accept additional messages
   */
  //protected def additionalReceive: Receive
}
