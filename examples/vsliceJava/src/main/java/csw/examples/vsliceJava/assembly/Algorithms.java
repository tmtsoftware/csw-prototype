package csw.examples.vsliceJava.assembly;

import akka.japi.Pair;
import csw.util.config.DoubleItem;
import csw.examples.vsliceJava.assembly.AssemblyContext.*;

import static javacsw.util.config.JItems.jvalue;

/**
 * This object contains functions that implement this test version of the trombone assembly algorithms.
 * <p>
 * These algorithms are representative in spirit, but not technically correct. The actual code will be
 * a closed loop and continually recalculate the elevation. Since the correctness of the algorithms was
 * not really the focus of the vertical slice, I wanted to just get something that sort of did the
 * right things and had the same variables.
 * <p>
 * The algorithms here are implemented in an object so they can be tested independently.  I think this is
 * a good strategy.  Then they are called from the FollowActor and other places. The method names are
 * representative of their function: rangeDistanceToElevation for instance.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class Algorithms {

  /**
   * Arbitrary check of the zenith angle to be within bounds
   *
   * @param zenithAngle DoubleItem that contains zenith angle
   * @return true if valid else false
   */
  public static boolean verifyZenithAngle(DoubleItem zenithAngle) {
    return jvalue(zenithAngle) < 90.0 && jvalue(zenithAngle) >= 0.0;
  }

  /**
   * Checking the input focus error against fake limits of +/- 20
   *
   * @param focusError DoubleItem that contains focusError
   * @return true if valid else false
   */
  public static boolean verifyFocusError(TromboneCalculationConfig calculationConfig, DoubleItem focusError) {
    return jvalue(focusError) >= calculationConfig.lowerFocusLimit && jvalue(focusError) <= calculationConfig.upperFocusLimit;
  }

  public static double zenithAngleToRangeDistance(double elevation, double zenithAngle) {
    return elevation / Math.cos(Math.toRadians(zenithAngle));
  }

  public static double rangeDistanceToElevation(double rangeDistance, double zenithAngle) {
    return Math.cos(Math.toRadians(zenithAngle)) * rangeDistance;
  }

  public static Pair<Double, Double> focusZenithAngleToElevationAndRangeDistance(TromboneCalculationConfig calculationConfig,
                                                                                 double elevation, double focusError, double zenithAngle) {
    double totalRangeDistance = focusZenithAngleToRangeDistance(calculationConfig, elevation, focusError, zenithAngle);
    double newElevation = rangeDistanceToElevation(totalRangeDistance, zenithAngle);
    return new Pair<>(totalRangeDistance, newElevation);
  }

  public static double focusZenithAngleToRangeDistance(TromboneCalculationConfig calculationConfig, double elevation, double focusError, double zenithAngle) {
    double rangeDistance1 = zenithAngleToRangeDistance(elevation, zenithAngle);
    double rangeError = focusErrorToRangeError(calculationConfig, focusError);
    return rangeDistance1 + rangeError;
  }

  // The focus to range distance is  the gain * focus value/4 where focus is pinned to be between +/- 20
  public static double focusErrorToRangeError(TromboneCalculationConfig calculationConfig, double focusError) {
    // Limit the focus Error
    double pinnedFocusValue = Math.max(calculationConfig.lowerFocusLimit, Math.min(calculationConfig.upperFocusLimit, focusError));
    return calculationConfig.focusErrorGain * pinnedFocusValue / 4.0;
  }

  /**
   * Converts the range distance is kilometers to stage position in millimeters
   * Note that in this example, the stage position is just the elevation value
   * Note that the units must be checked in the caller
   *
   * @param rangeDistance in kilometer units
   * @return stage position in millimeters
   */
  public static double rangeDistanceToStagePosition(double rangeDistance) {
    return rangeDistance;
  }

  /**
   * @param stagePosition is the value of the stage position in millimeters (currently the total NA elevation)
   * @return DoubleItem with key naTrombonePosition and units of enc
   */
  @SuppressWarnings("UnnecessaryLocalVariable")
  public static int stagePositionToEncoder(TromboneControlConfig controlConfig, double stagePosition) {
    // Scale value to be between 200 and 1000 encoder
    int encoderValue = (int) (controlConfig.positionScale * (stagePosition - controlConfig.stageZero) + controlConfig.minStageEncoder);
    int pinnedEncValue = Math.max(controlConfig.minEncoderLimit, Math.min(controlConfig.maxEncoderLimit, encoderValue));
    return pinnedEncValue;
  }

}
