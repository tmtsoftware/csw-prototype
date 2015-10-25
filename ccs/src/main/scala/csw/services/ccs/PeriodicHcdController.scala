package csw.services.ccs

import akka.actor.{ ActorLogging, Actor }
import csw.util.cfg.Configurations.SetupConfig

import scala.collection.immutable.Queue
import scala.concurrent.duration.{ Duration, FiniteDuration }

/**
 * Base trait for an HCD controller actor that checks its queue for inputs and updates its
 * state variables at a given rate.
 */
trait PeriodicHcdController extends Actor with ActorLogging {

  import HcdController._
  import context.dispatcher

  /**
   * The queue of incoming configs
   */
  private var queue = Queue.empty[SetupConfig]

  /**
   * This should be used by the implementer actor's receive method.
   * For example: def receive: Receive = receiveCommands orElse receiveLifecycleCommands
   */
  def receive: Receive = additionalReceive orElse {
    case Process ⇒
      process()

    case Submit(config) ⇒ queue = queue.enqueue(config)

    case x              ⇒ log.warning(s"Received unexpected message: $x")
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
   * Derived classes and traits can extend this to accept additional messages
   */
  protected def additionalReceive: Receive
}
