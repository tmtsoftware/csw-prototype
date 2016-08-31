package csw.services.ccs

/**
  * TMT Source Code: 8/25/16.
  */
object Validation {


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
