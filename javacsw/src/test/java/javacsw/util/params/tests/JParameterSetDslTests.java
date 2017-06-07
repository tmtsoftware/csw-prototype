package javacsw.util.params.tests;

import csw.util.param.*;
import csw.util.param.Parameters.Observe;
import csw.util.param.Parameters.Setup;
import csw.util.param.Parameters.CommandInfo;
import org.junit.Test;

import java.util.*;

import static javacsw.util.params.JParameters.*;
import static javacsw.util.params.JParameterSetDSL.*;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Tests the Java API to the config classes
 */
@SuppressWarnings({"OptionalGetWithoutIsPresent", "unused"})
public class JParameterSetDslTests {
  private static final String s1 = "encoder";
  private static final String s2 = "filter";
  private static final String s3 = "detectorTemp";

  private static final String ck = "wfos.blue.filter";
  private static final String ck1 = "wfos.prog.cloudcover";
  private static final String ck2 = "wfos.red.filter";
  private static final String ck3 = "wfos.red.detector";

  private static final IntKey k1 = IntKey("encoder");
  private static final IntKey k2 = IntKey("windspeed");
  private static final DoubleMatrixKey k4 = DoubleMatrixKey("matrixTest");

  private static final Parameters.CommandInfo info = new CommandInfo("Obs001");

  @Test
  public void testSetup() {
    Setup sc1 = jadd(setup(info, ck3), jset(k1, 22), jset(k2, 44));
    assertTrue(sc1.size() == 2);
    assertTrue(sc1.exists(k1));
    assertTrue(sc1.exists(k2));
    assertTrue(jvalue(jitem(sc1, k1)).equals(22));
    assertEquals(jvalues(jitem(sc1, k2)), Collections.singletonList(44));
  }

  @Test
  public void testSetupWithMatrix() {
    double[][] m1 = {{1., 2., 3.}, {4., 5., 6.}, {7., 8., 9.}};
    DoubleMatrix dm1 = DoubleMatrix(m1);
    Setup sc1 = jadd(setup(info, ck3), jset(k4, dm1));
    assertTrue(sc1.size() == 1);
    assertTrue(sc1.exists(k4));
    assertEquals(jvalue(jitem(sc1, k4)), dm1);
    assertTrue(jvalue(jitem(sc1, k4)).data()[0][0] == 1);
    assertTrue(jvalue(jitem(sc1, k4)).apply(0, 0) == 1);
  }

  @Test
  public void testObserve() {
    Observe oc1 = jadd(observe(info, ck3), jset(k1, 22), jset(k2, 44));
    assertTrue(oc1.size() == 2);
    assertTrue(oc1.exists(k1));
    assertTrue(oc1.exists(k2));
    assertTrue(jvalue(jitem(oc1, k1)).equals(22));
    assertEquals(jvalues(jitem(oc1, k2)), Collections.singletonList(44));
  }
}

