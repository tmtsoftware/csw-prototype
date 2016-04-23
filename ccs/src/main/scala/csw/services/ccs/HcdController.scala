package csw.services.ccs

import akka.actor.{Actor, ActorLogging, ActorRef}
import csw.util.cfg.Configurations.StateVariable.CurrentState
import csw.util.cfg.Configurations._

/**
 * Command service controller
 */
object HcdController {

  /**
   * Base trait of messages sent or received
   */
  sealed trait HcdControllerMessage

  /**
   * Message to submit a configuration to the HCD
   *
   * @param config describes the setup parameters to which the HCD should be configured
   */
  case class Submit(config: SetupConfig) extends HcdControllerMessage

  /**
   * Subscribes the sender to status messages from the HCD
   */
  case object Subscribe extends HcdControllerMessage

  /**
   * Unsubscribes the sender from status messages from the HCD
   */
  case object Unsubscribe extends HcdControllerMessage

}

/**
 * Base trait for an HCD controller actor that reacts immediately to SetupConfig messages.
 */
trait HcdController {
  this: Actor with ActorLogging ⇒

  import HcdController._

  // List of actors that should receive messages from this HCD with the current state (CurrentState)
  private var subscribers = Set[ActorRef]()

  /**
   * This should be used by the implementer actor's receive method.
   * For example: def receive: Receive = controllerReceive orElse ...
   */
  def controllerReceive: Receive = {

    case Submit(config)  ⇒ process(config)

    case Subscribe       ⇒ subscribe(sender())

    case Unsubscribe     ⇒ unsubscribe(sender())

    // Can be used by related actors to post the current status
    case s: CurrentState ⇒ notifySubscribers(s)

    case x               ⇒ log.warning(s"Received unexpected message: $x")
  }

  // Subscribes the given actorRef to changes in the HCD state
  private def subscribe(actorRef: ActorRef): Unit = {
    subscribers += actorRef
    // Make sure the actor gets the current state right away
    requestCurrentState()
  }

  // Unsubscribes the given actorRef from changes in the HCD state
  private def unsubscribe(actorRef: ActorRef): Unit = {
    subscribers -= actorRef
  }

  /**
   * Notifies any subscribers about the current state
   *
   * @param s the current HCD state
   */
  protected def notifySubscribers(s: CurrentState): Unit = {
    subscribers.foreach(_ ! s)
  }

  /**
   * Derived classes should process the given config and eventually either call
   * notifySubscribers() or send a CurrentState message to itself
   * (possibly from a worker actor) to indicate changes in the current HCD state.
   */
  protected def process(config: SetupConfig): Unit

  /**
   * A request for the current HCD state. The HCD should either call
   * notifySubscribers() or send a CurrentState message to itself
   * (possibly from a worker actor).
   */
  protected def requestCurrentState(): Unit
}

