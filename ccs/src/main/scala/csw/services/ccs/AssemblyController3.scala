package csw.services.ccs

import akka.actor.{Actor, ActorRef}
import csw.services.ccs.Validation.Validation
import csw.util.config.Configurations3._

object AssemblyController3 {

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
    * @param itemset the configuration to execute
    */
  case class Submit(itemset: ControlItemSet) extends AssemblyControllerMessage

  /**
    * Message to submit a oneway config to the assembly.
    * In this case, the sender will receive only an Accepted (or Error) message,
    * indicating that config is valid (or invalid).
    * There will be no messages on completion.
    *
    * @param itemset the configuration to execute
    */
  case class OneWay(itemset: ControlItemSet) extends AssemblyControllerMessage

}

/**
  * Base trait for an assembly controller actor that reacts immediately to SetupConfigArg messages.
  */
trait AssemblyController3 {
  this: Actor =>

  import AssemblyController3._

  // Optional actor waiting for current HCD states to match demand states
  //private var stateMatcherActor: Option[ActorRef] = None

  /**
    * Receive actor messages
    */
  protected def controllerReceive: Receive = {
    case Submit(configArg) =>
      configArg match {
        case si: Setup => setupSubmit(si, oneway = false, sender())
        case oi: Observe => observeSubmit(oi, oneway = false, sender())
      }

    case OneWay(configArg) =>
      configArg match {
        case sca: Setup => setupSubmit(sca, oneway = true, sender())
        case oca: Observe => observeSubmit(oca, oneway = true, sender())
      }
  }

  /**
    * Called for Submit messages
    *
    * @param sca     the SetupConfigArg received
    * @param oneway  true if no completed response is needed
    * @param replyTo actorRef of the actor that submitted the config
    */
  private def setupSubmit(sca: Setup, oneway: Boolean, replyTo: ActorRef): Unit = {
    val completionReplyTo = if (oneway) None else Some(replyTo)
    val validation = setup(sca, completionReplyTo)
    // The result for validation is sent here for oneway and submit
    val validationCommandResult = CommandStatus2.validationAsCommandStatus(validation)
    replyTo ! validationCommandResult
  }

  /**
    * Called for Submit messages with observe config arg
    *
    * @param oca     the ObserveConfigArg received
    * @param oneway  true if no completed response is needed
    * @param replyTo actorRef of the actor that submitted the config
    */
  private def observeSubmit(oca: Observe, oneway: Boolean, replyTo: ActorRef): Unit = {
    val completionReplyTo = if (oneway) None else Some(replyTo)
    val validation = observe(oca, completionReplyTo)

    val validationCommandResult = CommandStatus2.validationAsCommandStatus(validation)
    replyTo ! validationCommandResult
  }

  /**
    * Called to process the setup config and reply to the given actor with the command status.
    *
    * @param configArg contains a list of setup configurations
    * @param replyTo   if defined, the actor that should receive the final command status.
    * @return a validation object that indicates if the received config is valid
    */
  protected def setup(configArg: Setup, replyTo: Option[ActorRef]): Validation = Validation.Valid

  /**
    * Called to process the observe config and reply to the given actor with the command status.
    *
    * @param configArg contains a list of observe configurations
    * @param replyTo   if defined, the actor that should receive the final command status.
    * @return a validation object that indicates if the received config is valid
    */
  protected def observe(configArg: Observe, replyTo: Option[ActorRef]): Validation = Validation.Valid
}
