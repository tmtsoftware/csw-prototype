package csw.services.ccs

import csw.util.config.Configurations.SequenceConfig
import csw.util.config.RunId





object CommandStatus2 {

  /**
    * Command status
    */



  sealed trait ValidationIssue { def reason: String }
  final case class MissingKeyIssue(reason: String) extends ValidationIssue
  final case class WrongConfigKeyIssue(reason: String) extends ValidationIssue
  final case class WrongItemTypeIssue(reason: String) extends ValidationIssue
  final case class WrongUnitsIssue(reason: String) extends ValidationIssue
  final case class WrongNumberOfParametersIssue(reason: String) extends ValidationIssue
  // Returned when an Assembly receives a ConfigArg with multiple configs, but it can only execute one at a time
  final case class SingleConfigOnlyIssue(reason: String) extends ValidationIssue
  final case class UnresolvedLocationsIssue(reason: String) extends ValidationIssue
  final case class OtherIssue(reason: String) extends ValidationIssue

  /**
    * Base trait for the results of validating incoming configs
    */
  sealed trait Validation

  /**
    * Indicates a valid input config
    */
  case object Valid extends Validation

  /**
    * Indicates an invalid input config
    *
    */
  final case class Invalid(issue: ValidationIssue) extends Validation


    /**
      * Returns a name for the status
      */
    //def name: String = this.getClass.getSimpleName.toLowerCase

    sealed trait CommandStatus2

  /**
    * The command was valid when received, but is no longer valid because of itervening activities
    */
  final case class NoLongerValid(issue: ValidationIssue) extends CommandStatus2

  /**
    * The command has completed successfully
    */
  final case object Completed extends CommandStatus2

  /**
    * If a multi-config arg has an error, overall is set to Incomplete
    */
  final case object Incomplete extends CommandStatus2

  /**
    * The command is currently executing or has not yet started
    * When used for overall, it indicates that some commands may be complete and some have not yet executed or are executing
    * When used for a specific command, it indicates the command has not yet executed or is currently executing
    */
  final case class InProgress(message: String) extends CommandStatus2

  /**
    * The command was started, but failed with the given message
    */
  final case class Error(message: String) extends CommandStatus2

  /**
    * The command was aborted
    * Aborted means that the command/actions were stopped immediately.
    */
  final case object Aborted extends CommandStatus2

  /**
    * The command was canceled
    * Cancelled means the command/actions were stopped at the next convenient place. This is usually appropriate for
    */
  final case object Canceled extends CommandStatus2

  type ExecResult = (CommandStatus2, SequenceConfig)

  final case class ExecResults(results: List[ExecResult] = List.empty[ExecResult]) {
    def :+(pair: ExecResult) = ExecResults(results = results :+ pair)
  }

  /**
    * The unique id for the command
    */
  final case class CommandResult(runId: RunId, overall: CommandStatus2, details: ExecResults)

}
