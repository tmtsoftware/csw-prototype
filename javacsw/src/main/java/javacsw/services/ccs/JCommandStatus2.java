package javacsw.services.ccs;

import csw.services.ccs.CommandStatus2;
import csw.services.ccs.Validation;

/**
 * Java access to Scala CommandStatus2 constant objects
 */
public class JCommandStatus2 {

  /**
   * The configuration or set of configurations was valid and started
   */
  public static final CommandStatus2.CommandStatus2 Valid = CommandStatus2.Valid$.MODULE$;


//  /**
//   * The command was valid when received, but is no longer valid because of itervening activities
//   */
//  public static CommandStatus2.CommandStatus2 NoLongerValid(Validation.ValidationIssue issue) {
//      return new CommandStatus2.NoLongerValid(issue);
//  }

  /**
   * The command has completed successfully
   */
  public static final CommandStatus2.CommandStatus2 Completed = CommandStatus2.Completed$.MODULE$;

//  /**
//   * Command Completed with a result
//   * @param result - currently a SetupConfig - would like to add ResultConfiguration to types in Configuration and use it here
//   */
//  final case class CompletedWithResult(result: SetupConfig) extends CommandStatus2

//  /**
//   * The command is currently executing or has not yet started
//   * When used for a specific command, it indicates the command has not yet executed or is currently executing and is providing an update
//   */
//  final case class InProgress(message: String = "") extends CommandStatus2

//  /**
//   * The command was started, but ended with error with the given message
//   */
//  final case class Error(message: String) extends CommandStatus2

  /**
   * The command was aborted
   * Aborted means that the command/actions were stopped immediately.
   */
  public static final CommandStatus2.CommandStatus2 Aborted = CommandStatus2.Aborted$.MODULE$;

  /**
   * The command was cancelled
   * Cancelled means the command/actions were stopped at the next convenient place. This is usually appropriate for
   */
  public static final CommandStatus2.CommandStatus2 Cancelled = CommandStatus2.Cancelled$.MODULE$;

//  /**
//   * The following describe the overall status of a config arg when sent as a group
//   */
//  sealed trait OverallCommandStatus

  /**
   * A multi-config arg has been accepted for all parts
   */
  public static final CommandStatus2.OverallCommandStatus Accepted = CommandStatus2.Accepted$.MODULE$;

  /**
   * A multi-config arg has failed validiation for one or more reasons
   */
  public static final CommandStatus2.OverallCommandStatus NotAccepted = CommandStatus2.NotAccepted$.MODULE$;

  /**
   * If a multi-config arg has not completed all parts, overall is set to Incomplete
   */
  public static final CommandStatus2.OverallCommandStatus Incomplete = CommandStatus2.Incomplete$.MODULE$;

  /**
   * All parts of a multi-config arg have completed successfully
   */
  public static final CommandStatus2.OverallCommandStatus AllCompleted = CommandStatus2.AllCompleted$.MODULE$;
}
