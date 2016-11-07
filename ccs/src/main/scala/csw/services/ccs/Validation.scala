package csw.services.ccs
import scala.collection.JavaConverters._

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
  final case class WrongNumberOfItemsIssue(reason: String) extends ValidationIssue

  // Returned when an Assembly receives a ConfigArg with multiple configs, but it can only execute one at a time
  final case class SingleConfigOnlyIssue(reason: String) extends ValidationIssue

  // Returned when an Assembly receives a ConfigArg and one is already executing
  final case class AssemblyBusyIssue(reason: String) extends ValidationIssue

  // Returned when some required location is not available
  final case class UnresolvedLocationsIssue(reason: String) extends ValidationIssue

  // Parameter of a configuration is out of range
  final case class ItemValueOutOfRangeIssue(reason: String) extends ValidationIssue

  // The component is in the wrong internal state to handle a configuratio
  final case class WrongInternalStateIssue(reason: String) extends ValidationIssue

  // A command is unsupported in the current state
  final case class UnsupportedCommandInStateIssue(reason: String) extends ValidationIssue

  // A command is unsupported by component
  final case class UnsupportedCommandIssue(reason: String) extends ValidationIssue

  // A required service is not available
  final case class RequiredServiceUnavailableIssue(reason: String) extends ValidationIssue

  // A required HCD is not available
  final case class RequiredHCDUnavailableIssue(reason: String) extends ValidationIssue

  // A required Assembly is not available
  final case class RequiredAssemblyUnavailableIssue(reason: String) extends ValidationIssue

  final case class RequiredSequencerUnavailableIssue(reason: String) extends ValidationIssue

  // Some other issue!
  final case class OtherIssue(reason: String) extends ValidationIssue

  /**
   * A type created for a list of Validation results
   */
  type ValidationList = List[Validation]

  /**
   * Test that all validations in a list of Validation are Valid
   * @param validations a ValidationList from validation of a list of configurations
   * @return True if all validations are Valid, else false
   */
  def isAllValid(validations: ValidationList): Boolean = !validations.exists(_ != Validation.Valid)

  /**
   * Test that all validations in a list of Validation are Valid
   * @param validations a ValidationList from validation of a list of configurations
   * @return True if all validations are Valid, else false
   */
  def isAllValid(validations: java.util.List[Validation]): Boolean = !validations.asScala.exists(_ != Validation.Valid)

  /**
   * Base trait for the results of validating incoming configs
   * Only a subset of CommandStatus2 entries are also Validation (Valid, Invalid)
   */
  sealed trait Validation

  /**
   * The configuration or set of configurations was not valid before starting
   * @param issue
   */
  final case class Invalid(issue: ValidationIssue) extends Validation

  /**
   * The configuration or set of configurations was valid and started
   */
  final case object Valid extends Validation
}
