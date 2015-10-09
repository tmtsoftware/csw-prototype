package csw.services.ccs

import akka.actor.{ ActorLogging, Actor }
import csw.util.config.Configurations._

import scala.collection.immutable.Queue
import scala.concurrent.duration.{ Duration, FiniteDuration }

/**
 * Command service controller
 */
object Controller {

  /**
   * The type of the queue of incoming configs
   */
  type QueueType = Queue[SetupConfig]

  /**
   * Base trait of all received messages
   */
  sealed trait ControllerMessage

  /**
   * Tells the controller to check its inputs and update its outputs
   */
  case object Process extends ControllerMessage
}

/**
 * Base trait for a controller actor that checks its queue for inputs and updates its
 * state variables at a given rate.
 */
trait PeriodicController extends Actor with ActorLogging {

  import Controller._
  import context.dispatcher

  /**
   * The queue of incoming configs
   */
  private var queue = Queue.empty[SetupConfig]

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
   * The controller update rate: The controller inputs and outputs are processed at this rate
   */
  def rate: FiniteDuration

  /**
   * Periodic method to be implemented by the HCD or assembly.
   * This method can use the nextConfig method to pop the next config from the queue
   * and the key/value store API (kvs) to set the demand and current values.
   */
  protected def process(): Unit


  // Sends the Update message at the specified rate
  context.system.scheduler.schedule(Duration.Zero, rate, self, Process)

  /**
   * This should be used by the implementer actor's receive method.
   * For example: def receive: Receive = receiveCommands orElse receiveLifecycleCommands
   */
  def receiveCommands: Receive = {
    case Process ⇒
      process()

    case config: SetupConfig ⇒
      queue = queue.enqueue(config)

    case x ⇒ log.error(s"Received unexpected message $x")
  }
}


/**
 * Base trait for a controller actor that reacts immediately to SetupConfig messages.
 */
trait Controller extends Actor with ActorLogging {

  /**
   * Processes the config and updates the state variable
   */
  protected def process(config: SetupConfig): Unit

  /**
   * This should be used by the implementer actor's receive method.
   * For example: def receive: Receive = receiveCommands orElse receiveLifecycleCommands
   */
  def receiveCommands: Receive = {

    case config: SetupConfig ⇒
      process(config)
  }
}
