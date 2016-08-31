package csw.examples.vslice.assembly

import csw.services.ccs.Validation.{Valid, Validation}
import csw.util.config.Configurations.SetupConfig
import csw.util.config.{BooleanItem, DoubleItem, StringItem}

/**
  * TMT Source Code: 8/24/16.
  */
object ConfigValidation {
  import TromboneAssembly._
  import csw.services.ccs.Validation._

  /**
    * Validation for the init SetupConfig
    * @param sc the received SetupConfig
    * @return Valid or Invalid
    */
  def initValidation(sc: SetupConfig):Validation = {
    val size = sc.size
    if (sc.configKey != initCK) Invalid(WrongConfigKeyIssue("The SetupConfig is not an init configuration"))
    else
      // If no arguments, then this is okay
    if (size == 0)
      Valid
    else if (size == 2) {
      // Check for correct keys and types
      // This example assumes that we want only these two keys
      val missing = sc.missingKeys(configurationNameKey, configurationVersionKey)
      if (missing.nonEmpty)
        Invalid(MissingKeyIssue(s"The 2 parameter init SetupConfig requires keys: $configurationNameKey and $configurationVersionKey"))
      else if (!sc(configurationNameKey).isInstanceOf[StringItem] || !sc(configurationVersionKey).isInstanceOf[StringItem])
        Invalid(WrongItemTypeIssue(s"The init SetupConfig requires StringItems named: $configurationVersionKey and $configurationVersionKey"))
      else Valid
    }
    else Invalid(WrongNumberOfParametersIssue(s"The init configuration requires 0 or 2 parameters, but $size were received"))
  }

  /**
    * Validation for the datum SetupConfig -- currently nothing to validate
    * @param sc the received SetupConfig
    * @return Valid or Invalid
    */
  def datumValidation(sc: SetupConfig):Validation = Valid

  /**
    * Validation for the stop SetupConfig -- currently nothing to validate
    * @param sc the received SetupConfig
    * @return Valid or Invalid
    */
  def stopValidation(sc: SetupConfig):Validation = Valid

  /**
    * Validation for the move SetupConfig
    * Note: position is optional, if not present, it moves to home
    * @param sc the received SetupConfig
    * @return Valid or Invalid
    */
  def moveValidation(sc: SetupConfig):Validation = {
    if (sc.configKey != moveCK) {
      Invalid(WrongConfigKeyIssue("The SetupConfig is not a move configuration."))
    } else if (sc.size == 0)
      Valid
    else {
      // Check for correct key and type -- only checks that essential key is present, not strict
      if (!sc.exists(stagePositionKey)) {
        Invalid(MissingKeyIssue(s"The move SetupConfig must have a DoubleItem named: $stagePositionKey"))
      } else
      if (!sc(stagePositionKey).isInstanceOf[DoubleItem]) {
        Invalid(WrongItemTypeIssue(s"The move SetupConfig must have a DoubleItem named: $stagePositionKey"))
      } else
      if (sc(stagePositionKey).units != stagePositionUnits) {
        Invalid(WrongUnitsIssue(s"The move SetupConfig parameter: $stagePositionKey must have units of: $stagePositionUnits"))
      } else Valid
    }
  }

  /**
    * Validation for the position SetupConfig -- must have a single parameter named rangeDistance
    * @param sc the received SetupConfig
    * @return Valid or Invalid
    */
  def positionValidation(sc: SetupConfig):Validation = {
    if (sc.configKey != positionCK) {
      Invalid(WrongConfigKeyIssue("The SetupConfig is not a position configuration."))
    }
    else {
      // The spec says parameter is not required, but doesn't explain so requiring parameter
      // Check for correct key and type -- only checks that essential key is present, not strict
      if (!sc.exists(naLayerRangeDistanceKey)) {
        Invalid(MissingKeyIssue(s"The position SetupConfig must have a DoubleItem named: $naLayerRangeDistanceKey"))
      } else
      if (!sc(naLayerRangeDistanceKey).isInstanceOf[DoubleItem]) {
        Invalid(WrongItemTypeIssue(s"The position SetupConfig must have a DoubleItem named: $naLayerRangeDistanceKey"))
      } else
      if (sc(naLayerRangeDistanceKey).units != naLayerRangeDistanceUnits) {
        Invalid(WrongUnitsIssue(s"The position SetupConfig parameter: $naLayerRangeDistanceKey must have units of: $naLayerRangeDistanceUnits"))
      } else Valid
    }
  }

  /**
    * Validation for the setElevation SetupConfig
    * @param sc the received SetupConfig
    * @return Valid or Invalid
    */
  def setElevationValidation(sc: SetupConfig):Validation = {
    if (sc.configKey != setElevationCK) {
      Invalid(WrongConfigKeyIssue("The SetupConfig is not a setElevation configuration"))
    } else
    // Check for correct key and type -- only checks that essential key is present, not strict
    if (sc.missingKeys(zenithAngleKey, naLayerElevationKey).nonEmpty) {
      Invalid(MissingKeyIssue(s"The setElevation SetupConfig must have parameters named: $zenithAngleKey and $naLayerElevationKey"))
    } else
    if (!sc(zenithAngleKey).isInstanceOf[DoubleItem] || !sc(naLayerElevationKey).isInstanceOf[DoubleItem]) {
      Invalid(WrongItemTypeIssue(s"The setElevation SetupConfig must parameters: $zenithAngleKey and $naLayerElevationKey must be DoubleItems"))
    } else
    if (sc(zenithAngleKey).units != zenithAngleUnits) {
      Invalid(WrongUnitsIssue(s"The move SetupConfig parameter: $zenithAngleKey must have units: $zenithAngleUnits"))
    } else
    if (sc(naLayerElevationKey).units != naLayerRangeDistanceUnits) {
      Invalid(WrongUnitsIssue(s"The move SetupConfig parameter: $naLayerElevationKey must have units: $naLayerElevationUnits"))
    } else Valid
  }

  /**
    * Validation for the setAngle SetupConfig
    * @param sc the received SetupConfig
    * @return Valid or Invalid
    */
  def setAngleValidation(sc: SetupConfig):Validation = {
    if (sc.configKey != setAngleCK) {
      Invalid(WrongConfigKeyIssue("The SetupConfig is not a setAngle configuration"))
    } else
    // Check for correct key and type -- only checks that essential key is present, not strict
    if (!sc.exists(zenithAngleKey)) {
      Invalid(MissingKeyIssue(s"The setAngle SetupConfig must have a DoubleItem named: $zenithAngleKey"))
    } else
    if (!sc(zenithAngleKey).isInstanceOf[DoubleItem]) {
      Invalid(WrongItemTypeIssue(s"The setAngle SetupConfig must have a DoubleItem named: $zenithAngleKey"))
    } else
    if (sc(zenithAngleKey).units != zenithAngleUnits) {
      Invalid(WrongUnitsIssue(s"The setAngle SetupConfig parameter: $zenithAngleKey must have units: $zenithAngleUnits"))
    } else Valid
  }

  /**
    * Validation for the follow SetupConfig
    * @param sc the received SetupConfig
    * @return Valid or Invalid
    */
  def followValidation(sc: SetupConfig):Validation = {
    if (sc.configKey != followCK) {
      Invalid(WrongConfigKeyIssue("The SetupConfig is not a follow configuration"))
    } else
    // Check for correct key and type -- only checks that essential key is present, not strict
    if (!sc.exists(nssInUseKey)) {
      Invalid(MissingKeyIssue(s"The follow SetupConfig must have a BooleanItem named: $nssInUseKey"))
    } else
    if (!sc(nssInUseKey).isInstanceOf[BooleanItem]) {
      Invalid(WrongItemTypeIssue(s"The follow SetupConfig must have a BooleanItem named $nssInUseKey"))
    } else Valid
  }

}
