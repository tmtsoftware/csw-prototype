package csw.services.ccs

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorLogging, Actor}
import com.typesafe.config.Config
import csw.util.cfg.Configurations.SetupConfig

import scala.collection.immutable.Queue
import scala.concurrent.duration._

/**
  * Created by gillies on 11/8/15.
  */
object PeriodicHcdController2 {

  /**
    * Base trait of received messages
    */
  sealed trait PeriodicHcdControllerMessage

  /**
    * Tells the controller to check its inputs and update its outputs
    * @param interval the amount of time until the next update
    */
  case class Process(interval: FiniteDuration) extends PeriodicHcdControllerMessage

  case object EndProcess extends PeriodicHcdControllerMessage
}

/**
  * Base trait for an HCD controller actor that checks its queue for inputs and updates its
  * state variables at a given rate.
  */
trait PeriodicHcdController2 {
  this: Actor with ActorLogging =>

  import HcdController._
  import PeriodicHcdController2._
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
      // TODO: What is someone calls startProcessing again
      // Need to cancel current before starting new one
      var currentInterval = interval

      try process() catch {
        case ex: Exception ⇒ log.error(s"Failed to process message", ex)
      }
      // Schedule the next update
      var cancel = context.system.scheduler.scheduleOnce(interval, self, msg)

    case Submit(config) ⇒ queue = queue.enqueue(config)

    case x              ⇒ log.warning(s"Received unexpected xmessage: $x")
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
    * The rate is available to the HCD,
    * It then sends a message to self to start the periodic processing.
    * Each time the Process message is received, a timer should be started again
    * for the given duration (A repeating timer would risk continuing after an actor crashes).
    *
    * @param rate the rate to use for process - default is 1 second
    */
  def startProcessing(rate: FiniteDuration = 1.second): Unit = {
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
  protected def additionalReceive: Receive
}
