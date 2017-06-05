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
  public static CommandStatus.CommandResponse Invalid(ValidationIssue issue) {
    return new CommandStatus.Invalid(issue);
  }

  // This is present to support returning a Validation as a CommandStatus
  public static CommandStatus.Invalid Invalid(Validation.Invalid in) {
    return CommandStatus.createInvalid(in);
  }

  /**
   * The configuration or set of configurations was valid and started
   */
  public static final CommandStatus.CommandResponse Accepted = CommandStatus.Accepted$.MODULE$;


  /**
   * The command has completed successfully
   */
  public static final CommandStatus.CommandResponse Completed = CommandStatus.Completed$.MODULE$;

  /**
   * The command was aborted
   * Aborted means that the command/actions were stopped immediately.
   */
  public static final CommandStatus.CommandResponse Aborted = CommandStatus.Aborted$.MODULE$;

  /**
   * The command was cancelled
   * Cancelled means the command/actions were stopped at the next convenient place. This is usually appropriate for
   */
  public static final CommandStatus.CommandResponse Cancelled = CommandStatus.Cancelled$.MODULE$;
}
