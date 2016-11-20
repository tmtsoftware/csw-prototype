package csw.examples.vslice.assembly

import csw.services.ccs.CommandStatus
import csw.services.ccs.CommandStatus.NotAccepted
import csw.services.ccs.Validation._
import csw.util.config.Configurations.SetupConfig
import csw.util.config.UnitsOfMeasure.kilometers
import csw.util.config.{Configurations, DoubleKey}
import org.scalatest.{BeforeAndAfterAll, FunSpec, Inspectors, ShouldMatchers}

/**
 * TMT Source Code: 8/25/16.
 */
class ValidationTests extends FunSpec with ShouldMatchers with Inspectors with BeforeAndAfterAll {
  import ConfigValidation._

  implicit val ac = AssemblyTestData.TestAssemblyContext
  import ac._

  def checkInvalid(result: Validation): Invalid = {
    result shouldBe a[Invalid]
    result.asInstanceOf[Invalid]
  }

  def checkForWrongConfigKey(result: Validation): Unit = {
    checkInvalid(result).issue shouldBe a[WrongConfigKeyIssue]
  }

  def checkForMissingKeys(result: Validation): Unit = {
    checkInvalid(result).issue shouldBe a[MissingKeyIssue]
  }

  def checkForWrongItemType(result: Validation): Unit = {
    checkInvalid(result).issue shouldBe a[WrongItemTypeIssue]
  }

  def checkForWrongUnits(result: Validation): Unit = {
    checkInvalid(result).issue shouldBe a[WrongUnitsIssue]
  }

  def checkForWrongNumberOfParameters(result: Validation): Unit = {
    checkInvalid(result).issue shouldBe a[WrongNumberOfItemsIssue]
  }

  def checkForOutOfRange(result: Validation): Unit = {
    checkInvalid(result).issue shouldBe a[ItemValueOutOfRangeIssue]
  }

  def checkForOtherIssue(result: Validation): Unit = {
    checkInvalid(result).issue shouldBe a[OtherIssue]
  }

  /**
   * Test Description: This tests the validation of the init SC
   */
  describe("testing validation for init command") {

    it("should fail if not an init") {
      val sc = SetupConfig(positionCK)
      checkForWrongConfigKey(initValidation(sc))
    }

    it("should validate init setupconfig with 0 args") {
      val sc = SetupConfig(initCK)

      // Should validate with no arguments
      initValidation(sc) should be(Valid)
    }
    it("should validate 2 arg init setupconfig") {
      var sc = SetupConfig(initCK).madd(configurationNameKey -> "config1", configurationVersionKey -> "1.0")

      // Should validate with 2 good arguments
      initValidation(sc) should be(Valid)

      // Should be invalid with an extra argument
      sc = sc.add(zenithAngleKey -> 0.0)
      checkForWrongNumberOfParameters(initValidation(sc))
      //initValidation(sc).isInstanceOf[Invalid] should be(true)
    }
    it("should check for init item types") {
      // Make a key with the correct name that isn't the right type
      val cvKey = DoubleKey(configurationVersionKey.keyName)
      val sc = SetupConfig(initCK).madd(configurationNameKey -> "config1", cvKey -> 1.0)
      // Should be invalid
      checkForWrongItemType(initValidation(sc))
    }
  }

  /**
   * Test Description: This tests the validation of the move SC
   */
  describe("testing validation of move setupconfig") {
    it("should fail if not a move") {
      val sc = SetupConfig(positionCK)
      checkForWrongConfigKey(moveValidation(sc))
    }

    it("should validate the move setupconfig with 0 args") {
      val sc = SetupConfig(moveCK)
      // Should validate with no arguments
      moveValidation(sc) should be(Valid)
    }

    it("should validate 1 arg move setupconfig") {
      // Create but don't set units
      var sc = SetupConfig(moveCK).add(stagePositionKey -> 22.0)

      // Should fail for units
      checkForWrongUnits(moveValidation(sc))

      // Now add good units
      sc = sc.add(stagePositionKey -> 22.0 withUnits stagePositionUnits)

      // Validates with 1 good argument
      moveValidation(sc) should be(Valid)

      // Should be valid with an extra argument in this case
      sc = sc.add(zenithAngleKey -> 0.0)
      moveValidation(sc) shouldBe Valid
    }
  }

  /**
   * Test Description: This tests the validation of the position SC
   */
  describe("testing validation of position setupconfig") {
    it("should fail if not a position") {
      val sc = SetupConfig(moveCK)
      checkForWrongConfigKey(positionValidation(sc))
    }

    it("should fail for missing unitsg") {
      val sc = SetupConfig(positionCK).add(naRangeDistanceKey -> 22.0)

      // Should fail for units
      checkForWrongUnits(positionValidation(sc))
    }

    it("should validate when keys and units present") {
      // Now add good units
      var sc = SetupConfig(positionCK).add(naRangeDistanceKey -> 22.0 withUnits naRangeDistanceUnits)

      // Should validate with 1 good argument
      positionValidation(sc) shouldBe Valid

      // Should be valid with an extra argument in this case
      sc = sc.add(zenithAngleKey -> 0.0)
      positionValidation(sc) shouldBe Valid
    }

    it("should fail for negative range distance value") {
      // Now  good units with neg value
      val sc = SetupConfig(positionCK).add(naRangeDistanceKey -> -22.0 withUnits naRangeDistanceUnits)
      checkForOutOfRange(positionValidation(sc))
    }
  }

  /**
   * Test Description: This tests the validation of the setElevation SC
   */
  describe("testing validation for setElevation command") {

    it("should fail if not a setElevation") {
      val sc = SetupConfig(initCK)
      checkForWrongConfigKey(setElevationValidation(sc))
    }

    it("should vail to vailidate for missing units and keys") {
      // First check for missing args
      var sc = SetupConfig(setElevationCK)
      checkForMissingKeys(setElevationValidation(sc))

      // Should validate with 2 good arguments
      sc = sc.madd(zenithAngleKey -> 0.0, naElevationKey -> 100.0)
      checkForWrongUnits(setElevationValidation(sc))
    }

    it("should validate 2 arg setElevation setupconfig") {
      var sc = SetupConfig(setElevationCK).madd(zenithAngleKey -> 0.0 withUnits zenithAngleUnits, naElevationKey -> 100.0 withUnits naElevationUnits)
      setElevationValidation(sc) should be(Valid)

      // Should ignore an extra parameter
      sc = sc.add(naRangeDistanceKey -> 0.0)
      setElevationValidation(sc) shouldBe Valid
    }

    it("should check for init item types") {
      // Make a key with the correct name that isn't the right type
      val cvKey = DoubleKey(configurationVersionKey.keyName)
      val sc = SetupConfig(initCK).madd(configurationNameKey -> "config1", cvKey -> 1.0)
      // Should be invalid
      //val result = initValidation(sc)
      //info("result: " + result)
      initValidation(sc).isInstanceOf[Invalid] should be(true)
    }
  }

  /**
   * Test Description: Test tests the validation of a setAngle SC
   */
  describe("testing validation of setAngle setupconfig") {
    it("should fail if not a setAngle") {
      val sc = SetupConfig(moveCK)
      checkForWrongConfigKey(setAngleValidation(sc))
    }

    it("should fail for missing key") {
      val sc = SetupConfig(setAngleCK)

      // Should fail for units
      checkForMissingKeys(setAngleValidation(sc))
    }

    it("should fail for missing units") {
      val sc = SetupConfig(setAngleCK).add(zenithAngleKey -> 22.0)

      // Should fail for units
      checkForWrongUnits(setAngleValidation(sc))
    }

    it("should validate when keys and units present") {
      // Now add good units
      var sc = SetupConfig(setAngleCK).add(zenithAngleKey -> 22.0 withUnits zenithAngleUnits)

      // Should validate with 1 good argument
      setAngleValidation(sc) should be(Valid)

      // Should be valid with an extra argument in this case
      sc = sc.add(naElevationKey -> 0.0)
      setAngleValidation(sc) shouldBe Valid
    }
  }

  /**
   * Test Description: This tests the validation of a follow SC
   */
  describe("testing validation of follow setupconfig") {
    it("should fail if not a follow") {
      val sc = SetupConfig(moveCK)
      checkForWrongConfigKey(followValidation(sc))
    }

    it("should fail for missing key") {
      val sc = SetupConfig(followCK)

      // Should fail for units
      checkForMissingKeys(followValidation(sc))
    }

    it("should validate when key present") {
      var sc = SetupConfig(followCK).add(nssInUseKey -> true)

      // Should validate with 1 good argument
      followValidation(sc) should be(Valid)

      // Should be valid with an extra argument in this case
      sc = sc.add(naElevationKey -> 0.0)
      followValidation(sc) shouldBe Valid
    }
  }

  /**
   * Test Description: This is a test of the SetupConfigARg validation routine in TromboneAssembly
   */
  describe("Test of TromboneAssembly validation") {
    //implicit val tc = AssemblyTestData.TestAssemblyContext

    it("should work with okay sca") {
      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(stopCK))

      val issues = invalidsInTromboneSetupConfigArg(sca)
      issues shouldBe empty
    }

    it("should show a single issue") {
      // positionCK requires an argument
      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(positionCK))
      val issues: Seq[Invalid] = invalidsInTromboneSetupConfigArg(sca)
      issues should not be empty
      issues.size should be(1)
      checkForMissingKeys(issues.head)
    }

    it("should show multiple issues") {
      // positionCK needs an argument and moveCK has the wrong units
      val sca = Configurations.createSetupConfigArg(
        "testobsId",
        SetupConfig(initCK),
        SetupConfig(positionCK),
        SetupConfig(moveCK).add(stagePositionKey -> 22 withUnits kilometers)
      )
      val issues = invalidsInTromboneSetupConfigArg(sca)
      issues should not be empty
      issues.size should be(2)
      checkForMissingKeys(issues.head)
      checkForWrongUnits(issues(1))
    }

    it("should convert validation invalid successfully to a CommandStatus invalid") {
      //import csw.services.ccs.CommandStatus.Invalid
      val testmessage = "test message"

      val t1 = Invalid(WrongConfigKeyIssue(testmessage))

      val c1 = CommandStatus.Invalid(t1)
      c1.issue shouldBe a[WrongConfigKeyIssue]
      c1.issue.reason should equal(testmessage)

    }

    it("should convert validation result to comand status result") {
      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(positionCK), SetupConfig(moveCK).add(stagePositionKey -> 22 withUnits kilometers))

      // Check if validated properly
      val validations = ConfigValidation.validateTromboneSetupConfigArg(sca)
      validations.size should equal(sca.configs.size)
      validations.head shouldBe Valid
      validations(1) shouldBe a[Invalid]
      validations(2) shouldBe a[Invalid]

      // Convert to pairs
      val cresult = CommandStatus.validationsToCommandResultPairs(sca.configs, validations)
      cresult.size should equal(sca.configs.size)
      cresult.head.status shouldBe CommandStatus.Valid
      cresult.head.config should equal(sca.configs.head)

      cresult(1).status shouldBe a[CommandStatus.Invalid]
      cresult(1).config should equal(sca.configs(1))

      cresult(2).status shouldBe a[CommandStatus.Invalid]
      cresult(2).config should equal(sca.configs(2))

      // Is correct overall returned
      CommandStatus.validationsToOverallCommandStatus(validations) shouldBe NotAccepted

      // Same with no errors
      val sca2 = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), positionSC(22.0), moveSC(44.0))

      val validations2 = ConfigValidation.validateTromboneSetupConfigArg(sca2)
      isAllValid(validations2) shouldBe true

    }

  }

}

