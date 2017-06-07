package javacsw.services.ccs;

import csw.services.ccs.Validation.*;

/**
 * Java API for command validation
 */
@SuppressWarnings("unused")
public class JValidation {
  /**
   * The command was valid and started
   */
  public static final Validation Valid = Valid$.MODULE$;

  /**
   * The command was not valid before starting
   */
  public static Validation Invalid(ValidationIssue issue) {
    return new Invalid(issue);
  }

  // --- Convenience methods for creating ValidationIssue instances from Java ---

  // Returned when a command is missing a required key/item
  public static ValidationIssue MissingKeyIssue(String reason) {
    return new MissingKeyIssue(reason);
  }

  // Returned when an Assembly receives a command with a Prefix that it doesn't support
  public static ValidationIssue WrongPrefixIssue(String reason) {
    return new WrongPrefixIssue(reason);
  }

  // Returned when the parameter for a key is not the correct type (i.e. int vs double, etc.)
  public static ValidationIssue WrongParameterTypeIssue(String reason) {
    return new WrongParameterTypeIssue(reason);
  }

  // Returned when a command parameter does not have the correct units
  public static ValidationIssue WrongUnitsIssue(String reason) {
    return new WrongUnitsIssue(reason);
  }

  // Returned when a command does not have the correct number of parameters
  public static ValidationIssue WrongNumberOfParametersIssue(String reason) {
    return new WrongNumberOfParametersIssue(reason);
  }

//  // Returned when an Assembly receives a ConfigArg with multiple configs, but it can only execute one at a time
//  public static ValidationIssue SingleConfigOnlyIssue(String reason) {
//    return new SingleConfigOnlyIssue(reason);
//  }

  // Returned when an Assembly receives a command and one is already executing
  public static ValidationIssue AssemblyBusyIssue(String reason) {
    return new AssemblyBusyIssue(reason);
  }

  // Returned when some required location is not available
  public static ValidationIssue UnresolvedLocationsIssue(String reason) {
    return new UnresolvedLocationsIssue(reason);
  }

  // Parameter of a command is out of range
  public static ValidationIssue ItemValueOutOfRangeIssue(String reason) {
    return new ParameterValueOutOfRangeIssue(reason);
  }

  // The component is in the wrong internal state to handle a configuratio
  public static ValidationIssue WrongInternalStateIssue(String reason) {
    return new WrongInternalStateIssue(reason);
  }

  // A command is unsupported in the current state
  public static ValidationIssue UnsupportedCommandInStateIssue(String reason) {
    return new UnsupportedCommandInStateIssue(reason);
  }

  // A command is unsupported by component
  public static ValidationIssue UnsupportedCommandIssue(String reason) {
    return new UnsupportedCommandIssue(reason);
  }

  // Some other issue!
  public static ValidationIssue OtherIssue(String reason) {
    return new OtherIssue(reason);
  }

}
