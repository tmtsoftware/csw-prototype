package csw.services.ccs

import akka.actor.{ ActorLogging, Actor }
import csw.util.cfg.Configurations._

import scala.collection.immutable.Queue

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
   * Message to submit a configuration to the HCD
   * @param config describes the setup parameters to which the HCD should be configured
   */
  case class Submit(config: SetupConfig)

  /**
   * Tells the controller to check its inputs and update its outputs
   */
  case object Process extends HcdControllerMessage
}

/**
 * Base trait for an HCD controller actor that reacts immediately to SetupConfig messages.
 */
trait HcdController extends Actor with ActorLogging {

  import HcdController._

  /**
   * This should be used by the implementer actor's receive method.
   * For example: def receive: Receive = receiveCommands orElse receiveLifecycleCommands
   */
  override def receive: Receive = additionalReceive orElse {

    case Submit(config) ⇒ process(config)

    case x              ⇒ log.warning(s"Received unexpected message: $x")
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

