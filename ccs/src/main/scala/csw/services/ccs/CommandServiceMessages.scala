package csw.services.ccs

import csw.util.akka.PublisherActor
import csw.util.config.Configurations.{ControlConfigArg, SetupConfig}

/**
 * TMT Source Code: 10/9/16.
 */
object AssemblyMessages {

  sealed trait AssemblyMessages

  /**
   * Message to submit a configuration to the assembly.
   * The sender will receive CommandStatus messages.
   * If the config is valid, a Accepted message is sent, otherwise an Error.
   * When the work for the config has been completed, a Completed message is sent
   * (or an Error message, if an error occurred).
   *
   * @param config the configuration to execute
   */
  final case class Submit(config: ControlConfigArg) extends AssemblyMessages

  /**
   * Message to submit a oneway config to the assembly.
   * In this case, the sender will receive only an Accepted (or Error) message,
   * indicating that config is valid (or invalid).
   * There will be no messages on completion.
   *
   * @param config the configuration to execute
   */
  case class OneWay(config: ControlConfigArg) extends AssemblyMessages
}

object HcdMessages {

  sealed trait HcdMessages

  /**
   * Message to submit a configuration to the HCD
   *
   * @param config describes the setup parameters to which the HCD should be configured
   */
  final case class Submit(config: SetupConfig) extends HcdMessages


  /**
    * Message to subscribe the sender to the HCD's state.
    * The sender will receive [[csw.util.config.StateVariable.CurrentState]] messages from the HCD whenever it's state changes.
    */
  final object Subscribe extends HcdMessages

  /**
    * Message to unsubscribes from the HCD's state messages.
    */
  final object Unsubscribe extends HcdMessages

}

