package csw.examples.vsliceJava.assembly;

import csw.examples.vsliceJava.assembly.AssemblyContext.TromboneCalculationConfig;
import csw.examples.vsliceJava.assembly.AssemblyContext.TromboneControlConfig;
import csw.services.loc.Connection;
import csw.services.pkg.Component;
import csw.services.pkg.Component.AssemblyInfo;
import javacsw.services.pkg.JComponent;

import java.util.Collections;

import static javacsw.services.loc.JConnectionType.AkkaType;
import static javacsw.services.pkg.JComponent.DoNotRegister;
import static csw.examples.vsliceJava.assembly.Algorithms.*;

/**
 * TMT Source Code: 8/12/16.
 */
@SuppressWarnings("unused")
public class AssemblyTestData {

  double[] testFocusErrors = new double[] {-20.0, -16.0, -12.0, -8.0, -4.0, 0.0, 4.0, 8.0, 12.0, 16.0, 20.0};
  double[] testZenithAngles = new double[] {0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0, 50.0, 55.0, 60.0};

  AssemblyInfo TestAssemblyInfo = JComponent.assemblyInfo(
    "tromboneAssembly",
    "nfiraos.ncc.trombone",
    "csw.examples.vslice.assembly.TromboneAssembly",
    DoNotRegister, Collections.singleton(AkkaType), Collections.emptySet());

  AssemblyContext.TromboneCalculationConfig TestCalculationConfig = new TromboneCalculationConfig(
    95.0, .75, 20.0, -20.0, 4);

  TromboneControlConfig TestControlConfig = new TromboneControlConfig(
    8.0, 90,225.0,200,  1200);

  AssemblyContext TestAssemblyContext = new AssemblyContext(TestAssemblyInfo, TestCalculationConfig, TestControlConfig);

  double maxRDError = focusErrorToRangeError(TestCalculationConfig, TestCalculationConfig.upperFocusLimit);
  double maxRD = zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, testZenithAngles[testZenithAngles.length-1]);
  double maxTotalRD = maxRDError + zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, testZenithAngles[testZenithAngles.length-1]);

  double minRDError = focusErrorToRangeError(TestCalculationConfig, TestCalculationConfig.lowerFocusLimit);
  double minRD = zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, testZenithAngles[0]);
  double minTotalRD = minRDError + zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, testZenithAngles[0]);

  double minReasonableAltitude = 85.0;
  double maxReasonableAltitude = 110.0;
  double minReasonableRange = minReasonableAltitude;
  double maxReasonableRange = 200.0;
  double minReasonableStage = minReasonableAltitude;
  double maxReasonableStage = 200.0;
  double maxReasonableRangeError = 5.0;
  double minReasonableRangeError = -5.0;

  // The Java equivalent of a Scala Pair of doubles
  public static class Pair {
    public final double x, y;

    public Pair(double x, double y) {
      this.x = x;
      this.y = y;
    }
  }

  // These are values for testing the range error based on focus error (fe, fe distance error)
  val feRangeErrorTestValues = testFocusErrors.map(f => (f, focusErrorToRangeError(TestCalculationConfig, f)))

  // This provides "base" range distance based on za only (za, rd)
  val zaRangeDistance = testZenithAngles.map(f => (f, zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, f)))

  // This produces an Vector of data of form (newRangeDistance, newElevation), (newRangeDistance, newElevation),...) as a function of za at a given focus error
  def newRangeAndElData(fe: Double) = testZenithAngles.map(focusZenithAngleToElevationAndRangeDistance(TestCalculationConfig, TestCalculationConfig.defaultInitialElevation, fe, _))

  // This produces an Vector of data of form (newRangeDistance, newElevation), (newRangeDistance, newElevation),...) as a function of za at a given focus error
  def newZARangeAndElData(za: Double) = testFocusErrors.map(focusZenithAngleToElevationAndRangeDistance(TestCalculationConfig, TestCalculationConfig.defaultInitialElevation, _, za))

  type TestValue = ((Double, Double), Int)

  def getza(t: TestValue): Double = t._1._1

  def gettrd(t: TestValue): Double = t._1._2

  def getenc(t: TestValue): Int = t._2

  /**
   * This routine takes the two configs and returns a tuple of ((zenith angle, totalRangeDistance), encoder position)
   * for the range of zenith angle 0 to 60 in steps of 5 degrees
   *
   * @param calculationConfig the assembly calculation config provides algorithm parameters
   * @param controlConfig the control config provides values for mapping stage positions into encoder values
   * @return a Vector of the form (((za, totalRD), enc), ((za, totalRD), enc), ...)
   */
  def calculatedTestData(calculationConfig: TromboneCalculationConfig, controlConfig: TromboneControlConfig, fe: Double) = {

    val rde = focusErrorToRangeError(calculationConfig, fe)

    val rds = testZenithAngles.map(zenithAngleToRangeDistance(calculationConfig.defaultInitialElevation, _) + rde)

    val enc = rds.map(f => stagePositionToEncoder(controlConfig, rangeDistanceToStagePosition(f)))

    val tuples = testZenithAngles.zip(rds).zip(enc)
    tuples
  }

  /**
   * This routine takes the two configs and returns a tuple of ((zenith angle, totalRangeDistance), encoder position)
   * for the range of focus error -20 to 20 in steps of 2 um
   *
   * @param calculationConfig the assembly calculation config provides algorithm parameters
   * @param controlConfig the control config provides values for mapping stage positions into encoder values
   * @return a Vector of the form (((fe, totalRD), enc), ((fe, totalRD), enc), ...)
   */
  def calculatedFETestData(calculationConfig: TromboneCalculationConfig, controlConfig: TromboneControlConfig, elevation: Double, za: Double) = {

    val rangeDistance1 = zenithAngleToRangeDistance(elevation, za)
    val rangeError = testFocusErrors.map(focusErrorToRangeError(calculationConfig, _))
    val totalRangeDistance = rangeError.map(f => f + rangeDistance1)

    val enc = totalRangeDistance.map(f => stagePositionToEncoder(controlConfig, rangeDistanceToStagePosition(f)))

    val tuples = testFocusErrors.zip(totalRangeDistance).zip(enc)
    tuples
  }

  val minStagePos = rangeDistanceToStagePosition(minTotalRD)
  val maxStagePos = rangeDistanceToStagePosition(maxTotalRD)

  // These values take a total el + range to encoder value
  val encoderTestValues: Vector[(Double, Int)] = Vector(
    (80.0, 200),
    (90.0, 225),
    (100.0, 305),
    (110.0, 385),
    (120.0, 465),
    (130.0, 545),
    (140.0, 625),
    (150.0, 705),
    (160.0, 785),
    (170.0, 865),
    (180.0, 945),
    (190.0, 1025)
  )

}
