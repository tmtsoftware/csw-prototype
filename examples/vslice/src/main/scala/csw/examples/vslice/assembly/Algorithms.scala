package csw.examples.vslice.assembly

import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.assembly.FollowActor.CalculationConfig
import csw.examples.vslice.assembly.TromboneControl.TromboneControlConfig
import csw.util.config.DoubleItem
import csw.util.config.UnitsOfMeasure.millimeters

/**
  * TMT Source Code: 9/4/16.
  */
object Algorithms extends LazyLogging {

  /**
    * Arbitrary check of the zenith angle to be within bounds
    *
    * @param zenithAngle DoubleItem that contains zenith angle
    * @return true if valid else false
    */
  def verifyZenithAngle(zenithAngle: DoubleItem): Boolean = zenithAngle.head < 90.0 && zenithAngle.head >= 0.0

  /**
    * Checking the input focus error against fake limits of +/- 20
    *
    * @param focusError DoubleItem that contains focusError
    * @return true if valid else false
    */
  def verifyFocusError(focusError: DoubleItem): Boolean = focusError.head >= -20.0 && focusError.head <= 20.0

  // From Dan K's email,
  // elevation(t+1) =  cos(theta(t))*(elevation(t)/cos(theta(t)) + g*focusRangeDistanceError(t))
  // For this example, layer elevation is the initial elevation + a factor * cosine of zenith angle designed to be 90 to 100 km
  def naLayerElevation(calculationConfig: CalculationConfig, initialElevation: Double, zenithAngle: Double): Double =
    Math.cos(Math.toRadians(zenithAngle)) * calculationConfig.zenithFactor + initialElevation

  // The focus to range distance is  the gain * focus value/4 where focus is pinned to be between +/- 20
  def focusToRangeDistance(calculationConfig: CalculationConfig, focusError: Double): Double = {
    // Limit the focus Error
    val pinnedFocusValue = Math.max(calculationConfig.lowerFocusLimit, Math.min(calculationConfig.upperFocusLimit, focusError))
    calculationConfig.focusErrorGain * pinnedFocusValue / 4.0
  }

  /**
    * NALayerRange = zfactor*zenithAngle + focusError
    *
    * zfactor is read from configuration
    *
    * @param stagePosition is the value of the stage position in millimeters (currently the total NA elevation)
    * @return DoubleItem with key naTrombonePosition and units of enc
    */
  def rangeDistanceTransform(controlConfig: TromboneControlConfig, stagePosition: DoubleItem): Int = {
    if (stagePosition.units != millimeters) {
      // Flag an error but don't do anything else
      logger.error(s"rangeDistanceTransform: stage position units should be millimeters but are: ${stagePosition.units}")
    }
    // Scale value to be between 200 and 1000 encoder
    val encoderValue = (controlConfig.positionScale * (stagePosition.head - controlConfig.minElevation) + controlConfig.minElevationEncoder).toInt
    val pinnedEncValue = Math.max(controlConfig.minEncoderLimit, Math.min(controlConfig.maxEncoderLimit, encoderValue))
    pinnedEncValue
  }

}
