package javacsw.services.ccs;

import csw.services.ccs.CommandStatus;
import csw.services.ccs.Validation;
import csw.services.ccs.Validation.ValidationIssue;

/**
 * Java access to Scala CommandStatus constant objects
 */
public class JCommandStatus {

  /**
   * The configuration or set of configurations was not valid before starting
   * @param issue an issue that caused the input configuration to be invalid
   */
  public static CommandStatus.CommandStatus Invalid(ValidationIssue issue) {
    return new CommandStatus.Invalid(issue);
  }

  // This is present to support returning a Validation as a CommandStatus
  public static CommandStatus.Invalid Invalid(Validation.Invalid in) {
    return CommandStatus.createInvalid(in);
  }

  /**
   * The configuration or set of configurations was valid and started
   */
  public static final CommandStatus.CommandStatus Valid = CommandStatus.Valid$.MODULE$;


  /**
   * The command has completed successfully
   */
  public static final CommandStatus.CommandStatus Completed = CommandStatus.Completed$.MODULE$;

  /**
   * The command was aborted
   * Aborted means that the command/actions were stopped immediately.
   */
  public static final CommandStatus.CommandStatus Aborted = CommandStatus.Aborted$.MODULE$;

  /**
   * The command was cancelled
   * Cancelled means the command/actions were stopped at the next convenient place. This is usually appropriate for
   */
  public static final CommandStatus.CommandStatus Cancelled = CommandStatus.Cancelled$.MODULE$;

  /**
   * A multi-config arg has been accepted for all parts
   */
  public static final CommandStatus.OverallCommandStatus Accepted = CommandStatus.Accepted$.MODULE$;

  /**
   * A multi-config arg has failed validiation for one or more reasons
   */
  public static final CommandStatus.OverallCommandStatus NotAccepted = CommandStatus.NotAccepted$.MODULE$;

  /**
   * If a multi-config arg has not completed all parts, overall is set to Incomplete
   */
  public static final CommandStatus.OverallCommandStatus Incomplete = CommandStatus.Incomplete$.MODULE$;

  /**
   * All parts of a multi-config arg have completed successfully
   */
  public static final CommandStatus.OverallCommandStatus AllCompleted = CommandStatus.AllCompleted$.MODULE$;
}
