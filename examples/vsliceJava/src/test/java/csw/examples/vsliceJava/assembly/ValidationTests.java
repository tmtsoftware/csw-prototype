package csw.examples.vsliceJava.assembly;

import csw.util.config.Configurations.SetupConfig;
import csw.util.config.DoubleKey;
import javacsw.services.ccs.JValidation;
import org.junit.Test;

import static csw.examples.vsliceJava.assembly.AssemblyContext.*;
import static csw.examples.vsliceJava.assembly.ConfigValidation.*;
import static csw.services.ccs.Validation.*;
import static javacsw.services.ccs.JValidation.*;
import static javacsw.util.config.JItems.jadd;
import static javacsw.util.config.JItems.jset;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;


/**
 * These are tests of the calculations in the Calculation Actor
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class ValidationTests {
  private static AssemblyContext assemblyContext = AssemblyTestData.TestAssemblyContext;

  Invalid checkInvalid(Validation result) {
    assertTrue(result instanceof Invalid);
    return (Invalid)result;
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
      assertEquals(setElevationValidation(sc,assemblyContext), Valid);

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

//    it("should fail for missing units") {
//      SetupConfig sc = new SetupConfig(setAngleCK).add(zenithAngleKey -> 22.0)
//
//      // Should fail for units
//      checkForWrongUnits(setAngleValidation(sc))
//    }
//
//    it("should validate when keys and units present") {
//      // Now add good units
//      var sc = SetupConfig(setAngleCK).add(zenithAngleKey -> 22.0 withUnits zenithAngleUnits)
//
//      // Should validate with 1 good argument
//      setAngleValidation(sc) should be(Valid)
//
//      // Should be valid with an extra argument in this case
//      sc = sc.add(naElevationKey -> 0.0)
//      setAngleValidation(sc), Valid
//    }
//  }
//
//  /**
//   * Test Description: This tests the validation of a follow SC
//   */
//  describe("testing validation of follow setupconfig") {
//    it("should fail if not a follow") {
//      SetupConfig sc = new SetupConfig(moveCK)
//      checkForWrongConfigKey(followValidation(sc))
//    }
//
//    it("should fail for missing key") {
//      SetupConfig sc = new SetupConfig(followCK)
//
//      // Should fail for units
//      checkForMissingKeys(followValidation(sc))
//    }
//
//    it("should validate when key present") {
//      var sc = SetupConfig(followCK).add(nssInUseKey -> true)
//
//      // Should validate with 1 good argument
//      followValidation(sc) should be(Valid)
//
//      // Should be valid with an extra argument in this case
//      sc = sc.add(naElevationKey -> 0.0)
//      followValidation(sc), Valid
//    }
//  }
//
//  /**
//   * Test Description: This is a test of the SetupConfigARg validation routine in TromboneAssembly
//   */
//  describe("Test of TromboneAssembly validation") {
//    //implicit val tc = AssemblyTestData.TestAssemblyContext
//
//    it("should work with okay sca") {
//      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(stopCK))
//
//      val issues = invalidsInTromboneSetupConfigArg(sca)
//      issues, empty
//    }
//
//    it("should show a single issue") {
//      // positionCK requires an argument
//      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(positionCK))
//      val issues: Seq[Invalid] = invalidsInTromboneSetupConfigArg(sca)
//      issues should not be empty
//      issues.size should be(1)
//      checkForMissingKeys(issues.head)
//    }
//
//    it("should show multiple issues") {
//      // positionCK needs an argument and moveCK has the wrong units
//      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(positionCK), SetupConfig(moveCK).add(stagePositionKey -> 22 withUnits kilometers))
//      val issues = invalidsInTromboneSetupConfigArg(sca)
//      issues should not be empty
//      issues.size should be(2)
//      checkForMissingKeys(issues.head)
//      checkForWrongUnits(issues(1))
//    }
//
//    it("should convert validation invalid successfully to a CommandStatus invalid") {
//      //import csw.services.ccs.CommandStatus.Invalid
//      val testmessage = "test message"
//
//      val t1 = Invalid(WrongConfigKeyIssue(testmessage))
//
//      val c1 = CommandStatus.Invalid(t1)
//      c1.issue, a[WrongConfigKeyIssue);
//      c1.issue.reason should equal(testmessage)
//
//    }
//
//    it("should convert validation result to comand status result") {
//      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(positionCK), SetupConfig(moveCK).add(stagePositionKey -> 22 withUnits kilometers))
//
//      // Check if validated properly
//      val validations = ConfigValidation.validateTromboneSetupConfigArg(sca)
//      validations.size should equal(sca.configs.size)
//      validations.head, Valid
//      validations(1), a[Invalid]
//      validations(2), a[Invalid]
//
//      // Convert to pairs
//      val cresult = CommandStatus.validationsToCommandResultPairs(sca.configs, validations)
//      cresult.size should equal(sca.configs.size)
//      cresult.head._1, CommandStatus.Valid
//      cresult.head._2 should equal(sca.configs.head)
//
//      cresult(1)._1, a[CommandStatus.Invalid]
//      cresult(1)._2 should equal(sca.configs(1))
//
//      cresult(2)._1, a[CommandStatus.Invalid]
//      cresult(2)._2 should equal(sca.configs(2))
//
//      // Is correct overall returned
//      CommandStatus.validationsToOverallCommandStatus(validations), NotAccepted
//
//      // Same with no errors
//      val sca2 = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), positionSC(22.0), moveSC(44.0))
//
//      val validations2 = ConfigValidation.validateTromboneSetupConfigArg(sca2)
//      isAllValid(validations2), true
//
//    }
//
//  }

}

