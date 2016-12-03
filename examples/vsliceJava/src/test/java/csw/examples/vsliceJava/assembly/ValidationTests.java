package csw.examples.vsliceJava.assembly;

import csw.services.ccs.CommandStatus;
import csw.util.config.Configurations;
import csw.util.config.Configurations.SequenceConfig;
import csw.util.config.Configurations.SetupConfig;
import csw.util.config.Configurations.SetupConfigArg;
import csw.util.config.DoubleKey;
import javacsw.services.ccs.JCommandStatus;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static csw.examples.vsliceJava.assembly.AssemblyContext.*;
import static csw.examples.vsliceJava.assembly.ConfigValidation.*;
import static csw.services.ccs.Validation.*;
import static javacsw.services.ccs.JCommandStatus.NotAccepted;
import static javacsw.services.ccs.JValidation.Valid;
import static javacsw.services.ccs.JValidation.WrongConfigKeyIssue;
import static javacsw.util.config.JItems.jadd;
import static javacsw.util.config.JItems.jset;
import static javacsw.util.config.JUnitsOfMeasure.kilometers;
import static junit.framework.TestCase.*;


/**
 * These are tests of the calculations in the Calculation Actor
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class ValidationTests {
  private static AssemblyContext assemblyContext = AssemblyTestData.TestAssemblyContext;

  Invalid checkInvalid(Validation result) {
    assertTrue(result instanceof Invalid);
    return (Invalid) result;
  }

  void checkForWrongConfigKey(Validation result) {
    assertTrue(checkInvalid(result).issue() instanceof WrongConfigKeyIssue);
  }

  void checkForMissingKeys(Validation result) {
    assertTrue(checkInvalid(result).issue() instanceof MissingKeyIssue);
  }

  void checkForWrongItemType(Validation result) {
    assertTrue(checkInvalid(result).issue() instanceof WrongItemTypeIssue);
  }

  void checkForWrongUnits(Validation result) {
    assertTrue(checkInvalid(result).issue() instanceof WrongUnitsIssue);
  }

  void checkForWrongNumberOfParameters(Validation result) {
    assertTrue(checkInvalid(result).issue() instanceof WrongNumberOfItemsIssue);
  }

  void checkForOutOfRange(Validation result) {
    assertTrue(checkInvalid(result).issue() instanceof ItemValueOutOfRangeIssue);
  }

  void checkForOtherIssue(Validation result) {
    assertTrue(checkInvalid(result).issue() instanceof OtherIssue);
  }

  /*
   * Test Description: This tests the validation of the init SC
   */

  // --- testing validation for init command ---

  @Test
  public void test1() {
    // should fail if not an init
    SetupConfig sc = new SetupConfig(assemblyContext.positionCK.prefix());
    checkForWrongConfigKey(initValidation(sc, assemblyContext));
  }

  @Test
  public void test2() {
    // should validate init setupconfig with 0 args
    SetupConfig sc = new SetupConfig(assemblyContext.initCK.prefix());

    // Should validate with no arguments
    assertEquals(initValidation(sc, assemblyContext), Valid);
  }

  @Test
  public void test3() {
    // should validate 2 arg init setupconfig
    SetupConfig sc = jadd(new SetupConfig(assemblyContext.initCK.prefix()),
      jset(configurationNameKey, "config1"),
      jset(configurationVersionKey, "1.0"));

    // Should validate with 2 good arguments
    assertEquals(initValidation(sc, assemblyContext), Valid);

    // Should be invalid with an extra argument
    sc = sc.add(jset(zenithAngleKey, 0.0));
    checkForWrongNumberOfParameters(initValidation(sc, assemblyContext));
  }

  @Test
  public void test4() {
    // should check for init item types
    // Make a key with the correct name that isn't the right type
    DoubleKey cvKey = new DoubleKey(configurationVersionKey.keyName());
    SetupConfig sc = jadd(new SetupConfig(assemblyContext.initCK.prefix()),
      jset(configurationNameKey, "config1"),
      jset(cvKey, 1.0));
    // Should be invalid
    checkForWrongItemType(initValidation(sc, assemblyContext));
  }

  /*
   * Test Description: This tests the validation of the move SC
   */

  // --- testing validation of move setupconfig ---

  // should fail if not a move
  @Test
  public void test5() {
    SetupConfig sc = new SetupConfig(assemblyContext.positionCK.prefix());
    checkForWrongConfigKey(moveValidation(sc, assemblyContext));
  }

  @Test
  public void test6() {
    // should validate the move setupconfig with 0 args
    SetupConfig sc = new SetupConfig(assemblyContext.moveCK.prefix());
    // Should validate with no arguments
    assertEquals(moveValidation(sc, assemblyContext), Valid);
  }

  @Test
  public void test7() {
    // should validate 1 arg move setupconfig
    // Create but don't set units
    SetupConfig sc = new SetupConfig(assemblyContext.moveCK.prefix()).add(jset(stagePositionKey, 22.0));

    // Should fail for units
    checkForWrongUnits(moveValidation(sc, assemblyContext));

    // Now add good units
    sc = sc.add(jset(stagePositionKey, 22.0).withUnits(stagePositionUnits));

    // Validates with 1 good argument
    assertEquals(moveValidation(sc, assemblyContext), Valid);

    // Should be valid with an extra argument in this case
    sc = sc.add(jset(zenithAngleKey, 0.0));
    assertEquals(moveValidation(sc, assemblyContext), Valid);
  }

  /*
   * Test Description: This tests the validation of the position SC
   */
  // --- testing validation of position setupconfig ---

  @Test
  public void test8() {
    // should fail if not a position
    SetupConfig sc = new SetupConfig(assemblyContext.moveCK.prefix());
    checkForWrongConfigKey(positionValidation(sc, assemblyContext));
  }

  @Test
  public void test9() {
    // should fail for missing unitsg
    SetupConfig sc = new SetupConfig(assemblyContext.positionCK.prefix()).add(jset(naRangeDistanceKey, 22.0));

    // Should fail for units
    checkForWrongUnits(positionValidation(sc, assemblyContext));
  }

  @Test
  public void test10() {
    // should validate when keys and units present
    // Now add good units
    SetupConfig sc = new SetupConfig(assemblyContext.positionCK.prefix()).add(
      jset(naRangeDistanceKey, 22.0).withUnits(naRangeDistanceUnits));

    // Should validate with 1 good argument
    assertEquals(positionValidation(sc, assemblyContext), Valid);

    // Should be valid with an extra argument in this case
    sc = sc.add(jset(zenithAngleKey, 0.0));
    assertEquals(positionValidation(sc, assemblyContext), Valid);
  }

  @Test
  public void test11() {
    // should fail for negative range distance value
    // Now  good units with neg value
    SetupConfig sc = new SetupConfig(assemblyContext.positionCK.prefix()).add(
      jset(naRangeDistanceKey, -22.0).withUnits(naRangeDistanceUnits));
    checkForOutOfRange(positionValidation(sc, assemblyContext));
  }

  /*
   * Test Description: This tests the validation of the setElevation SC
   */
  // --- testing validation for setElevation command ---

  @Test
  public void test12() {
    // should fail if not a setElevation
    SetupConfig sc = new SetupConfig(assemblyContext.initCK.prefix());
    checkForWrongConfigKey(setElevationValidation(sc, assemblyContext));
  }

  @Test
  public void test13() {
    // should vail to vailidate for missing units and keys
    // First check for missing args
    SetupConfig sc = new SetupConfig(assemblyContext.setElevationCK.prefix());
    checkForMissingKeys(setElevationValidation(sc, assemblyContext));

    // Should validate with 2 good arguments
    sc = jadd(sc, jset(zenithAngleKey, 0.0), jset(naElevationKey, 100.0));
    checkForWrongUnits(setElevationValidation(sc, assemblyContext));
  }

  @Test
  public void test14() {
    /// should validate 2 arg setElevation setupconfig
    SetupConfig sc = jadd(new SetupConfig(assemblyContext.setElevationCK.prefix()),
      jset(zenithAngleKey, 0.0).withUnits(zenithAngleUnits),
      jset(naElevationKey, 100.0).withUnits(naElevationUnits));
    assertEquals(setElevationValidation(sc, assemblyContext), Valid);

    // Should ignore an extra parameter
    sc = sc.add(jset(naRangeDistanceKey, 0.0));
    assertEquals(setElevationValidation(sc, assemblyContext), Valid);
  }

  @Test
  public void test15() {
    // should check for init item types
    // Make a key with the correct name that isn't the right type
    DoubleKey cvKey = new DoubleKey(configurationVersionKey.keyName());
    SetupConfig sc = jadd(new SetupConfig(assemblyContext.initCK.prefix()),
      jset(configurationNameKey, "config1"),
      jset(cvKey, 1.0));
    // Should be invalid
    assertTrue(initValidation(sc, assemblyContext) instanceof Invalid);
  }


  /*
   * Test Description: Test tests the validation of a setAngle SC
   */

  // --- testing validation of setAngle setupconfig

  public void test16() {
    // should fail if not a setAngle
    SetupConfig sc = new SetupConfig(assemblyContext.moveCK.prefix());
    checkForWrongConfigKey(setAngleValidation(sc, assemblyContext));
  }

  @Test
  public void test17() {
    // should fail for missing key
    SetupConfig sc = new SetupConfig(assemblyContext.setAngleCK.prefix());

    // Should fail for units
    checkForMissingKeys(setAngleValidation(sc, assemblyContext));
  }

  @Test
  public void test18() {
    // should fail for missing units
    SetupConfig sc = new SetupConfig(assemblyContext.setAngleCK.prefix()).add(jset(zenithAngleKey, 22.0));

    // Should fail for units
    checkForWrongUnits(setAngleValidation(sc, assemblyContext));
  }

  @Test
  public void test19() {
    // should validate when keys and units present
    // Now add good units
    SetupConfig sc = new SetupConfig(assemblyContext.setAngleCK.prefix()).add(
      jset(zenithAngleKey, 22.0).withUnits(zenithAngleUnits));

    // Should validate with 1 good argument
    assertEquals(setAngleValidation(sc, assemblyContext), Valid);

    // Should be valid with an extra argument in this case
    sc = sc.add(jset(naElevationKey, 0.0));
    assertEquals(setAngleValidation(sc, assemblyContext), Valid);
  }

  /*
   * Test Description: This tests the validation of a follow SC
   */
  // --- testing validation of follow setupconfig ---

  @Test
  public void test20() {
    // should fail if not a follow
    SetupConfig sc = new SetupConfig(assemblyContext.moveCK.prefix());
    checkForWrongConfigKey(followValidation(sc, assemblyContext));
  }

  @Test
  public void test21() {
    // should fail for missing key
    SetupConfig sc = new SetupConfig(assemblyContext.followCK.prefix());

    // Should fail for units
    checkForMissingKeys(followValidation(sc, assemblyContext));
  }

  @Test
  public void test22() {
    // should validate when key present
    SetupConfig sc = new SetupConfig(assemblyContext.followCK.prefix()).add(jset(nssInUseKey, true));

    // Should validate with 1 good argument
    assertEquals(followValidation(sc, assemblyContext), Valid);

    // Should be valid with an extra argument in this case
    sc = sc.add(jset(naElevationKey, 0.0));
    assertEquals(followValidation(sc, assemblyContext), Valid);
  }

  /*
   * Test Description: This is a test of the SetupConfigARg validation routine in TromboneAssembly
   */
  // --- Test of TromboneAssembly validation ---

  @Test
  public void test23() {
    // should work with okay sca
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()),
      new SetupConfig(assemblyContext.stopCK.prefix()));

    List<Invalid> issues = invalidsInTromboneSetupConfigArg(sca, assemblyContext);
    assertTrue(issues.isEmpty());
  }

  @Test
  public void test24() {
    // should show a single issue
    // positionCK requires an argument
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()),
      new SetupConfig(assemblyContext.positionCK.prefix()));
    List<Invalid> issues = invalidsInTromboneSetupConfigArg(sca, assemblyContext);
    assertFalse(issues.isEmpty());
    assertEquals(issues.size(), 1);
    checkForMissingKeys(issues.get(0));
  }

  @Test
  public void test25() {
    // should show multiple issues
    // positionCK needs an argument and moveCK has the wrong units
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()),
      new SetupConfig(assemblyContext.positionCK.prefix()),
      jadd(new SetupConfig(assemblyContext.moveCK.prefix()), jset(stagePositionKey, 22.0).withUnits(kilometers)));
    List<Invalid> issues = invalidsInTromboneSetupConfigArg(sca, assemblyContext);
    assertTrue(!issues.isEmpty());
    assertEquals(issues.size(), 2);
    checkForMissingKeys(issues.get(0));
    checkForWrongUnits(issues.get(1));
  }

  @Test
  public void test26() {
    // should convert validation invalid successfully to a CommandStatus invalid
    String testmessage = "test message";

    Invalid t1 = new Invalid(WrongConfigKeyIssue(testmessage));

    CommandStatus.Invalid c1 = JCommandStatus.Invalid(t1);
    assertTrue(c1.issue() instanceof WrongConfigKeyIssue);
    assertEquals(c1.issue().reason(), testmessage);
  }

  @Test
  public void test27() {
    // should convert validation result to comand status result
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()),
      new SetupConfig(assemblyContext.positionCK.prefix()),
      new SetupConfig(assemblyContext.moveCK.prefix()).add(jset(stagePositionKey, 22.0).withUnits(kilometers)));

    // Check if validated properly
    List<Validation> validations = ConfigValidation.validateTromboneSetupConfigArg(sca, assemblyContext);
    assertEquals(validations.size(), sca.configs().size());
    assertEquals(validations.get(0), Valid);
    assertTrue(validations.get(1) instanceof Invalid);
    assertTrue(validations.get(2) instanceof Invalid);

    // Convert to pairs
    List<SequenceConfig> configs = sca.getConfigs().stream().map(f -> (SequenceConfig) f).collect(Collectors.toList());
    List<CommandStatus.CommandResultPair> cresult = CommandStatus.validationsToCommandResultPairs(configs, validations);
    assertEquals(cresult.size(), sca.configs().size());
    assertEquals(cresult.get(0).status(), JCommandStatus.Valid);
    assertEquals(cresult.get(0).config(), sca.getConfigs().get(0));

    assertTrue(cresult.get(1).status() instanceof CommandStatus.Invalid);
    assertEquals(cresult.get(1).config(), sca.getConfigs().get(1));

    assertTrue(cresult.get(2).status() instanceof CommandStatus.Invalid);
    assertEquals(cresult.get(2).config(), sca.getConfigs().get(2));

    // Is correct overall returned
    assertEquals(CommandStatus.validationsToOverallCommandStatus(validations), NotAccepted);

    // Same with no errors
    SetupConfigArg sca2 = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()), assemblyContext.positionSC(22.0), assemblyContext.moveSC(44.0));

    List<Validation> validations2 = ConfigValidation.validateTromboneSetupConfigArg(sca2, assemblyContext);
    assertTrue(isAllValid(validations2));
  }
}

