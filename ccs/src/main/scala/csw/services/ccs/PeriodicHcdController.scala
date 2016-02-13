package csw.services.ccs

import java.util.concurrent.TimeUnit

import akka.actor.{ActorLogging, Actor}
import com.typesafe.config.Config
import csw.util.cfg.Configurations.SetupConfig

import scala.collection.immutable.Queue
import scala.concurrent.duration._

object PeriodicHcdController {

  /**
   * Base trait of received messages
   */
  sealed trait PeriodicHcdControllerMessage

  /**
   * Tells the controller to check its inputs and update its outputs
   * @param interval the amount of time until the next update
   */
  case class Process(interval: FiniteDuration) extends PeriodicHcdControllerMessage
}

/**
 * Base trait for an HCD controller actor that checks its queue for inputs and updates its
 * state variables at a given rate.
 */
trait PeriodicHcdController extends Actor with ActorLogging {

  import HcdController._
  import PeriodicHcdController._
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
    case msg @ Process(interval) ⇒
      try process() catch {
        case ex: Exception ⇒ log.error(s"Failed to process message", ex)
      }
      // Schedule the next update
      context.system.scheduler.scheduleOnce(interval, self, msg)

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
   * Event periodic HCD should call this method once to start processing.
   *
   * If the "rate" key is specified in the config, use it, otherwise the default rate to
   * send a message to self to start the periodic processing.
   * Each time the Process message is received, a timer should be started again
   * for the given duration (A repeating timer would risk continuing after an actor crashes).
   *
   * @param conf the component's config file
   * @param defaultRate the default duration to use if not defined in the config
   */
  protected def startProcessing(conf: Config, defaultRate: FiniteDuration = 1.second): Unit = {
    import scala.concurrent.duration._
    val rate = if (conf.hasPath("rate"))
      FiniteDuration(conf.getDuration("rate", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
    else defaultRate
    self ! PeriodicHcdController.Process(rate)
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
  protected def additionalReceive: Receive
}
