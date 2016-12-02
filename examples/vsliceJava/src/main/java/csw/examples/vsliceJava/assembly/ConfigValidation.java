package csw.examples.vsliceJava.assembly;

import csw.util.config.Configurations.ConfigKey;
import csw.util.config.Configurations.SetupConfig;
import csw.util.config.Configurations.SetupConfigArg;
import csw.util.config.DoubleItem;
import csw.util.config.StringItem;
import javacsw.services.ccs.JValidation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static csw.examples.vsliceJava.assembly.AssemblyContext.*;
import static csw.examples.vsliceJava.assembly.AssemblyContext.configurationVersionKey;
import static csw.services.ccs.Validation.Invalid;
import static csw.services.ccs.Validation.Validation;
import static javacsw.services.ccs.JValidation.*;
import static javacsw.util.config.JItems.jitem;
import static javacsw.util.config.JItems.jvalue;

/**
 * TMT Source Code: 8/24/16.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class ConfigValidation {

  /**
   * Looks for any SetupConfigs in a SetupConfigArg that fail validation and returns as a list of only Invalid
   *
   * @param sca input SetupConfigArg for checking
   * @param ac  AssemblyContext provides command names
   * @return scala [[List]] that includes only the Invalid configurations in the SetupConfigArg
   */
  public static List<Invalid> invalidsInTromboneSetupConfigArg(SetupConfigArg sca, AssemblyContext ac) {
    // Returns a list of all failed validations in config arg
    List<Validation> list = validateTromboneSetupConfigArg(sca, ac);
    List<Invalid> badList = new ArrayList<>();
    for (Validation v : list) {
      if (v instanceof Invalid) badList.add((Invalid) v);
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
    for (SetupConfig config : sca.getConfigs()) {
      result.add(validateOneSetupConfig(config, ac));
    }
    return result;
  }

  /**
   * Validation for the init SetupConfig
   *
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
      Set<String> missing = sc.jMissingKeys(configurationNameKey, configurationVersionKey);

      if (!missing.isEmpty())
        return Invalid(MissingKeyIssue("The 2 parameter init SetupConfig requires keys: "
          + configurationNameKey + " and " + configurationVersionKey));

      try {
        StringItem i1 = jitem(sc, configurationNameKey);
        StringItem i2 = jitem(sc, configurationVersionKey);
      } catch (Exception ex) {
        return Invalid(JValidation.WrongItemTypeIssue("The init SetupConfig requires StringItems named: "
          + configurationVersionKey + " and " + configurationVersionKey));
      }
      return JValidation.Valid;
    }
    return Invalid(WrongNumberOfItemsIssue("The init configuration requires 0 or 2 items, but " + size + " were received"));
  }

  /**
   * Validation for the datum SetupConfig -- currently nothing to validate
   *
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation datumValidation(SetupConfig sc) {
    return JValidation.Valid;
  }

  /**
   * Validation for the stop SetupConfig -- currently nothing to validate
   *
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation stopValidation(SetupConfig sc) {
    return JValidation.Valid;
  }

  /**
   * Validation for the move SetupConfig
   * Note: position is optional, if not present, it moves to home
   *
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation moveValidation(SetupConfig sc, AssemblyContext ac) {
    if (!sc.configKey().equals(ac.moveCK)) {
      return Invalid(WrongConfigKeyIssue("The SetupConfig is not a move configuration."));
    }
    if (sc.size() == 0) return JValidation.Valid;

    // Check for correct key and type -- only checks that essential key is present, not strict
    if (!sc.exists(stagePositionKey)) {
      return Invalid(MissingKeyIssue("The move SetupConfig must have a DoubleItem named: " + stagePositionKey));
    }

    try {
      jitem(sc, stagePositionKey);
    } catch(Exception ex) {
      return Invalid(WrongItemTypeIssue("The move SetupConfig must have a DoubleItem named: " + stagePositionKey));
    }
    if (jitem(sc, stagePositionKey).units() != stagePositionUnits) {
      return Invalid(WrongUnitsIssue("The move SetupConfig parameter: "
        + stagePositionKey
        + " must have units of: "
        + stagePositionUnits));
    }
    return JValidation.Valid;
  }

  /**
   * Validation for the position SetupConfig -- must have a single parameter named rangeDistance
   *
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation positionValidation(SetupConfig sc, AssemblyContext ac) {
    if (!sc.configKey().equals(ac.positionCK)) {
      return Invalid(WrongConfigKeyIssue("The SetupConfig is not a position configuration."));
    }

    // The spec says parameter is not required, but doesn't explain so requiring parameter
    // Check for correct key and type -- only checks that essential key is present, not strict
    if (!sc.exists(naRangeDistanceKey)) {
      return Invalid(MissingKeyIssue("The position SetupConfig must have a DoubleItem named: " + naRangeDistanceKey));
    }

    try {
      jitem(sc, naRangeDistanceKey);
    } catch(Exception ex) {
      return Invalid(WrongItemTypeIssue("The position SetupConfig must have a DoubleItem named: " + naRangeDistanceKey));
    }

    if (jitem(sc, naRangeDistanceKey).units() != naRangeDistanceUnits) {
      return Invalid(WrongUnitsIssue("The position SetupConfig parameter: "
        + naRangeDistanceKey
        + " must have units of: "
        + naRangeDistanceUnits));
    }

    double el = jvalue(jitem(sc, naRangeDistanceKey));
    if (el < 0) {
      return Invalid(ItemValueOutOfRangeIssue("Range distance value of "
        + el
        + " for position must be greater than or equal 0 km."));
    }

    return JValidation.Valid;
  }

  /**
   * Validation for the setElevation SetupConfig
   *
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation setElevationValidation(SetupConfig sc, AssemblyContext ac) {
    if (!sc.configKey().equals(ac.setElevationCK)) {
      return Invalid(WrongConfigKeyIssue("The SetupConfig is not a setElevation configuration"));
    }

    // Check for correct key and type -- only checks that essential key is present, not strict
    if (!sc.jMissingKeys(naElevationKey).isEmpty()) {
      return Invalid(MissingKeyIssue("The setElevation SetupConfig must have a parameter named: " + naElevationKey));
    }

    try {
      jitem(sc, naElevationKey);
    } catch(Exception ex) {
      return Invalid(WrongItemTypeIssue("The setElevation SetupConfig must have a parameter: " + naElevationKey + " as a DoubleItem"));
    }

    if (jitem(sc, naElevationKey).units() != naRangeDistanceUnits) {
      return Invalid(WrongUnitsIssue("The move SetupConfig parameter: "
        + naElevationKey
        + " must have units: "
        + naElevationUnits));
    }

    return Valid;
  }

  /**
   * Validation for the setAngle SetupConfig
   *
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation setAngleValidation(SetupConfig sc, AssemblyContext ac) {
    if (!sc.configKey().equals(ac.setAngleCK)) {
      return Invalid(WrongConfigKeyIssue("The SetupConfig is not a setAngle configuration"));
    }

    // Check for correct key and type -- only checks that essential key is present, not strict
    if (!sc.exists(zenithAngleKey)) {
      return Invalid(MissingKeyIssue("The setAngle SetupConfig must have a DoubleItem named: " + zenithAngleKey));
    }

    try {
      jitem(sc, zenithAngleKey);
    } catch(Exception ex) {
      return Invalid(WrongItemTypeIssue("The setAngle SetupConfig must have a DoubleItem named: " + zenithAngleKey));
    }

    DoubleItem di = jitem(sc, zenithAngleKey);
    if (di.units() != zenithAngleUnits) {
      return Invalid(WrongUnitsIssue("The setAngle SetupConfig parameter: "
        + zenithAngleKey
        + " must have units: "
        + zenithAngleUnits));
    }

    return Valid;
  }

  /**
   * Validation for the follow SetupConfig
   *
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  public static Validation followValidation(SetupConfig sc, AssemblyContext ac) {
    if (!sc.configKey().equals(ac.followCK)) {
      return Invalid(WrongConfigKeyIssue("The SetupConfig is not a follow configuration"));
    }
    // Check for correct key and type -- only checks that essential key is present, not strict
    if (!sc.exists(nssInUseKey)) {
      return Invalid(MissingKeyIssue("The follow SetupConfig must have a BooleanItem named: " + nssInUseKey));
    }

    try {
      jitem(sc, nssInUseKey);
    } catch(Exception ex) {
      return Invalid(WrongItemTypeIssue("The follow SetupConfig must have a BooleanItem named " + nssInUseKey));
    }

    return Valid;
  }
}
