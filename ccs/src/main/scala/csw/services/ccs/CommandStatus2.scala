package csw.services.ccs

import csw.services.ccs.Validation.{Validation, ValidationIssue}
import csw.util.config.Configurations.{SequenceConfig, SetupConfig}
import csw.util.config.RunId

object CommandStatus2 {

  /**
   * Returns a name for the status
   */
  def name: String = this.getClass.getSimpleName.toLowerCase

  sealed trait CommandStatus2

  /**
   * The configuration or set of configurations was not valid before starting
   * TODO: Should we return a list of validation issues?
   * @param issue an issue that caused the input configuration to be invalid
   */
  final case class Invalid(issue: ValidationIssue) extends CommandStatus2

  object Invalid {
    // This is present to support returning a Validation as a CommandStatus
    def apply(in: Validation.Invalid): CommandStatus2.Invalid = CommandStatus2.Invalid(in.issue)
  }

  /**
   * Converts a validation result to a CommandStatus result
   * @param validation the result of a validation either Validation.Valid or Validation.Invalid
   * @return cooresponding CommandStatus as CommandStatus.Valid or CommandStatus.Invalid with the identical issue
   */
  def validationAsCommandStatus(validation: Validation): CommandStatus2 = {
    validation match {
      case Validation.Valid        => CommandStatus2.Valid
      case inv: Validation.Invalid => CommandStatus2.Invalid(inv.issue)
    }
  }

  /**
   * Function converts a sequence of configs with their corresponding Validations to a Sequence of CommandResultPair
   * that can be included in a CommandResult
   *
   * Note: The validations must be done elsewhere and must be one to one with the configs
   * @param configs sequence of configs that were validated
   * @param validations sequence of validation results cooresponding to the sequence of configs
   * @return sequence of CommandResultPair where validations have been converted to cooresponding CommandStatus types
   */
  def validationsToCommandResultPairs(configs: Seq[SequenceConfig], validations: List[Validation]): List[CommandResultPair] =
    validations.map(f => validationAsCommandStatus(f)).zip(configs)

  def validationsToOverallCommandStatus(validations: List[Validation]): OverallCommandStatus =
    if (validations.filter(_ != Validation.Valid).isEmpty) Accepted else NotAccepted

  def validationsToCommandResult(runId: RunId, configs: Seq[SequenceConfig], validations: List[Validation]): CommandResult = {
    val commandResultPairs = validationsToCommandResultPairs(configs, validations)
    val overall = validationsToOverallCommandStatus(validations)
    // Currently returning all configs for accepted, but could return empty as in the next line
    //CommandResult(runId, overall, if (overall == Accepted) CommandResults(List.empty[CommandResultPair]) else CommandResults(commandResultPairs))
    CommandResult(runId, overall, CommandResults(commandResultPairs))
  }

  /**
   * The configuration or set of configurations was valid and started
   */
  final case object Valid extends CommandStatus2

  /**
   * The command was valid when received, but is no longer valid because of itervening activities
   */
  final case class NoLongerValid(issue: ValidationIssue) extends CommandStatus2

  /**
   * The command has completed successfully
   */
  final case object Completed extends CommandStatus2

  /**
   * Command Completed with a result
   * @param result - currently a SetupConfig - would like to add ResultConfiguration to types in Configuration and use it here
   */
  final case class CompletedWithResult(result: SetupConfig) extends CommandStatus2

  /**
   * The command is currently executing or has not yet started
   * When used for a specific command, it indicates the command has not yet executed or is currently executing and is providing an update
   */
  final case class InProgress(message: String = "") extends CommandStatus2

  /**
   * The command was started, but ended with error with the given message
   */
  final case class Error(message: String) extends CommandStatus2

  /**
   * The command was aborted
   * Aborted means that the command/actions were stopped immediately.
   */
  case object Aborted extends CommandStatus2

  /**
   * The command was cancelled
   * Cancelled means the command/actions were stopped at the next convenient place. This is usually appropriate for
   */
  case object Cancelled extends CommandStatus2

  /**
   * The following describe the overall status of a config arg when sent as a group
   */
  sealed trait OverallCommandStatus

  /**
   * A multi-config arg has been accepted for all parts
   */
  final case object Accepted extends OverallCommandStatus

  /**
   * A multi-config arg has failed validiation for one or more reasons
   */
  final case object NotAccepted extends OverallCommandStatus

  /**
   * If a multi-config arg has not completed all parts, overall is set to Incomplete
   */
  final case object Incomplete extends OverallCommandStatus

  /**
   * All parts of a multi-config arg have completed successfully
   */
  final case object AllCompleted extends OverallCommandStatus

  type CommandResultPair = (CommandStatus2, SequenceConfig)

  final case class CommandResults(results: List[CommandResultPair] = List.empty[CommandResultPair]) {
    def :+(pair: CommandResultPair) = CommandResults(results = results :+ pair)
    def status(index: Int) = results(index)._1
    def config(index: Int) = results(index)._2
  }

  /**
   * The unique id for the command
   */
  final case class CommandResult(runId: RunId, overall: OverallCommandStatus, details: CommandResults)
}
