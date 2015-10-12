package csw.services.ccs

import akka.actor.{ ActorLogging, Actor }
import csw.services.loc.LocationService.{ ResolvedService, Disconnected, ServicesReady }
import csw.services.loc.ServiceRef
import csw.util.config.Configurations._
import csw.util.config.StateVariable.DemandState

import scala.collection.immutable.Queue
import scala.concurrent.duration.{ Duration, FiniteDuration }

/**
 * Command service controller
 */
object HcdController {

  /**
   * The type of the queue of incoming configs
   */
  type HcdQueueType = Queue[DemandState]

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
 * Base trait for an HCD controller actor that checks its queue for inputs and updates its
 * state variables at a given rate.
 */
trait PeriodicHcdController extends Actor with ActorLogging {

  import HcdController._
  import context.dispatcher

  /**
   * The queue of incoming configs
   */
  private var queue = Queue.empty[DemandState]

  /**
   * Removes and returns the next SetupConfig from the queue, or None if the queue is empty
   */
  protected def nextConfig: Option[DemandState] = {
    if (queue.nonEmpty) {
      val (config, q) = queue.dequeue
      queue = q
      Some(config)
    } else None
  }

  /**
   * Returns the next SetupConfig in the queue without removing it, or None if the queue is empty
   */
  protected def peekConfig: Option[DemandState] = {
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

    case config: DemandState ⇒ queue = queue.enqueue(config)
  }
}

/**
 * Base trait for an HCD controller actor that reacts immediately to DemandState messages.
 */
trait HcdController extends Actor with ActorLogging {

  /**
   * Processes the config and updates the state variable
   */
  protected def process(config: DemandState): Unit

  /**
   * This should be used by the implementer actor's receive method.
   * For example: def receive: Receive = receiveCommands orElse receiveLifecycleCommands
   */
  def receiveCommands: Receive = {

    case config: DemandState ⇒ process(config)
  }
}

/**
 * Base trait for an assembly controller actor that reacts immediately to SetupConfigArg messages.
 */
trait AssemblyController extends Actor with ActorLogging {

  /**
   * Processes the config and updates the state variable
   */
  protected def process(config: SetupConfigArg): Unit

  /**
   * Called once the required services (HCDs, other assemblies) for this assembly
   * have been located by the location service
   * @param services map with an entry fo each service
   */
  protected def servicesReady(services: Map[ServiceRef, ResolvedService]): Unit

  /**
   * Called when the connection to any of the required services is lost
   */
  protected def disconnected(): Unit

  /**
   * This should be used by the implementer actor's receive method.
   * For example: def receive: Receive = receiveCommands orElse receiveLifecycleCommands
   */
  def receiveCommands: Receive = {

    case config: SetupConfigArg ⇒ process(config)

    case ServicesReady(map)     ⇒ servicesReady(map)

    case Disconnected           ⇒ disconnected()
  }
}

