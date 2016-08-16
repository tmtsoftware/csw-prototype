package csw.examples.vslice.assembly

import csw.examples.vslice.assembly.CalculationActor.TromboneControlConfig
import org.scalatest.{BeforeAndAfterAll, FunSpec, Inspectors, ShouldMatchers}

/**
  * These are tests of the calculations in the Calculation Actor
  */
class AlgorithmTests extends FunSpec with ShouldMatchers with Inspectors with BeforeAndAfterAll {

  import AlgorithmData._

  def ~=(x: Double, y: Double, precision: Double) = {
    if ((x - y).abs < precision) true else false
  }

  val initialElevation = controlConfig.defaultInitialElevation

  describe("Testing the algorithms for correctness without actor") {
    import CalculationActor._
    import TromboneAssembly._

    val maxRD = focusToRangeDistance(controlConfig, controlConfig.upperFocusLimit)
    val maxTotal = maxRD + naLayerElevation(controlConfig, initialElevation, 0)

    val minRD = focusToRangeDistance(controlConfig, controlConfig.lowerFocusLimit)
    val minTotal = minRD + naLayerElevation(controlConfig, initialElevation, 70)

    val typRD = focusToRangeDistance(controlConfig, 0)
    val typTotal = typRD + naLayerElevation(controlConfig, initialElevation, 0)

    it("should test focusToRangeDistance") {
      val result = rangeTestValues.map(_._1).map(f => focusToRangeDistance(controlConfig, f))
      val answers = rangeTestValues.map(_._2)

      result should equal(answers)
    }

    it("should test elevation calculation") {
      val result = elevationTestValues.map(_._1).map(f => naLayerElevation(controlConfig, initialElevation, f))

      // This rolls up the results and compares each one
      val zippedAnswers = elevationTestValues.map(_._2).zip(result)
      forAll(zippedAnswers) { z => z._1 should equal(z._2 +- 0.01) } // .01 is loose buy good enough for this
    }

    it("total should be reasonable") {
      // Total values are at limits with +20 error and 0 zenith angle
      maxTotal should be < 100.0
      minTotal should be > 85.0
      info(s"m/m: + $minTotal/$maxTotal")
      info(s"Typ: $typTotal")
    }

    it("tests total to encoder within values") {
      val maxEncoder = rangeDistanceTransform(controlConfig, maxTotal)

      val minEncoder = rangeDistanceTransform(controlConfig, minTotal)
    }

    // This isn't a great test, but a real system would know this transformation and test it
    it("encoder values should test") {
      val result = encoderTestValues.map(_._1).map(f => rangeDistanceTransform(controlConfig, f))
      val answers = encoderTestValues.map(_._2)

      result should equal(answers)
    }

    it("should verify focuserror values") {
      verifyFocusError(focusErrorKey -> 0.0) should be(true)
      verifyFocusError(focusErrorKey -> -21.0) should be(false)
      verifyFocusError(focusErrorKey -> 21.0) should be(false)
    }

    it("should verify zenith angle values") {
      verifyZenithAngle(zenithAngleKey -> 0.0) should be(true)
      verifyZenithAngle(zenithAngleKey -> -1.0) should be(false)
      verifyZenithAngle(zenithAngleKey -> 92.0) should be(false)
    }
  }


}
