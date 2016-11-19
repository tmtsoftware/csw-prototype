package csw.examples.vsliceJava.assembly;

import akka.japi.Pair;
import csw.examples.vsliceJava.assembly.AssemblyContext.TromboneCalculationConfig;
import csw.examples.vsliceJava.assembly.AssemblyContext.TromboneControlConfig;
import csw.services.loc.ComponentId;
import csw.services.loc.Connection;
import csw.services.pkg.Component.AssemblyInfo;
import javacsw.services.loc.JComponentType;
import javacsw.services.pkg.JComponent;
import net.logstash.logback.encoder.org.apache.commons.lang.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static javacsw.services.loc.JConnectionType.AkkaType;
import static csw.examples.vsliceJava.assembly.Algorithms.*;
import static javacsw.services.pkg.JComponent.RegisterAndTrackServices;
import static javacsw.util.JUtils.*;

@SuppressWarnings({"unused", "WeakerAccess"})
public class AssemblyTestData {

  static List<Double> testFocusErrors = Arrays.asList(ArrayUtils.toObject(
    new double[]{-20.0, -16.0, -12.0, -8.0, -4.0, 0.0, 4.0, 8.0, 12.0, 16.0, 20.0}));
  static List<Double> testZenithAngles = Arrays.asList(ArrayUtils.toObject(
    new double[]{0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0, 50.0, 55.0, 60.0}));

  static ComponentId hcdId = new ComponentId("lgsTromboneHCD", JComponentType.HCD);

  static AssemblyInfo TestAssemblyInfo = JComponent.assemblyInfo(
    "lgsTrombone",
    "nfiraos.ncc.trombone",
    "csw.examples.vsliceJava.assembly.TromboneAssembly",
    RegisterAndTrackServices, Collections.singleton(AkkaType), Collections.singleton(new Connection.AkkaConnection(hcdId)));

  static TromboneCalculationConfig TestCalculationConfig = new TromboneCalculationConfig(
    95.0, .75, 20.0, -20.0, 4);

  static TromboneControlConfig TestControlConfig = new TromboneControlConfig(
    8.0, 225, 90.0, 200, 1200);

  static AssemblyContext TestAssemblyContext = new AssemblyContext(TestAssemblyInfo, TestCalculationConfig, TestControlConfig);

  static double maxRDError = focusErrorToRangeError(TestCalculationConfig, TestCalculationConfig.upperFocusLimit);
  static double maxRD = zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, testZenithAngles.get(testZenithAngles.size() - 1));
  static double maxTotalRD = maxRDError + zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, testZenithAngles.get(testZenithAngles.size() - 1));

  static double minRDError = focusErrorToRangeError(TestCalculationConfig, TestCalculationConfig.lowerFocusLimit);
  static double minRD = zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, testZenithAngles.get(0));
  static double minTotalRD = minRDError + zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, testZenithAngles.get(0));

  static double minReasonableAltitude = 85.0;
  static double maxReasonableAltitude = 110.0;
  static double minReasonableRange = minReasonableAltitude;
  static double maxReasonableRange = 200.0;
  static double minReasonableStage = minReasonableAltitude;
  static double maxReasonableStage = 200.0;
  static double maxReasonableRangeError = 5.0;
  static double minReasonableRangeError = -5.0;

  // These are values for testing the range error based on focus error (fe, fe distance error)
  static List<Pair<Double, Double>> feRangeErrorTestValues = testFocusErrors.stream().map(f ->
    new Pair<>(f, focusErrorToRangeError(TestCalculationConfig, f)))
    .collect(Collectors.toList());

  // This provides "base" range distance based on za only (za, rd)
  static List<Pair<Double, Double>> zaRangeDistance = testZenithAngles.stream().map(f ->
    new Pair<>(f, zenithAngleToRangeDistance(TestCalculationConfig.defaultInitialElevation, f)))
    .collect(Collectors.toList());

  // This produces an Vector of data of form (newRangeDistance, newElevation), (newRangeDistance, newElevation),...) as a function of za at a given focus error
  static List<Pair<Double, Double>> newRangeAndElData(Double fe) {
    return testZenithAngles.stream().map(f ->
      focusZenithAngleToElevationAndRangeDistance(TestCalculationConfig, TestCalculationConfig.defaultInitialElevation, fe, f))
      .collect(Collectors.toList());
  }

  // This produces an Vector of data of form (newRangeDistance, newElevation), (newRangeDistance, newElevation),...) as a function of za at a given focus error
  static List<Pair<Double, Double>> newZARangeAndElData(Double za) {
    return testFocusErrors.stream().map(f ->
      focusZenithAngleToElevationAndRangeDistance(TestCalculationConfig, TestCalculationConfig.defaultInitialElevation, f, za))
      .collect(Collectors.toList());
  }

  static class TestValue {
    final Pair<Pair<Double, Double>, Integer> v;

    public TestValue(Pair<Pair<Double, Double>, Integer> v) {
      this.v = v;
    }
  }

  static double getza(TestValue t) {
    return t.v.first().first();
  }

  static double gettrd(TestValue t) {
    return t.v.first().second();
  }

  static int getenc(TestValue t) {
    return t.v.second();
  }


  /**
   * This routine takes the two configs and returns a tuple of ((zenith angle, totalRangeDistance), encoder position)
   * for the range of zenith angle 0 to 60 in steps of 5 degrees
   *
   * @param calculationConfig the assembly calculation config provides algorithm parameters
   * @param controlConfig     the control config provides values for mapping stage positions into encoder values
   * @return a Vector of the form (((za, totalRD), enc), ((za, totalRD), enc), ...)
   */
  static List<TestValue> calculatedTestData(TromboneCalculationConfig calculationConfig, TromboneControlConfig controlConfig, double fe) {

    double rde = focusErrorToRangeError(calculationConfig, fe);

    List<Double> rds = testZenithAngles.stream().map(f -> zenithAngleToRangeDistance(calculationConfig.defaultInitialElevation, f) + rde).
      collect(Collectors.toList());

    List<Integer> enc = rds.stream().map(f -> stagePositionToEncoder(controlConfig, rangeDistanceToStagePosition(f))).
      collect(Collectors.toList());

    List<Pair<Pair<Double, Double>, Integer>> tuples = zip(zip(testZenithAngles, rds), enc);
    return tuples.stream().map(TestValue::new).collect(Collectors.toList());
  }

  /**
   * This routine takes the two configs and returns a tuple of ((zenith angle, totalRangeDistance), encoder position)
   * for the range of focus error -20 to 20 in steps of 2 um
   *
   * @param calculationConfig the assembly calculation config provides algorithm parameters
   * @param controlConfig     the control config provides values for mapping stage positions into encoder values
   * @return a Vector of the form (((fe, totalRD), enc), ((fe, totalRD), enc), ...)
   */
  static List<TestValue> calculatedFETestData(TromboneCalculationConfig calculationConfig, TromboneControlConfig controlConfig, Double elevation, Double za) {

    double rangeDistance1 = zenithAngleToRangeDistance(elevation, za);
    List<Double> rangeError = testFocusErrors.stream().map(f -> focusErrorToRangeError(calculationConfig, f)).collect(Collectors.toList());
    List<Double> totalRangeDistance = rangeError.stream().map(f -> f + rangeDistance1).collect(Collectors.toList());

    List<Integer> enc = totalRangeDistance.stream().map(f -> stagePositionToEncoder(controlConfig, rangeDistanceToStagePosition(f))).collect(Collectors.toList());

    List<Pair<Pair<Double, Double>, Integer>> tuples = zip(zip(testFocusErrors, totalRangeDistance), enc);
    return tuples.stream().map(TestValue::new).collect(Collectors.toList());
  }

  static final double minStagePos = rangeDistanceToStagePosition(minTotalRD);
  static final double maxStagePos = rangeDistanceToStagePosition(maxTotalRD);

  // These values take a total el + range to encoder value
  static final List<Pair<Double, Integer>> encoderTestValues = new ArrayList<>();

  static {
    encoderTestValues.add(new Pair<>(80.0, 200));
    encoderTestValues.add(new Pair<>(90.0, 225));
    encoderTestValues.add(new Pair<>(100.0, 305));
    encoderTestValues.add(new Pair<>(110.0, 385));
    encoderTestValues.add(new Pair<>(120.0, 465));
    encoderTestValues.add(new Pair<>(130.0, 545));
    encoderTestValues.add(new Pair<>(140.0, 625));
    encoderTestValues.add(new Pair<>(150.0, 705));
    encoderTestValues.add(new Pair<>(160.0, 785));
    encoderTestValues.add(new Pair<>(170.0, 865));
    encoderTestValues.add(new Pair<>(180.0, 945));
    encoderTestValues.add(new Pair<>(190.0, 1025));
  }
}
