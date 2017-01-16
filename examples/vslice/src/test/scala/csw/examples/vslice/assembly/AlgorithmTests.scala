package csw.examples.vslice.assembly

import org.scalatest.{BeforeAndAfterAll, FunSpec, Inspectors, ShouldMatchers}

/**
 * These are tests of the calculations in the Calculation Actor
 */
class AlgorithmTests extends FunSpec with ShouldMatchers with Inspectors {
  import Algorithms._
  import AssemblyTestData._

  import AssemblyTestData.TestAssemblyContext._

  def ~=(x: Double, y: Double, precision: Double) = {
    if ((x - y).abs < precision) true else false
  }

  val testFocusError = -20 to 20 by 4

  val calculationConfig = TestCalculationConfig
  val controlConfig = TestControlConfig

  val initialElevation = calculationConfig.defaultInitialElevation

  describe("Testing the algorithms for correctness without actor") {

    it("should produce good range distance error - good") {

      val rde = testFocusError.map(focusErrorToRangeError(calculationConfig, _))
      // Verify reasonableness
      // range distances should be the same at 0 za
      rde.head shouldBe >(minReasonableRangeError)
      rde.last shouldBe <(maxReasonableRangeError)
    }

    def round(din: Double): Double = Math.rint(din * 100) / 100

    it("should have good zenith angle to range distance - good") {

      val testElevation = calculationConfig.defaultInitialElevation

      val rds = testZenithAngles.map(zenithAngleToRangeDistance(testElevation, _))

      // Verify reasonableness
      // range distances should be the same at 0 za
      rds.head shouldBe >(minTotalRD)
      rds.last shouldBe <(maxTotalRD)
    }

    // This is the approach used to find the new elevation after adding the range error
    it("should work with elevation too") {
      val testElevation = calculationConfig.defaultInitialElevation

      val rds = testZenithAngles.map(zenithAngleToRangeDistance(testElevation, _))

      val in = testZenithAngles.zip(rds)

      val els = in.map(f => rangeDistanceToElevation(f._2, f._1)).map(round)

      els.forall(_.equals(testElevation)) shouldBe true

    }

    it("should work with the next values generation") {
      // First change the zenith angle with a fixed fe so range distance = elevation
      val fe1 = 0.0
      val p1 = testZenithAngles.map(focusZenithAngleToElevationAndRangeDistance(calculationConfig, calculationConfig.defaultInitialElevation, fe1, _))
      //info("P1: " + p1)
      p1.head._1 should equal(p1.head._2)
      p1.head._1 should equal(calculationConfig.defaultInitialElevation)
      p1.last._1 should equal(maxRD)
      p1.last._2 should equal(calculationConfig.defaultInitialElevation)

      val fe2 = calculationConfig.lowerFocusLimit
      val p2 = testZenithAngles.map(focusZenithAngleToElevationAndRangeDistance(calculationConfig, calculationConfig.defaultInitialElevation, fe2, _))
      //info("P2: " + p2)
      p2.head._1 should equal(p2.head._2)
      p2.head._1 should equal(calculationConfig.defaultInitialElevation + minRDError)

      val fe3 = calculationConfig.upperFocusLimit
      val p3 = testZenithAngles.map(focusZenithAngleToElevationAndRangeDistance(calculationConfig, calculationConfig.defaultInitialElevation, fe3, _))
      //info("P3: " + p3)
      p3.head._1 should equal(p3.head._2)
      p3.head._1 should equal(calculationConfig.defaultInitialElevation + maxRDError)
    }

    it("should verify focuserror values") {
      verifyFocusError(TestCalculationConfig, focusErrorKey -> 0.0) should be(true)
      verifyFocusError(TestCalculationConfig, focusErrorKey -> 15.0) should be(true)
      verifyFocusError(TestCalculationConfig, focusErrorKey -> -21.0) should be(false)
      verifyFocusError(TestCalculationConfig, focusErrorKey -> 41.0) should be(false)
    }

    it("should verify zenith angle values") {
      verifyZenithAngle(zenithAngleKey -> 0.0) should be(true)
      verifyZenithAngle(zenithAngleKey -> -1.0) should be(false)
      verifyZenithAngle(zenithAngleKey -> 92.0) should be(false)
    }

    it("should take a range distance and provide a reasonable stage position") {
      val minStage = rangeDistanceToStagePosition(minRD)
      val maxStage = rangeDistanceToStagePosition(maxRD)

      minStage shouldBe >(minReasonableStage)
      maxStage shouldBe <(maxReasonableStage)
    }

    it("should provide a reasonable encoder value") {
      val minStage = rangeDistanceToStagePosition(minRD)
      val maxStage = rangeDistanceToStagePosition(maxRD)

      val minEncoder = stagePositionToEncoder(controlConfig, minStage)
      val maxEncoder = stagePositionToEncoder(controlConfig, maxStage)

      info(s"minStage/maxStage: $minStage/$maxStage")
      info(s"minEnc/maxEnc: $minEncoder/$maxEncoder")
      info(s"zero: ${stagePositionToEncoder(controlConfig, 0.0)}")

      minEncoder shouldBe >(controlConfig.minEncoderLimit)
      maxEncoder shouldBe <(controlConfig.maxEncoderLimit)
    }

    it("should give good data") {

      println("test1: " + feRangeErrorTestValues)
      println("test2: " + zaRangeDistance)

      println("tst3: " + newRangeAndElData(-20.0))

      println("Test4: " + calculatedTestData(calculationConfig, controlConfig, -20.0))
    }

  }

}
