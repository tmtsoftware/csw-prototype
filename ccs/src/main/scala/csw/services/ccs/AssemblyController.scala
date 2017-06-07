package csw.services.ccs

import akka.actor.{Actor, ActorRef}
import csw.services.ccs.Validation.Validation
import csw.util.param.Parameters._

object AssemblyController {

  /**
   * Base trait of all received messages
   */
  sealed trait AssemblyControllerMessage

  /**
   * Message to submit a command to the assembly.
   * The sender will receive CommandStatus messages.
   * If the command is valid, a Accepted message is sent, otherwise an Error.
   * When the work for the command has been completed, a Completed message is sent
   * (or an Error message, if an error occurred).
   *
   * @param command the command to execute
   */
  case class Submit(command: ControlCommand) extends AssemblyControllerMessage

  /**
   * Message to submit a oneway command to the assembly.
   * In this case, the sender will receive only an Accepted (or Error) message,
   * indicating that command is valid (or invalid).
   * There will be no messages on completion.
   *
   * @param command the command to execute
   */
  case class OneWay(command: ControlCommand) extends AssemblyControllerMessage

}

/**
 * Base trait for an assembly controller actor that reacts immediately to Setup or Observe messages.
 */
trait AssemblyController {
  this: Actor =>

  import AssemblyController._

  /**
   * Receive actor messages
   */
  protected def controllerReceive: Receive = {
    case Submit(controlCommand) =>
      controlCommand match {
        case si: Setup   => setupSubmit(si, oneway = false, sender())
        case oi: Observe => observeSubmit(oi, oneway = false, sender())
      }

    case OneWay(controlCommand) =>
      controlCommand match {
        case sca: Setup   => setupSubmit(sca, oneway = true, sender())
        case oca: Observe => observeSubmit(oca, oneway = true, sender())
      }
  }

  /**
   * Called for Submit messages
   *
   * @param s  the Setup received
   * @param oneway  true if no completed response is needed
   * @param replyTo actorRef of the actor that submitted the command
   */
  private def setupSubmit(s: Setup, oneway: Boolean, replyTo: ActorRef): Unit = {
    val completionReplyTo = if (oneway) None else Some(replyTo)
    val validation = setup(s, completionReplyTo)
    // The result for validation is sent here for oneway and submit
    val validationCommandResult = CommandStatus.validationAsCommandStatus(validation)
    replyTo ! validationCommandResult
  }

  /**
   * Called for Observe messages received
   *
   * @param o  the Observe received
   * @param oneway  true if no completed response is needed
   * @param replyTo actorRef of the actor that submitted the command
   */
  private def observeSubmit(o: Observe, oneway: Boolean, replyTo: ActorRef): Unit = {
    val completionReplyTo = if (oneway) None else Some(replyTo)
    val validation = observe(o, completionReplyTo)

    val validationCommandResult = CommandStatus.validationAsCommandStatus(validation)
    replyTo ! validationCommandResult
  }

  /**
   * Called to process a setup command and reply to the given actor with the command status.
   *
   * @param s contains the setup command
   * @param replyTo   if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received command is valid
   */
  protected def setup(s: Setup, replyTo: Option[ActorRef]): Validation = Validation.Valid

  /**
   * Called to process an observe command and reply to the given actor with the command status.
   *
   * @param o contains the observe command
   * @param replyTo   if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received command is valid
   */
  protected def observe(o: Observe, replyTo: Option[ActorRef]): Validation = Validation.Valid
}
