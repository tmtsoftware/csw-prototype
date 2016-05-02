package csw.services.ccs

import akka.actor.{Actor, ActorLogging}
import csw.util.akka.PublisherActor
import csw.util.cfg.StateVariable.CurrentState
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
}

/**
 * Base trait for an HCD controller actor that reacts immediately to SetupConfig messages.
 */
trait HcdController extends PublisherActor[CurrentState] {
  this: Actor with ActorLogging ⇒

  import HcdController._

  /**
   * This should be used by the implementer actor's receive method.
   * For example: def receive: Receive = controllerReceive orElse ...
   */
  def controllerReceive: Receive = publisherReceive orElse {
    case Submit(config)  ⇒ process(config)

    // Can be used by related actors to post the current status
    case s: CurrentState ⇒ notifySubscribers(s)
  }

  /**
   * Derived classes should process the given config and eventually either call
   * notifySubscribers() or send a CurrentState message to itself
   * (possibly from a worker actor) to indicate changes in the current HCD state.
   */
  protected def process(config: SetupConfig): Unit
}

