package csw.services.ccs

/**
  * TMT Source Code: 8/25/16.
  */
object Validation {


  sealed trait ValidationIssue { def reason: String }

  // Returned when a configuration is missing a required key/item
  final case class MissingKeyIssue(reason: String) extends ValidationIssue

  // Returned when an Assembly receives a configuration with a ConfigKey/Prefix that it doesn't support
  final case class WrongConfigKeyIssue(reason: String) extends ValidationIssue

  // Returned when the item for a key is not the correct type (i.e. int vs double, etc.)
  final case class WrongItemTypeIssue(reason: String) extends ValidationIssue

  // Returned when a configuration item does not have the correct units
  final case class WrongUnitsIssue(reason: String) extends ValidationIssue

  // Returned when a configuration does not have the correct number of parameters
  final case class WrongNumberOfParametersIssue(reason: String) extends ValidationIssue

  // Returned when an Assembly receives a ConfigArg with multiple configs, but it can only execute one at a time
  final case class SingleConfigOnlyIssue(reason: String) extends ValidationIssue

  // Returned when an Assembly receives a ConfigArg and one is already executing
  final case class AssemblyBusyIssue(reason: String) extends ValidationIssue

  // Returned when some required location is not available
  final case class UnresolvedLocationsIssue(reason: String) extends ValidationIssue

  // Parameter of a configuration is out of range
  final case class ParameterValueOutOfRangeIssue(reason: String) extends ValidationIssue

  // Some other issue!
  final case class OtherIssue(reason: String) extends ValidationIssue

  /**
    * Base trait for the results of validating incoming configs
    */
  trait Validation

  /**
    * Indicates a valid input config
    */
  case object Valid extends Validation

  /**
    * Indicates an invalid input config
    *
    */
  case class Invalid(issue: ValidationIssue) extends Validation

}
