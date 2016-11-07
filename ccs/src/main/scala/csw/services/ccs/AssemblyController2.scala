package csw.services.ccs

import akka.actor.{Actor, ActorRef}
import csw.services.ccs.Validation.Validation
import csw.services.log.PrefixedActorLogging
import csw.util.akka.PublisherActor
import csw.util.config.Configurations.{ControlConfigArg, ObserveConfigArg, SetupConfigArg}
import csw.util.config.StateVariable._

object AssemblyController2 {

  /**
   * Base trait of all received messages
   */
  sealed trait AssemblyControllerMessage

  /**
   * Message to submit a configuration to the assembly.
   * The sender will receive CommandStatus messages.
   * If the config is valid, a Accepted message is sent, otherwise an Error.
   * When the work for the config has been completed, a Completed message is sent
   * (or an Error message, if an error occurred).
   *
   * @param config the configuration to execute
   */
  case class Submit(config: ControlConfigArg) extends AssemblyControllerMessage

  /**
   * Message to submit a oneway config to the assembly.
   * In this case, the sender will receive only an Accepted (or Error) message,
   * indicating that config is valid (or invalid).
   * There will be no messages on completion.
   *
   * @param config the configuration to execute
   */
  case class OneWay(config: ControlConfigArg) extends AssemblyControllerMessage

}

/**
 * Base trait for an assembly controller actor that reacts immediately to SetupConfigArg messages.
 */
trait AssemblyController2 extends PublisherActor[CurrentStates] {
  this: Actor with PrefixedActorLogging =>

  import CommandStatus2._
  import AssemblyController2._
  import context.dispatcher

  // Optional actor waiting for current HCD states to match demand states
  //private var stateMatcherActor: Option[ActorRef] = None

  /**
   * Receive actor messages
   */
  protected def controllerReceive: Receive = publisherReceive orElse {
    case Submit(configArg) =>
      configArg match {
        case sca: SetupConfigArg   => setupSubmit(sca, oneway = false, sender())
        case oca: ObserveConfigArg => observeSubmit(oca, oneway = false, sender())
      }

    case OneWay(configArg) =>
      configArg match {
        case sca: SetupConfigArg   => setupSubmit(sca, oneway = true, sender())
        case oca: ObserveConfigArg => observeSubmit(oca, oneway = true, sender())
      }
  }

  /**
   * Called for Submit messages
   *
   * @param sca  the SetupConfigArg received
   * @param oneway  true if no completed response is needed
   * @param replyTo actorRef of the actor that submitted the config
   */
  private def setupSubmit(sca: SetupConfigArg, oneway: Boolean, replyTo: ActorRef): Unit = {
    val statusReplyTo = if (oneway) None else Some(replyTo)
    val validations = setup(sca, statusReplyTo)
    // The result for validation is sent here for oneway and submit
    val validationCommandResult = validationsToCommandResult(sca.info.runId, sca.configs, validations)
    replyTo ! validationCommandResult
  }

  /**
   * Called for Submit messages with observe config arg
   *
   * @param oca  the ObserveConfigArg received
   * @param oneway  true if no completed response is needed
   * @param replyTo actorRef of the actor that submitted the config
   */
  private def observeSubmit(oca: ObserveConfigArg, oneway: Boolean, replyTo: ActorRef): Unit = {
    val statusReplyTo = if (oneway) None else Some(replyTo)
    val validations = observe(oca, statusReplyTo)

    val validationCommandResult = validationsToCommandResult(oca.info.runId, oca.configs, validations)
    replyTo ! validationCommandResult
  }

  /**
   * Called to process the setup config and reply to the given actor with the command status.
   *
   * @param configArg contains a list of setup configurations
   * @param replyTo   if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  protected def setup(configArg: SetupConfigArg, replyTo: Option[ActorRef]): List[Validation] = List.empty[Validation]

  /**
   * Called to process the observe config and reply to the given actor with the command status.
   *
   * @param configArg contains a list of observe configurations
   * @param replyTo   if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  protected def observe(configArg: ObserveConfigArg, replyTo: Option[ActorRef]): List[Validation] = List.empty[Validation]
}
