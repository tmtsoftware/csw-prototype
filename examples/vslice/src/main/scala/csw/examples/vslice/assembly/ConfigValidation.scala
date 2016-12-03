package csw.examples.vslice.assembly

import csw.services.ccs.Validation._
import csw.util.config.Configurations.{SetupConfig, SetupConfigArg}

import scala.util.Try

/**
 * TMT Source Code: 8/24/16.
 */
object ConfigValidation {

  /**
   * Looks for any SetupConfigs in a SetupConfigArg that fail validation and returns as a list of only Invalid
   * @param sca input SetupConfigArg for checking
   * @param ac AssemblyContext provides command names
   * @return scala [[List]] that includes only the Invalid configurations in the SetupConfigArg
   */
  def invalidsInTromboneSetupConfigArg(sca: SetupConfigArg)(implicit ac: AssemblyContext): List[Invalid] =
    // Returns a list of all failed validations in config arg
    validateTromboneSetupConfigArg(sca).collect { case a: Invalid => a }

  /**
   * Runs Trombone-specific validation on a single SetupConfig.
   * @return
   */
  def validateOneSetupConfig(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {
    sc.configKey match {
      case ac.initCK         => initValidation(sc)
      case ac.datumCK        => datumValidation(sc)
      case ac.stopCK         => stopValidation(sc)
      case ac.moveCK         => moveValidation(sc)
      case ac.positionCK     => positionValidation(sc)
      case ac.setElevationCK => setElevationValidation(sc)
      case ac.setAngleCK     => setAngleValidation(sc)
      case ac.followCK       => followValidation(sc)
      case x                 => Invalid(OtherIssue(s"SetupConfig with prefix $x is not supported"))
    }
  }

  // Validates a SetupConfigArg for Trombone Assembly
  def validateTromboneSetupConfigArg(sca: SetupConfigArg)(implicit ac: AssemblyContext): ValidationList =
    sca.configs.map(config => validateOneSetupConfig(config)).toList

  /**
   * Validation for the init SetupConfig
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  def initValidation(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {

    val size = sc.size
    if (sc.configKey != ac.initCK) Invalid(WrongConfigKeyIssue("The SetupConfig is not an init configuration"))
    else // If no arguments, then this is okay
    if (sc.size == 0)
      Valid
    else if (size == 2) {
      import ac._
      // Check for correct keys and types
      // This example assumes that we want only these two keys
      val missing = sc.missingKeys(configurationNameKey, configurationVersionKey)
      if (missing.nonEmpty)
        Invalid(MissingKeyIssue(s"The 2 parameter init SetupConfig requires keys: $configurationNameKey and $configurationVersionKey"))
      else if (Try(sc(configurationNameKey)).isFailure || Try(sc(configurationVersionKey)).isFailure)
        Invalid(WrongItemTypeIssue(s"The init SetupConfig requires StringItems named: $configurationVersionKey and $configurationVersionKey"))
      else Valid
    } else Invalid(WrongNumberOfItemsIssue(s"The init configuration requires 0 or 2 items, but $size were received"))
  }

  /**
   * Validation for the datum SetupConfig -- currently nothing to validate
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  def datumValidation(sc: SetupConfig): Validation = Valid

  /**
   * Validation for the stop SetupConfig -- currently nothing to validate
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  def stopValidation(sc: SetupConfig): Validation = Valid

  /**
   * Validation for the move SetupConfig
   * Note: position is optional, if not present, it moves to home
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  def moveValidation(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {
    if (sc.configKey != ac.moveCK) {
      Invalid(WrongConfigKeyIssue("The SetupConfig is not a move configuration."))
    } else if (sc.size == 0)
      Valid
    else {
      // Check for correct key and type -- only checks that essential key is present, not strict
      if (!sc.exists(ac.stagePositionKey)) {
        Invalid(MissingKeyIssue(s"The move SetupConfig must have a DoubleItem named: ${ac.stagePositionKey}"))
      } else if (Try(sc(ac.stagePositionKey)).isFailure)
        Invalid(WrongItemTypeIssue(s"The move SetupConfig must have a DoubleItem named: ${ac.stagePositionKey}"))
      else if (sc(ac.stagePositionKey).units != ac.stagePositionUnits) {
        Invalid(WrongUnitsIssue(s"The move SetupConfig parameter: ${ac.stagePositionKey} must have units of: ${ac.stagePositionUnits}"))
      } else Valid
    }
  }

  /**
   * Validation for the position SetupConfig -- must have a single parameter named rangeDistance
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  def positionValidation(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {
    if (sc.configKey != ac.positionCK) {
      Invalid(WrongConfigKeyIssue("The SetupConfig is not a position configuration."))
    } else {
      // The spec says parameter is not required, but doesn't explain so requiring parameter
      // Check for correct key and type -- only checks that essential key is present, not strict
      if (!sc.exists(ac.naRangeDistanceKey)) {
        Invalid(MissingKeyIssue(s"The position SetupConfig must have a DoubleItem named: ${ac.naRangeDistanceKey}"))
      } else if (Try(sc(ac.naRangeDistanceKey)).isFailure) {
        Invalid(WrongItemTypeIssue(s"The position SetupConfig must have a DoubleItem named: ${ac.naRangeDistanceKey}"))
      } else if (sc(ac.naRangeDistanceKey).units != ac.naRangeDistanceUnits) {
        Invalid(WrongUnitsIssue(s"The position SetupConfig parameter: ${ac.naRangeDistanceKey} must have units of: ${ac.naRangeDistanceUnits}"))
      } else {
        val el = sc(ac.naRangeDistanceKey).head
        if (el < 0) {
          Invalid(ItemValueOutOfRangeIssue(s"Range distance value of $el for position must be greater than or equal 0 km."))
        } else Valid
      }
    }
  }

  /**
   * Validation for the setElevation SetupConfig
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  def setElevationValidation(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {
    if (sc.configKey != ac.setElevationCK) {
      Invalid(WrongConfigKeyIssue("The SetupConfig is not a setElevation configuration"))
    } else if (sc.missingKeys(ac.naElevationKey).nonEmpty) {
      // Check for correct key and type -- only checks that essential key is present, not strict
      Invalid(MissingKeyIssue(s"The setElevation SetupConfig must have a parameter named: ${ac.naElevationKey}"))
    } else if (Try(sc(ac.naElevationKey)).isFailure) {
      Invalid(WrongItemTypeIssue(s"The setElevation SetupConfig must have a parameter: ${ac.naElevationKey} as a DoubleItem"))
    } else if (sc(ac.naElevationKey).units != ac.naRangeDistanceUnits) {
      Invalid(WrongUnitsIssue(s"The move SetupConfig parameter: ${ac.naElevationKey} must have units: ${ac.naElevationUnits}"))
    } else Valid
  }

  /**
   * Validation for the setAngle SetupConfig
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  def setAngleValidation(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {
    if (sc.configKey != ac.setAngleCK) {
      Invalid(WrongConfigKeyIssue("The SetupConfig is not a setAngle configuration"))
    } else // Check for correct key and type -- only checks that essential key is present, not strict
    if (!sc.exists(ac.zenithAngleKey)) {
      Invalid(MissingKeyIssue(s"The setAngle SetupConfig must have a DoubleItem named: ${ac.zenithAngleKey}"))
    } else if (Try(sc(ac.zenithAngleKey)).isFailure) {
      Invalid(WrongItemTypeIssue(s"The setAngle SetupConfig must have a DoubleItem named: ${ac.zenithAngleKey}"))
    } else if (sc(ac.zenithAngleKey).units != ac.zenithAngleUnits) {
      Invalid(WrongUnitsIssue(s"The setAngle SetupConfig parameter: ${ac.zenithAngleKey} must have units: ${ac.zenithAngleUnits}"))
    } else Valid
  }

  /**
   * Validation for the follow SetupConfig
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  def followValidation(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {
    if (sc.configKey != ac.followCK) {
      Invalid(WrongConfigKeyIssue("The SetupConfig is not a follow configuration"))
    } else if (!sc.exists(ac.nssInUseKey)) {
      // Check for correct key and type -- only checks that essential key is present, not strict
      Invalid(MissingKeyIssue(s"The follow SetupConfig must have a BooleanItem named: ${ac.nssInUseKey}"))
    } else if (Try(sc(ac.nssInUseKey)).isFailure) {
      Invalid(WrongItemTypeIssue(s"The follow SetupConfig must have a BooleanItem named ${ac.nssInUseKey}"))
    } else Valid
  }
}
