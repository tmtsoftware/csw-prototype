package csw.services.ccs

import akka.actor.{Actor, ActorLogging}
import csw.util.akka.PublisherActor
import csw.util.config.StateVariable.CurrentState
import csw.util.config.Configurations._

/**
 * Command service controller
 */
object HcdController {

  /**
   * Base trait of messages received by HcdController
   */
  sealed trait HcdControllerMessage

  /**
   * Message to submit a configuration to the HCD
   *
   * @param config describes the setup parameters to which the HCD should be configured
   */
  final case class Submit(config: SetupConfig) extends HcdControllerMessage

  // --- Inherited messages that this actor receives ---

  /**
   * Message to subscribe the sender to the HCD's state.
   * The sender will receive [[CurrentState]] messages from the HCD whenever it's state changes.
   */
  val Subscribe = PublisherActor.Subscribe

  /**
   * Message to unsubscribes from the HCD's state messages.
   */
  val Unsubscribe = PublisherActor.Unsubscribe

  /**
   * Message to request that the HCD's current state be sent to all subscribers
   */
  val RequestCurrent = PublisherActor.RequestCurrent
}

/**
 * Base trait for an HCD controller actor that reacts immediately to SetupConfig messages.
 */
trait HcdController extends PublisherActor[CurrentState] {
  this: Actor =>

  import HcdController._

  /**
   * This should be used by the implementer actor's receive method.
   * For example: def receive: Receive = controllerReceive orElse ...
   */
  protected def controllerReceive: Receive = publisherReceive orElse {
    case Submit(config)  => process(config)

    // Can be used by related actors to post the current status
    case s: CurrentState => notifySubscribers(s)
  }

  /**
   * A derived class should process the given config and, if oneway is false, either call
   * notifySubscribers() or send a CurrentState message to itself
   * (possibly from a worker actor) to indicate changes in the current HCD state.
   *
   * @param config            the config received
   */
  protected def process(config: SetupConfig): Unit
}

