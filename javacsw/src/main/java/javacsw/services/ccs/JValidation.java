package javacsw.services.ccs;

import csw.services.ccs.Validation.*;

/**
 * Java API for config validation
 */
public class JValidation {
  /**
   * The configuration or set of configurations was valid and started
   */
  public static final Validation Valid = Valid$.MODULE$;

//  /**
//   * The configuration or set of configurations was not valid before starting
//   */
//  public static Validation Invalid(ValidationIssue issue) {
//    return new Invalid(issue);
//  }
}
