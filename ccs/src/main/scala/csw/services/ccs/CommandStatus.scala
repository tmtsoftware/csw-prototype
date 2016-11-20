package csw.services.ccs

import csw.services.ccs.Validation.{Validation, ValidationIssue}
import csw.util.config.Configurations.{SequenceConfig, SetupConfig}
import csw.util.config.RunId

import scala.compat.java8.OptionConverters._
import scala.collection.JavaConverters._

object CommandStatus {

  /**
   * Returns a name for the status
   */
  def name: String = this.getClass.getSimpleName.toLowerCase

  sealed trait CommandStatus

  /**
   * The configuration or set of configurations was not valid before starting
   * @param issue an issue that caused the input configuration to be invalid
   */
  final case class Invalid(issue: ValidationIssue) extends CommandStatus

  object Invalid {
    // This is present to support returning a Validation as a CommandStatus
    def apply(in: Validation.Invalid): CommandStatus.Invalid = new CommandStatus.Invalid(in.issue)
  }

  /**
   * Java API: This is present to support returning a Validation as a CommandStatus
   */
  def createInvalid(in: Validation.Invalid): CommandStatus.Invalid = Invalid(in)

  /**
   * Converts a validation result to a CommandStatus result
   * @param validation the result of a validation either Validation.Valid or Validation.Invalid
   * @return cooresponding CommandStatus as CommandStatus.Valid or CommandStatus.Invalid with the identical issue
   */
  def validationAsCommandStatus(validation: Validation): CommandStatus = {
    validation match {
      case Validation.Valid        => CommandStatus.Valid
      case inv: Validation.Invalid => CommandStatus.Invalid(inv.issue)
    }
  }

  /**
   * Function converts a sequence of configs with their corresponding Validations to a Sequence of CommandResultPair
   * that can be included in a CommandResult
   *
   * Note: The validations must be done elsewhere and must be one to one with the configs
   * @param configs sequence of configs that were validated
   * @param validations sequence of validation results cooresponding to the sequence of configs
   * @return sequence of CommandResultPair where validations have been converted to corresponding CommandStatus types
   */
  def validationsToCommandResultPairs(configs: Seq[SequenceConfig], validations: List[Validation]): List[CommandResultPair] =
    validations.map(f => validationAsCommandStatus(f)).zip(configs).map(p => CommandResultPair(p._1, p._2))

  /**
   * Java API: Function converts a list of configs with their corresponding Validations to a list of pairs
   * of (CommandStatus, SequenceConfig) that can be included in a CommandResult
   *
   * Note: The validations must be done elsewhere and must be one to one with the configs
   * @param configs sequence of configs that were validated
   * @param validations sequence of validation results cooresponding to the sequence of configs
   * @return sequence of CommandResultPair where validations have been converted to corresponding CommandStatus types
   */
  def validationsToCommandResultPairs(configs: java.util.List[SequenceConfig], validations: java.util.List[Validation]): java.util.List[CommandResultPair] =
    validationsToCommandResultPairs(configs.asScala, validations.asScala.toList).asJava

  def validationsToOverallCommandStatus(validations: List[Validation]): OverallCommandStatus =
    if (!validations.exists(_ != Validation.Valid)) Accepted else NotAccepted

  // Java API
  def validationsToOverallCommandStatus(validations: java.util.List[Validation]): OverallCommandStatus =
    validationsToOverallCommandStatus(validations.asScala.toList)

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
  case object Valid extends CommandStatus

  /**
   * The command was valid when received, but is no longer valid because of itervening activities
   */
  final case class NoLongerValid(issue: ValidationIssue) extends CommandStatus

  /**
   * The command has completed successfully
   */
  case object Completed extends CommandStatus

  /**
   * Command Completed with a result
   * @param result - currently a SetupConfig - would like to add ResultConfiguration to types in Configuration and use it here
   */
  final case class CompletedWithResult(result: SetupConfig) extends CommandStatus

  /**
   * The command is currently executing or has not yet started
   * When used for a specific command, it indicates the command has not yet executed or is currently executing and is providing an update
   */
  final case class InProgress(message: String = "") extends CommandStatus

  /**
   * The command was started, but ended with error with the given message
   */
  final case class Error(message: String) extends CommandStatus

  /**
   * The command was aborted
   * Aborted means that the command/actions were stopped immediately.
   */
  case object Aborted extends CommandStatus

  /**
   * The command was cancelled
   * Cancelled means the command/actions were stopped at the next convenient place. This is usually appropriate for
   */
  case object Cancelled extends CommandStatus

  /**
   * The following describe the overall status of a config arg when sent as a group
   */
  sealed trait OverallCommandStatus

  /**
   * A multi-config arg has been accepted for all parts
   */
  case object Accepted extends OverallCommandStatus

  /**
   * A multi-config arg has failed validiation for one or more reasons
   */
  case object NotAccepted extends OverallCommandStatus

  /**
   * If a multi-config arg has not completed all parts, overall is set to Incomplete
   */
  case object Incomplete extends OverallCommandStatus

  /**
   * All parts of a multi-config arg have completed successfully
   */
  case object AllCompleted extends OverallCommandStatus

  // Using a class instead of a pair to make it easier for Java to access...
  case class CommandResultPair(status: CommandStatus, config: SequenceConfig)

  final case class CommandResults(results: List[CommandResultPair] = List.empty[CommandResultPair]) {
    def :+(pair: CommandResultPair) = CommandResults(results = results :+ pair)
    def status(index: Int): CommandStatus = results(index).status
    def config(index: Int): SequenceConfig = results(index).config

    /**
     * Java API to access results
     */
    def getResults: java.util.List[akka.japi.Pair[CommandStatus, SequenceConfig]] = {
      results.map(p => new akka.japi.Pair(p.status, p.config)).asJava
    }
  }

  /**
   * The unique id for the command
   */
  final case class CommandResult(runId: RunId, overall: OverallCommandStatus, details: CommandResults)
}
