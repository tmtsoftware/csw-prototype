package csw.services.ccs

import csw.util.param.Parameters.Setup

/**
 * TMT Source Code: 10/9/16.
 */
object AssemblyMessages {

  sealed trait AssemblyMessages

  /**
   * Message sent to Assemblies to start a diagnostic mode
   * @param hint
   */
  case class DiagnosticMode(hint: String = "")

  /**
   * Message sent to Assemblies to stop diagnostic mode and return to normal operations
   */
  case object OperationsMode
}

object HcdMessages {

  sealed trait HcdMessages

  /**
   * Message to submit a configuration to the HCD
   *
   * @param config describes the setup parameters to which the HCD should be configured
   */
  final case class Submit(config: Setup) extends HcdMessages

  /**
   * Message to subscribe the sender to the HCD's state.
   * The sender will receive [[csw.util.param.StateVariable.CurrentState]] messages from the HCD whenever it's state changes.
   */
  object Subscribe extends HcdMessages

  /**
   * Message to unsubscribes from the HCD's state messages.
   */
  object Unsubscribe extends HcdMessages

}

