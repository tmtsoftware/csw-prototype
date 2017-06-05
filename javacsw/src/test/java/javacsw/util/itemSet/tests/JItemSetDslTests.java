package javacsw.util.itemSet.tests;

import csw.util.itemSet.*;
import csw.util.itemSet.ItemSets.Observe;
import csw.util.itemSet.ItemSets.Setup;
import org.junit.Test;

import java.util.*;

import static javacsw.util.itemSet.JItems.*;
import static javacsw.util.itemSet.JConfigDSL.*;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Tests the Java API to the config classes
 */
@SuppressWarnings({"OptionalGetWithoutIsPresent", "unused"})
public class JItemSetDslTests {
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

  private static final ItemSets.ItemSetInfo info = new ItemSets.ItemSetInfo(new ObsId("Obs001"));

  @Test
  public void testSetupConfig() {
    Setup sc1 = jadd(sc(info, ck3), jset(k1, 22), jset(k2, 44));
    assertTrue(sc1.size() == 2);
    assertTrue(sc1.exists(k1));
    assertTrue(sc1.exists(k2));
    assertTrue(jvalue(jitem(sc1, k1)).equals(22));
    assertEquals(jvalues(jitem(sc1, k2)), Collections.singletonList(44));
  }

  @Test
  public void testSetupConfigWithMatrix() {
    double[][] m1 = {{1., 2., 3.}, {4., 5., 6.}, {7., 8., 9.}};
    DoubleMatrix dm1 = DoubleMatrix(m1);
    Setup sc1 = jadd(sc(info, ck3), jset(k4, dm1));
    assertTrue(sc1.size() == 1);
    assertTrue(sc1.exists(k4));
    assertEquals(jvalue(jitem(sc1, k4)), dm1);
    assertTrue(jvalue(jitem(sc1, k4)).data()[0][0] == 1);
    assertTrue(jvalue(jitem(sc1, k4)).apply(0, 0) == 1);
  }

  @Test
  public void testObserveConfig() {
    Observe oc1 = jadd(oc(info, ck3), jset(k1, 22), jset(k2, 44));
    assertTrue(oc1.size() == 2);
    assertTrue(oc1.exists(k1));
    assertTrue(oc1.exists(k2));
    assertTrue(jvalue(jitem(oc1, k1)).equals(22));
    assertEquals(jvalues(jitem(oc1, k2)), Collections.singletonList(44));
  }
}

