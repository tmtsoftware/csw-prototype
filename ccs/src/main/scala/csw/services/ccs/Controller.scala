package csw.services.ccs

import akka.actor.{ ActorRef, ActorLogging, Actor }
import csw.services.loc.LocationService.{ ResolvedService, Disconnected, ServicesReady }
import csw.services.loc.ServiceRef
import csw.util.cfg.Configurations._

import scala.collection.immutable.Queue
import scala.concurrent.duration.{ Duration, FiniteDuration }

/**
 * Command service controller
 */
object HcdController {

  /**
   * The type of the queue of incoming configs
   */
  type HcdQueueType = Queue[SetupConfig]

  /**
   * Base trait of all received messages
   */
  sealed trait HcdControllerMessage

  /**
   * Tells the controller to check its inputs and update its outputs
   */
  case object Process extends HcdControllerMessage

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
  private var queue = Queue.empty[SetupConfig]

  /**
   * This should be used by the implementer actor's receive method.
   * For example: def receive: Receive = receiveCommands orElse receiveLifecycleCommands
   */
  def receive: Receive = additionalReceive orElse {
    case Process ⇒
      process()

    case config: SetupConfig ⇒ queue = queue.enqueue(config)

    case x                   ⇒ log.warning(s"Received unexpected message: $x")
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

/**
 * Base trait for an HCD controller actor that reacts immediately to SetupConfig messages.
 */
trait HcdController extends Actor with ActorLogging {

  /**
   * This should be used by the implementer actor's receive method.
   * For example: def receive: Receive = receiveCommands orElse receiveLifecycleCommands
   */
  override def receive: Receive = additionalReceive orElse {

    case config: SetupConfig ⇒ process(config)

    case x                   ⇒ log.warning(s"Received unexpected message: $x")
  }

  /**
   * Processes the config and updates the current state variable
   */
  protected def process(config: SetupConfig): Unit

  /**
   * Derived classes and traits can extend this to accept additional messages
   */
  protected def additionalReceive: Receive = Actor.emptyBehavior
}

///**
// * Assembly controller
// */
//object AssemblyController {
//
//  /**
//   * Base trait of all received messages
//   */
//  sealed trait AssemblyControllerMessage
//
//}

/**
 * Base trait for an assembly controller actor that reacts immediately to SetupConfigArg messages.
 */
trait AssemblyController extends Actor with ActorLogging {

  override def receive = waitingForServices

  /**
   * Receive state while waiting for required services
   */
  def waitingForServices: Receive = additionalReceive orElse {
    case config: SetupConfigArg ⇒ log.warning(s"Ignoring config since services not connected: $config")

    case ServicesReady(map) ⇒
      connected(map)
      context.become(ready(map))

    case Disconnected ⇒

    case x            ⇒ log.warning(s"Received unexpected message: $x")
  }

  /**
   * Receive state while required services are available
   */
  def ready(services: Map[ServiceRef, ResolvedService]): Receive = additionalReceive orElse {
    case config: SetupConfigArg ⇒ process(services, config, sender())

    case ServicesReady(map)     ⇒ context.become(ready(map))

    case Disconnected           ⇒ context.become(waitingForServices)

    case x                      ⇒ log.warning(s"Received unexpected message: $x")
  }

  /**
   * Called to process the config and reply to the given actor with the command status.
   * The replyTo should receive a CommandStatus.Accepted message, once the config has been validated,
   * and then a CommandStatus.Complete (or Error, Canceled, Aborted) when complete.
   *
   * @param services contains information about any required services
   * @param configArg contains a list of configurations
   * @param replyTo the actor that should receive command status messages for the configArg.
   */
  protected def process(services: Map[ServiceRef, ResolvedService], configArg: SetupConfigArg, replyTo: ActorRef): Unit

  /**
   * Derived classes and traits can extend this to accept additional messages
   */
  protected def additionalReceive: Receive

  /**
   * Derived classes and traits can extend this to be notified when required services are ready
   * @param services maps serviceRef to information that includes the host, port and actorRef
   */
  protected def connected(services: Map[ServiceRef, ResolvedService]): Unit = {}

  /**
   * Derived classes and traits can extend this to be notified when required services are disconnected
   */
  protected def disconnected(): Unit = {}

}

