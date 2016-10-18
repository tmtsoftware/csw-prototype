package csw.examples.vsliceJava.assembly;

import csw.services.ccs.Validation.Invalid;
import csw.services.ccs.Validation.Validation;
import csw.util.config.*;
import csw.util.config.Configurations.*;

import static javacsw.services.ccs.JValidation.*;
import static javacsw.util.config.JItems.jvalue;

import javacsw.services.ccs.JValidation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * TMT Source Code: 8/24/16.
 */
@SuppressWarnings("unused")
public class ConfigValidation {

  /**
   * Looks for any SetupConfigs in a SetupConfigArg that fail validation and returns as a list of only Invalid
   * @param sca input SetupConfigArg for checking
   * @param ac AssemblyContext provides command names
   * @return scala [[List]] that includes only the Invalid configurations in the SetupConfigArg
   */
  public static List<Invalid> invalidsInTromboneSetupConfigArg(SetupConfigArg sca, AssemblyContext ac) {
    // Returns a list of all failed validations in config arg
    List<Validation> list = validateTromboneSetupConfigArg(sca, ac);
    List<Invalid> badList = new ArrayList<>();
    for(Validation v : list) {
      if (v instanceof Invalid) badList.add((Invalid)v);
    }
    return badList;
  }

  /**
   * Runs Trombone-specific validation on a single SetupConfig.
   */
  public static Validation validateOneSetupConfig(SetupConfig sc, AssemblyContext ac) {
    ConfigKey configKey = sc.configKey();
    if (configKey.equals(ac.initCK)) return initValidation(sc, ac);
    if (configKey.equals(ac.datumCK)) return datumValidation(sc);
    if (configKey.equals(ac.stopCK)) return stopValidation(sc);
    if (configKey.equals(ac.moveCK)) return moveValidation(sc, ac);
    if (configKey.equals(ac.positionCK)) return positionValidation(sc, ac);
    if (configKey.equals(ac.setElevationCK)) return setElevationValidation(sc, ac);
    if (configKey.equals(ac.setAngleCK)) return setAngleValidation(sc, ac);
    if (configKey.equals(ac.followCK)) return followValidation(sc, ac);
    return Invalid(OtherIssue("SetupConfig with prefix $x is not support for $componentName"));
  }

  // Validates a SetupConfigArg for Trombone Assembly
  public static List<Validation> validateTromboneSetupConfigArg(SetupConfigArg sca, AssemblyContext ac) {
    List<Validation> result = new ArrayList<>();
    for(SetupConfig config : sca.jconfigs()) {
      result.add(validateOneSetupConfig(config, ac));
    }
    return result;
  }

  /**
   * Validation for the init SetupConfig
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation initValidation(SetupConfig sc, AssemblyContext ac) {
    int size = sc.size();
    if (!sc.configKey().equals(ac.initCK))
      return Invalid(WrongConfigKeyIssue("The SetupConfig is not an init configuration"));

    // If no arguments, then this is okay
    if (sc.size() == 0)
      return JValidation.Valid;

    if (size == 2) {
      // Check for correct keys and types
      // This example assumes that we want only these two keys
      Set<String> missing = sc.jMissingKeys(ac.configurationNameKey, ac.configurationVersionKey);
      if (!missing.isEmpty())
        return Invalid(MissingKeyIssue("The 2 parameter init SetupConfig requires keys: "
          + ac.configurationNameKey + " and " + ac.configurationVersionKey));
//      if (!(JavaHelpers.jvalue(sc, ac.configurationNameKey, 0) instanceof StringItem)
//        || !(JavaHelpers.jvalue(sc, ac.configurationVersionKey, 0) instanceof StringItem))
//        return Invalid(WrongItemTypeIssue("The init SetupConfig requires StringItems named: "
//          + ac.configurationVersionKey + " and " + ac.configurationVersionKey));
      return JValidation.Valid;
    }
    return Invalid(WrongNumberOfItemsIssue("The init configuration requires 0 or 2 items, but " + size + " were received"));
  }

  /**
   * Validation for the datum SetupConfig -- currently nothing to validate
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation datumValidation(SetupConfig sc) { return JValidation.Valid; }

  /**
   * Validation for the stop SetupConfig -- currently nothing to validate
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation stopValidation(SetupConfig sc) { return JValidation.Valid; }

  /**
   * Validation for the move SetupConfig
   * Note: position is optional, if not present, it moves to home
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation moveValidation(SetupConfig sc, AssemblyContext ac) {
    if (!sc.configKey().equals(ac.moveCK)) {
      return Invalid(WrongConfigKeyIssue("The SetupConfig is not a move configuration."));
    }
    if (sc.size() == 0) return JValidation.Valid;

    // Check for correct key and type -- only checks that essential key is present, not strict
    if (!sc.exists(ac.stagePositionKey)) {
      return Invalid(MissingKeyIssue("The move SetupConfig must have a DoubleItem named: " + ac.stagePositionKey));
    }

    DoubleItem di = JavaHelpers.jvalue(sc, ac.stagePositionKey, 0);
//    if (!(di instanceof DoubleItem)) {
//      return Invalid(WrongItemTypeIssue("The move SetupConfig must have a DoubleItem named: " + ac.stagePositionKey));
//    }
    if (di.units() != ac.stagePositionUnits) {
      return Invalid(WrongUnitsIssue("The move SetupConfig parameter: "
        + ac.stagePositionKey
        + " must have units of: "
        + ac.stagePositionUnits));
    }
    return JValidation.Valid;
  }

  /**
   * Validation for the position SetupConfig -- must have a single parameter named rangeDistance
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation positionValidation(SetupConfig sc, AssemblyContext ac) {
    if (!sc.configKey().equals(ac.positionCK)) {
      return Invalid(WrongConfigKeyIssue("The SetupConfig is not a position configuration."));
    }
      // The spec says parameter is not required, but doesn't explain so requiring parameter
      // Check for correct key and type -- only checks that essential key is present, not strict
      if (!sc.exists(ac.naRangeDistanceKey)) {
        return Invalid(MissingKeyIssue("The position SetupConfig must have a DoubleItem named: " + ac.naRangeDistanceKey));
      }
      DoubleItem di = JavaHelpers.jvalue(sc, ac.naRangeDistanceKey, 0);
//      if (!(di instanceof DoubleItem)) {
//        return Invalid(WrongItemTypeIssue("The position SetupConfig must have a DoubleItem named: " + ac.naRangeDistanceKey));
//      }
      if (di.units() != ac.naRangeDistanceUnits) {
        return Invalid(WrongUnitsIssue("The position SetupConfig parameter: "
          + ac.naRangeDistanceKey
          + " must have units of: "
          + ac.naRangeDistanceUnits));
      }

        double el = jvalue(di);
        if (el < 0) {
          return Invalid(ItemValueOutOfRangeIssue("Range distance value of "
            + el
          + " for position must be greater than or equal 0 km."));
        }
        return JValidation.Valid;
  }

  /**
   * Validation for the setElevation SetupConfig
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation setElevationValidation(SetupConfig sc, AssemblyContext ac) {
    if (!sc.configKey().equals(ac.setElevationCK)) {
      return Invalid(WrongConfigKeyIssue("The SetupConfig is not a setElevation configuration"));
    }
    // Check for correct key and type -- only checks that essential key is present, not strict
    if (!sc.jMissingKeys(ac.naElevationKey).isEmpty()) {
      return Invalid(MissingKeyIssue("The setElevation SetupConfig must have a parameter named: " + ac.naElevationKey));
    }
//    if (!sc(ac.naElevationKey).isInstanceOf[DoubleItem]) {
//      Invalid(WrongItemTypeIssue(s"The setElevation SetupConfig must have a parameter: ${ac.naElevationKey} as a DoubleItem"))
//    }
    DoubleItem di = JavaHelpers.jvalue(sc, ac.naElevationKey, 0);
    if (di.units() != ac.naRangeDistanceUnits) {
      return Invalid(WrongUnitsIssue("The move SetupConfig parameter: "
        + ac.naElevationKey
        + " must have units: "
        + ac.naElevationUnits));
    }
    return Valid;
  }

  /**
   * Validation for the setAngle SetupConfig
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation setAngleValidation(SetupConfig sc, AssemblyContext ac) {
    if (!sc.configKey().equals(ac.setAngleCK)) {
      return Invalid(WrongConfigKeyIssue("The SetupConfig is not a setAngle configuration"));
    }
    // Check for correct key and type -- only checks that essential key is present, not strict
    if (!sc.exists(ac.zenithAngleKey)) {
      return Invalid(MissingKeyIssue("The setAngle SetupConfig must have a DoubleItem named: " + ac.zenithAngleKey));
    }
//    if (!sc(ac.zenithAngleKey).isInstanceOf[DoubleItem]) {
//      Invalid(WrongItemTypeIssue(s"The setAngle SetupConfig must have a DoubleItem named: ${ac.zenithAngleKey}"))
//    }
    DoubleItem di = JavaHelpers.jvalue(sc, ac.zenithAngleKey, 0);
    if (di.units() != ac.zenithAngleUnits) {
      return Invalid(WrongUnitsIssue("The setAngle SetupConfig parameter: "
        + ac.zenithAngleKey
        + " must have units: "
        + ac.zenithAngleUnits));
    }
    return Valid;
  }

  /**
   * Validation for the follow SetupConfig
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation followValidation(SetupConfig sc, AssemblyContext ac) {
    if (!sc.configKey().equals(ac.followCK)) {
      return Invalid(WrongConfigKeyIssue("The SetupConfig is not a follow configuration"));
    }
    // Check for correct key and type -- only checks that essential key is present, not strict
    if (!sc.exists(ac.nssInUseKey)) {
      return Invalid(MissingKeyIssue("The follow SetupConfig must have a BooleanItem named: " + ac.nssInUseKey));
    }
//    if (!sc(ac.nssInUseKey).isInstanceOf[BooleanItem]) {
//      Invalid(WrongItemTypeIssue(s"The follow SetupConfig must have a BooleanItem named ${ac.nssInUseKey}"))
//    }
    return Valid;
  }

}
