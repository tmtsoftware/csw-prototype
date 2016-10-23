//package csw.examples.vsliceJava.assembly;
//
//import com.typesafe.config.Config;
//import csw.services.ccs.Validation.*;
//import csw.services.loc.ComponentType;
//import csw.util.config.*;
//import csw.util.config.Configurations.*;
//
//import static javacsw.util.config.JItems.*;
//import static javacsw.util.config.JConfigDSL.*;
//import static javacsw.util.config.JUnitsOfMeasure.*;
//
//import csw.services.pkg.Component.AssemblyInfo;
//import javacsw.services.ccs.JValidation;
//import javacsw.services.ccs.JValidation.*;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Set;
//
///**
// * TMT Source Code: 8/24/16.
// */
//public class ConfigValidation {
//
//  /**
//   * Looks for any SetupConfigs in a SetupConfigArg that fail validation and returns as a list of only Invalid
//   * @param sca input SetupConfigArg for checking
//   * @param ac AssemblyContext provides command names
//   * @return scala [[List]] that includes only the Invalid configurations in the SetupConfigArg
//   */
//  public static List<Invalid> invalidsInTromboneSetupConfigArg(SetupConfigArg sca, AssemblyContext ac) {
//    // Returns a list of all failed validations in config arg
//    validateTromboneSetupConfigArg(sca).collect {
//    case a:
//      Invalid =>a
//  }
//  }
//
//  /**
//   * Runs Trombone-specific validation on a single SetupConfig.
//   */
//  public static Validation validateOneSetupConfig(SetupConfig sc, AssemblyContext ac) {
//    ConfigKey configKey = sc.configKey();
//    if (configKey.equals(ac.initCK)) return initValidation(sc, ac);
//    if (configKey.equals(ac.datumCK)) return datumValidation(sc);
////      case ac.datumCK        => datumValidation(sc)
////      case ac.stopCK         => stopValidation(sc)
////      case ac.moveCK         => moveValidation(sc)
////      case ac.positionCK     => positionValidation(sc)
////      case ac.setElevationCK => setElevationValidation(sc)
////      case ac.setAngleCK     => setAngleValidation(sc)
////      case ac.followCK       => followValidation(sc)
////      case x                 => Invalid(OtherIssue("SetupConfig with prefix $x is not support for $componentName"))
//  }
//
//  // Validates a SetupConfigArg for Trombone Assembly
//  public static List<Validation> validateTromboneSetupConfigArg(SetupConfigArg sca, AssemblyContext ac) {
//    List<Validation> result = new ArrayList<>();
//    for(SetupConfig config : sca.jconfigs()) {
//      result.add(validateOneSetupConfig(config, ac));
//    }
//    return result;
//  }
//
//  /**
//   * Validation for the init SetupConfig
//   * @param sc the received SetupConfig
//   * @return Valid or Invalid
//   */
//  public static Validation initValidation(SetupConfig sc, AssemblyContext ac) {
//    int size = sc.size();
//    if (!sc.configKey().equals(ac.initCK))
//      return new Invalid(new WrongConfigKeyIssue("The SetupConfig is not an init configuration"));
//
//    // If no arguments, then this is okay
//    if (sc.size() == 0)
//      return JValidation.Valid;
//
//    if (size == 2) {
//      // Check for correct keys and types
//      // This example assumes that we want only these two keys
//      Set<String> missing = sc.jMissingKeys(ac.configurationNameKey, ac.configurationVersionKey);
//      if (!missing.isEmpty())
//        return new Invalid(new MissingKeyIssue("The 2 parameter init SetupConfig requires keys: "
//          + ac.configurationNameKey + " and " + ac.configurationVersionKey));
//      if (!(JavaHelpers.jvalue(sc, ac.configurationNameKey, 0) instanceof StringItem)
//        || !(JavaHelpers.jvalue(sc, ac.configurationVersionKey, 0) instanceof StringItem))
//        return new Invalid(new WrongItemTypeIssue("The init SetupConfig requires StringItems named: "
//          + ac.configurationVersionKey + " and " + ac.configurationVersionKey));
//      return JValidation.Valid;
//    } else return new Invalid(new WrongNumberOfItemsIssue("The init configuration requires 0 or 2 items, but " + size + " were received"));
//  }
//
//  /**
//   * Validation for the datum SetupConfig -- currently nothing to validate
//   * @param sc the received SetupConfig
//   * @return Valid or Invalid
//   */
//  public static Validation datumValidation(SetupConfig sc) { return JValidation.Valid; }
//
//  /**
//   * Validation for the stop SetupConfig -- currently nothing to validate
//   * @param sc the received SetupConfig
//   * @return Valid or Invalid
//   */
//  public static XXX stopValidation(sc: SetupConfig): Validation = Valid
//
//  /**
//   * Validation for the move SetupConfig
//   * Note: position is optional, if not present, it moves to home
//   * @param sc the received SetupConfig
//   * @return Valid or Invalid
//   */
//  public static XXX moveValidation(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {
//    if (sc.configKey != ac.moveCK) {
//      Invalid(WrongConfigKeyIssue("The SetupConfig is not a move configuration."))
//    } else if (sc.size == 0)
//      Valid
//    else {
//      // Check for correct key and type -- only checks that essential key is present, not strict
//      if (!sc.exists(ac.stagePositionKey)) {
//        Invalid(MissingKeyIssue(s"The move SetupConfig must have a DoubleItem named: ${ac.stagePositionKey}"))
//      } else if (!sc(ac.stagePositionKey).isInstanceOf[DoubleItem]) {
//        Invalid(WrongItemTypeIssue(s"The move SetupConfig must have a DoubleItem named: ${ac.stagePositionKey}"))
//      } else if (sc(ac.stagePositionKey).units != ac.stagePositionUnits) {
//        Invalid(WrongUnitsIssue(s"The move SetupConfig parameter: ${ac.stagePositionKey} must have units of: ${ac.stagePositionUnits}"))
//      } else Valid
//    }
//  }
//
//  /**
//   * Validation for the position SetupConfig -- must have a single parameter named rangeDistance
//   * @param sc the received SetupConfig
//   * @return Valid or Invalid
//   */
//  public static XXX positionValidation(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {
//    if (sc.configKey != ac.positionCK) {
//      Invalid(WrongConfigKeyIssue("The SetupConfig is not a position configuration."))
//    } else {
//      // The spec says parameter is not required, but doesn't explain so requiring parameter
//      // Check for correct key and type -- only checks that essential key is present, not strict
//      if (!sc.exists(ac.naRangeDistanceKey)) {
//        Invalid(MissingKeyIssue(s"The position SetupConfig must have a DoubleItem named: ${ac.naRangeDistanceKey}"))
//      } else if (!sc(ac.naRangeDistanceKey).isInstanceOf[DoubleItem]) {
//        Invalid(WrongItemTypeIssue(s"The position SetupConfig must have a DoubleItem named: ${ac.naRangeDistanceKey}"))
//      } else if (sc(ac.naRangeDistanceKey).units != ac.naRangeDistanceUnits) {
//        Invalid(WrongUnitsIssue(s"The position SetupConfig parameter: ${ac.naRangeDistanceKey} must have units of: ${ac.naRangeDistanceUnits}"))
//      } else {
//        val el = sc(ac.naRangeDistanceKey).head
//        if (el < 0) {
//          Invalid(ItemValueOutOfRangeIssue(s"Range distance value of $el for position must be greater than or equal 0 km."))
//        } else Valid
//      }
//    }
//  }
//
//  /**
//   * Validation for the setElevation SetupConfig
//   * @param sc the received SetupConfig
//   * @return Valid or Invalid
//   */
//  public static XXX setElevationValidation(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {
//    if (sc.configKey != ac.setElevationCK) {
//      Invalid(WrongConfigKeyIssue("The SetupConfig is not a setElevation configuration"))
//    } else // Check for correct key and type -- only checks that essential key is present, not strict
//    if (sc.missingKeys(ac.naElevationKey).nonEmpty) {
//      Invalid(MissingKeyIssue(s"The setElevation SetupConfig must have a parameter named: ${ac.naElevationKey}"))
//    } else if (!sc(ac.naElevationKey).isInstanceOf[DoubleItem]) {
//      Invalid(WrongItemTypeIssue(s"The setElevation SetupConfig must have a parameter: ${ac.naElevationKey} as a DoubleItem"))
//    } else if (sc(ac.naElevationKey).units != ac.naRangeDistanceUnits) {
//      Invalid(WrongUnitsIssue(s"The move SetupConfig parameter: ${ac.naElevationKey} must have units: ${ac.naElevationUnits}"))
//    } else Valid
//  }
//
//  /**
//   * Validation for the setAngle SetupConfig
//   * @param sc the received SetupConfig
//   * @return Valid or Invalid
//   */
//  public static XXX setAngleValidation(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {
//    if (sc.configKey != ac.setAngleCK) {
//      Invalid(WrongConfigKeyIssue("The SetupConfig is not a setAngle configuration"))
//    } else // Check for correct key and type -- only checks that essential key is present, not strict
//    if (!sc.exists(ac.zenithAngleKey)) {
//      Invalid(MissingKeyIssue(s"The setAngle SetupConfig must have a DoubleItem named: ${ac.zenithAngleKey}"))
//    } else if (!sc(ac.zenithAngleKey).isInstanceOf[DoubleItem]) {
//      Invalid(WrongItemTypeIssue(s"The setAngle SetupConfig must have a DoubleItem named: ${ac.zenithAngleKey}"))
//    } else if (sc(ac.zenithAngleKey).units != ac.zenithAngleUnits) {
//      Invalid(WrongUnitsIssue(s"The setAngle SetupConfig parameter: ${ac.zenithAngleKey} must have units: ${ac.zenithAngleUnits}"))
//    } else Valid
//  }
//
//  /**
//   * Validation for the follow SetupConfig
//   * @param sc the received SetupConfig
//   * @return Valid or Invalid
//   */
//  public static XXX followValidation(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {
//    if (sc.configKey != ac.followCK) {
//      Invalid(WrongConfigKeyIssue("The SetupConfig is not a follow configuration"))
//    } else // Check for correct key and type -- only checks that essential key is present, not strict
//    if (!sc.exists(ac.nssInUseKey)) {
//      Invalid(MissingKeyIssue(s"The follow SetupConfig must have a BooleanItem named: ${ac.nssInUseKey}"))
//    } else if (!sc(ac.nssInUseKey).isInstanceOf[BooleanItem]) {
//      Invalid(WrongItemTypeIssue(s"The follow SetupConfig must have a BooleanItem named ${ac.nssInUseKey}"))
//    } else Valid
//  }
//
//}
