package csw.examples.vsliceJava.assembly;

import akka.japi.Pair;
import net.logstash.logback.encoder.org.apache.commons.lang.ArrayUtils;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static csw.examples.vsliceJava.assembly.Algorithms.*;
import static csw.examples.vsliceJava.assembly.AssemblyContext.focusErrorKey;
import static csw.examples.vsliceJava.assembly.AssemblyContext.zenithAngleKey;
import static csw.examples.vsliceJava.assembly.AssemblyTestData.*;
import static javacsw.util.JUtils.zip;
import static javacsw.util.config.JItems.jset;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;


/**
 * These are tests of the calculations in the Calculation Actor
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class AlgorithmTests {

  static final List<Double> testFocusError = Arrays.asList(ArrayUtils.toObject(
    new double[]{-20.0, -16.0, -12.0, -8.0, -4.0, 0.0, 4.0, 8.0, 12.0, 16.0, 20.0}));

  static final AssemblyContext.TromboneCalculationConfig calculationConfig = TestCalculationConfig;
  static final AssemblyContext.TromboneControlConfig controlConfig = TestControlConfig;

  static final double initialElevation = calculationConfig.defaultInitialElevation;

  // default margin of error
  static final double delta = 0.0001;

  // --- Testing the algorithms for correctness without actor ---

  @Test
  public void shouldProduceGoodRangeDistanceError() {
    List<Double> rde = testFocusError.stream().map(f -> focusErrorToRangeError(calculationConfig, f))
      .collect(Collectors.toList());
    // Verify reasonableness
    // range distances should be the same at 0 za
    assertTrue(rde.get(0) > minReasonableRangeError);
    assertTrue(rde.get(rde.size() - 1) < maxReasonableRangeError);
  }

  double round(double din) {
    return Math.rint(din * 100) / 100;
  }

  @Test
  public void shouldHaveGoodZenithAngleToRangeDistance() {
    double testElevation = calculationConfig.defaultInitialElevation;

    List<Double> rds = testZenithAngles.stream().map(f -> zenithAngleToRangeDistance(testElevation, f))
      .collect(Collectors.toList());

    // Verify reasonableness
    // range distances should be the same at 0 za
    assertTrue(rds.get(0) > minTotalRD);
    assertTrue(rds.get(rds.size() - 1) < maxTotalRD);
  }

  // This is the approach used to find the new elevation after adding the range error
  @Test
  public void shouldWorkWithElevationToo() {
    double testElevation = calculationConfig.defaultInitialElevation;

    List<Double> rds = testZenithAngles.stream().map(f -> zenithAngleToRangeDistance(testElevation, f))
      .collect(Collectors.toList());

    List<Pair<Double, Double>> in = zip(testZenithAngles, rds);

    List<Double> els = in.stream().map(f -> rangeDistanceToElevation(f.second(), f.first())).map(this::round)
      .collect(Collectors.toList());

    els.forEach(f -> assertEquals(f, testElevation, delta));
  }

  @Test
  public void shouldWorkWithTheNextValuesGeneration() {
    // First change the zenith angle with a fixed fe so range distance = elevation
    double fe1 = 0.0;
    List<Pair<Double, Double>> p1 = testZenithAngles.stream().map(f ->
      focusZenithAngleToElevationAndRangeDistance(calculationConfig, calculationConfig.defaultInitialElevation, fe1, f))
      .collect(Collectors.toList());

    //info("P1: " + p1)
    assertEquals(p1.get(0).first(), p1.get(0).second());
    assertEquals(p1.get(0).first(), calculationConfig.defaultInitialElevation, delta);
    assertEquals(p1.get(p1.size() - 1).first(), maxRD, delta);
    assertEquals(p1.get(p1.size() - 1).second(), calculationConfig.defaultInitialElevation, delta);

    double fe2 = calculationConfig.lowerFocusLimit;
    List<Pair<Double, Double>> p2 = testZenithAngles.stream().map(f ->
      focusZenithAngleToElevationAndRangeDistance(calculationConfig, calculationConfig.defaultInitialElevation, fe2, f))
      .collect(Collectors.toList());

    //info("P2: " + p2)
    assertEquals(p2.get(0).first(), p2.get(0).second(), delta);
    assertEquals(p2.get(0).first(), calculationConfig.defaultInitialElevation + minRDError, delta);

    double fe3 = calculationConfig.upperFocusLimit;
    List<Pair<Double, Double>> p3 = testZenithAngles.stream().map(f ->
      focusZenithAngleToElevationAndRangeDistance(calculationConfig, calculationConfig.defaultInitialElevation, fe3, f))
      .collect(Collectors.toList());

    //info("P3: " + p3)
    assertEquals(p3.get(0).first(), p3.get(0).second(), delta);
    assertEquals(p3.get(0).first(), calculationConfig.defaultInitialElevation + maxRDError, delta);
  }

  @Test
  public void shouldVerifyFocusErrorValues() {
    assertTrue(verifyFocusError(TestCalculationConfig, jset(focusErrorKey, 0.0)));
    assertTrue(verifyFocusError(TestCalculationConfig, jset(focusErrorKey, 15.0)));
    assertFalse(verifyFocusError(TestCalculationConfig, jset(focusErrorKey, -21.0)));
    assertFalse(verifyFocusError(TestCalculationConfig, jset(focusErrorKey, 41.0)));
  }

  @Test
  public void shouldVerifyZenithAngleValues() {
    assertTrue(verifyZenithAngle(jset(zenithAngleKey, 0.0)));
    assertFalse(verifyZenithAngle(jset(zenithAngleKey, -1.0)));
    assertFalse(verifyZenithAngle(jset(zenithAngleKey, 92.0)));
  }

  @Test
  public void shouldTakeARangeDistanceAndProvideAReasonableStagePosition() {
    double minStage = rangeDistanceToStagePosition(minRD);
    double maxStage = rangeDistanceToStagePosition(maxRD);

    assertTrue(minStage > minReasonableStage);
    assertTrue(maxStage < maxReasonableStage);
  }

  @Test
  public void shouldProvideAReasonableEncoderValue() {
    double minStage = rangeDistanceToStagePosition(minRD);
    double maxStage = rangeDistanceToStagePosition(maxRD);

    int minEncoder = stagePositionToEncoder(controlConfig, minStage);
    int maxEncoder = stagePositionToEncoder(controlConfig, maxStage);

    System.out.println("minStage/maxStage: " + minStage + "/" + maxStage);
    System.out.println("minEnc/maxEnc: " + minEncoder + "/" + maxEncoder);
    System.out.println("zero: " + stagePositionToEncoder(controlConfig, 0.0));

    assertTrue(minEncoder > controlConfig.minEncoderLimit);
    assertTrue(maxEncoder < controlConfig.maxEncoderLimit);
  }

  @Test
  public void shouldGiveGoodData() {
    System.out.println("test1: " + feRangeErrorTestValues);
    System.out.println("test2: " + zaRangeDistance);

    System.out.println("tst3: " + newRangeAndElData(-20.0));

    System.out.println("Test4: " + calculatedTestData(calculationConfig, controlConfig, -20.0));
  }

}

