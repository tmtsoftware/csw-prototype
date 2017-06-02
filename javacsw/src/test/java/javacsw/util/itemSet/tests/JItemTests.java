package javacsw.util.itemSet.tests;

import csw.util.itemSet.*;
import javacsw.util.itemSet.JItems;
import javacsw.util.itemSet.JUnitsOfMeasure;
import org.junit.Test;

import java.util.*;

import static javacsw.util.itemSet.JItems.*;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests the Java API to the config classes
 */
@SuppressWarnings({"OptionalGetWithoutIsPresent", "unused"})
public class JItemTests {
  private static final String s1 = "encoder";
  private static final String s2 = "filter";
  private static final String s3 = "detectorTemp";

  private static final String ck = "wfos.blue.filter";
  private static final String ck1 = "wfos.prog.cloudcover";
  private static final String ck2 = "wfos.red.filter";
  private static final String ck3 = "wfos.red.detector";

  // @SuppressWarnings("EqualsBetweenInconvertibleTypes")

  @Test
  public void basicKeyTests() {
    // Should be constructed properly
    IntKey k1 = IntKey(s1);
    IntKey k2 = IntKey(s2);
    assertTrue(Objects.equals(k1.keyName(), s1));

    // Should support equality of keys
    IntKey k3 = IntKey(s1);
    assertEquals(k3, k1);
    assertNotEquals(k3, k2);
    assertNotEquals(k1, k2);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testBooleanItem() {
    // should allow setting from boolean
    Boolean bval = false;
    BooleanKey bk = BooleanKey(s1);
    BooleanItem bi = jset(bk, bval);
    assertEquals(jvalues(bi), Collections.singletonList(bval));
    assertFalse(jvalue(bi));
    assertEquals(jget(bi, 0).get(), false);

    List<Boolean> bval2 = Arrays.asList(false, true);
    BooleanItem bi2 = jset(bk, bval2).withUnits(JUnitsOfMeasure.degrees);
    assertEquals(bi2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(bi2, 1), bval2.get(1));
    assertEquals(jvalues(bi2), bval2);

    bi2 = jset(bk, bval2, JUnitsOfMeasure.degrees);
    assertSame(bi2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(bi2, 1), bval2.get(1));
    assertEquals(jvalues(bi2), bval2);

    BooleanItem bi3 = jset(bk, true, false).withUnits(JUnitsOfMeasure.degrees);
    assertSame(bi3.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalues(bi3), Arrays.asList(true, false));
  }

  @Test
  public void testByteArrayItem() {
    byte[] a1 = {1, 2, 3, 4, 5};
    byte[] a2 = {10, 20, 30, 40, 50};

    ByteArray ba1 = ByteArray(a1);
    ByteArray ba2 = ByteArray(a2);
    ByteArrayKey bk = ByteArrayKey(s1);

    // Test singel item
    ByteArrayItem di = jset(bk, ba1);
    assertEquals(jvalues(di), Collections.singletonList(ba1));
    assertEquals(jvalue(di), ba1);
    assertEquals(jget(di, 0).get(), ba1);

    List<ByteArray> listIn = Arrays.asList(ba1, ba2);

    // Test with list, withUnits
    ByteArrayItem di2 = jset(bk, listIn).withUnits(JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test with list and units
    di2 = jset(bk, listIn, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test using one array without and with units
    di2 = jset(bk, a1);
    assertEquals(jvalue(di2), ba1);
    di2 = jset(bk, a2, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2), ba2);

    // Test with varargs
    ByteArrayItem di3 = jset(bk, ba1, ba2);
    assertEquals(jvalue(di3, 1), ba2);
    assertEquals(jvalues(di3), listIn);

    byte[] a = {1, 2, 3};
    byte[] b = {10, 11, 12};
    byte[] c = {100, 101, 102};

    ByteArrayItem di4 = jset(bk, a, b, c).withUnits(JUnitsOfMeasure.meters);
    assertSame(di4.units(), JUnitsOfMeasure.meters);
    assertEquals(jvalues(di4).size(), 3);
    assertEquals(jvalue(di4, 2), ByteArray(c));
  }

  @Test
  public void testByteMatrixItem() {

    byte[][] m1 = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    byte[][] m2 = {{1, 2, 3, 4, 5}, {10, 20, 30, 40, 50}};

    ByteMatrix bm1 = ByteMatrix(m1);
    ByteMatrix bm2 = ByteMatrix(m2);
    ByteMatrixKey bk = ByteMatrixKey(s1);

    // Test with single item
    ByteMatrixItem di = jset(bk, bm1);
    assertEquals(jvalues(di), Collections.singletonList(bm1));
    assertEquals(jvalue(di), bm1);
    assertEquals(jget(di, 0).get(), bm1);

    List<ByteMatrix> listIn = Arrays.asList(bm1, bm2);

    // Test with list, withUnits
    ByteMatrixItem di2 = jset(bk, listIn).withUnits(JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test with list, units
    di2 = jset(bk, listIn, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test using one matrix without and with units
    di2 = jset(bk, m1);
    assertEquals(jvalue(di2), bm1);
    di2 = jset(bk, m2, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2), bm2);

    // Test using varargs as matrix
    ByteMatrixItem di3 = jset(bk, bm1, bm2).withUnits(JUnitsOfMeasure.seconds);
    assertSame(di3.units(), JUnitsOfMeasure.seconds);
    assertEquals(jvalue(di3, 1), bm2);
    assertEquals(jvalues(di3), listIn);

    // Test using varargs as arrays
    ByteMatrixItem di4 = jset(bk, m1, m2).withUnits(JUnitsOfMeasure.meters);
    assertSame(di4.units(), JUnitsOfMeasure.meters);
    assertEquals(jvalue(di4, 0), bm1);
    assertEquals(jvalues(di4), listIn);
  }

  @Test
  public void testCharItem() {
    // should allow setting from char
    char cval = 'K';
    CharKey ck = CharKey(s1);
    CharItem ci = jset(ck, cval);
    assertEquals(jvalues(ci), Collections.singletonList(cval));
    assertTrue(jvalue(ci) == cval);
    assertTrue(jget(ci, 0).get() == cval);

    List<Character> listIn = Arrays.asList('K', 'G');
    // Test with list, withUnits
    CharItem ci2 = jset(ck, listIn).withUnits(JUnitsOfMeasure.degrees);
    assertSame(ci2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(ci2, 1), listIn.get(1));
    assertEquals(jvalues(ci2), listIn);

    // Test with units
    ci2 = jset(ck, listIn, JUnitsOfMeasure.degrees);
    assertSame(ci2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(ci2, 1), listIn.get(1));
    assertEquals(jvalues(ci2), listIn);
  }

  @Test
  public void testDoubleKey() {
    double dval = 123.456;
    DoubleKey dk = DoubleKey(s1);

    // Test single item
    DoubleItem di = jset(dk, dval);
    assertEquals(jvalues(di), Collections.singletonList(dval));
    assertTrue(jvalue(di) == dval);
    assertTrue(jget(di, 0).get() == dval);

    List<Double> dval2 = Arrays.asList(123.0, 456.0);
    // Test with list, withUnits
    DoubleItem di2 = jset(dk, dval2).withUnits(JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), dval2.get(1));
    assertEquals(jvalues(di2), dval2);

    // Test with List, units
    di2 = jset(dk, dval2, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), dval2.get(1));
    assertEquals(jvalues(di2), dval2);
  }

  @Test
  public void testDoubleArrayKey() {
    double[] a1 = {1., 2., 3., 4., 5.};
    double[] a2 = {10., 20., 30., 40., 50.};

    DoubleArray da1 = DoubleArray(a1);
    DoubleArray da2 = DoubleArray(a2);
    DoubleArrayKey dk = DoubleArrayKey(s1);

    // Test single item
    DoubleArrayItem di = jset(dk, da1);
    assertEquals(jvalues(di), Collections.singletonList(da1));
    assertEquals(jvalue(di), da1);
    assertEquals(jget(di, 0).get(), da1);

    List<DoubleArray> listIn = Arrays.asList(da1, da2);

    // Test with list, withUnits
    DoubleArrayItem di2 = jset(dk, listIn).withUnits(JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test with list and units
    di2 = jset(dk, listIn, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test using one array without and with units
    di2 = jset(dk, a1);
    assertEquals(jvalue(di2), da1);
    di2 = jset(dk, a2, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2), da2);

    // Test with var args
    DoubleArrayItem di3 = jset(dk, da1, da2);
    assertEquals(jvalue(di3, 1), da2);
    assertEquals(jvalues(di3), listIn);

    double[] a = {1, 2, 3};
    double[] b = {10, 20, 30};
    double[] c = {100, 200, 300};

    DoubleArrayItem di4 = jset(dk, a, b, c).withUnits(JUnitsOfMeasure.meters);
    assertEquals(jvalues(di4).size(), 3);
    assertEquals(jvalue(di4, 2), DoubleArray(c));
  }

  @Test
  public void testDoubleMatrixKey() {
    double[][] m1 = {{1., 2., 3.}, {4., 5., 6.}, {7., 8., 9.}};
    double[][] m2 = {{1., 2., 3., 4., 5.}, {10., 20., 30., 40., 50.}};

    DoubleMatrix dm1 = DoubleMatrix(m1);
    DoubleMatrix dm2 = DoubleMatrix(m2);
    DoubleMatrixKey dk = DoubleMatrixKey(s1);

    // Test with single item
    DoubleMatrixItem di = jset(dk, dm1);
    assertEquals(jvalues(di), Collections.singletonList(dm1));
    assertEquals(jvalue(di), dm1);
    assertEquals(jget(di, 0).get(), dm1);

    // Test with list, withUnits
    List<DoubleMatrix> listIn = Arrays.asList(dm1, dm2);
    DoubleMatrixItem di2 = jset(dk, listIn).withUnits(JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test with list, units
    di2 = jset(dk, listIn, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test using one matrix without and with units
    di2 = jset(dk, m1);
    assertEquals(jvalue(di2), dm1);
    di2 = jset(dk, m2, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2), dm2);

    // Test using varargs as matrix
    DoubleMatrixItem di3 = jset(dk, dm1, dm2).withUnits(JUnitsOfMeasure.seconds);
    assertSame(di3.units(), JUnitsOfMeasure.seconds);
    assertEquals(jvalue(di3, 1), dm2);
    assertEquals(jvalues(di3), listIn);

    // Test using varargs as arrays
    DoubleMatrixItem di4 = jset(dk, m1, m2).withUnits(JUnitsOfMeasure.meters);
    assertSame(di4.units(), JUnitsOfMeasure.meters);
    assertEquals(jvalue(di4, 0), dm1);
    assertEquals(jvalues(di4), listIn);
  }

  @Test
  public void testFloatKey() {
    float fval = 123.456f;
    float fval1 = 6543.21f;
    FloatKey fk = FloatKey(s1);

    // Test single item
    FloatItem fi = jset(fk, fval);
    assertEquals(jvalues(fi), Collections.singletonList(fval));
    assertTrue(jvalue(fi) == fval);
    assertTrue(jget(fi, 0).get() == fval);

    // Test with varargs floats
    fi = jset(fk, fval, fval1);
    assertEquals(jvalues(fi), Arrays.asList(fval, fval1));

    // Test list, withUnits
    List<Float> fval2 = Arrays.asList(123.0f, 456.0f);
    FloatItem fi2 = jset(fk, fval2).withUnits(JUnitsOfMeasure.degrees);
    assertSame(fi2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(fi2, 1), fval2.get(1));
    assertEquals(jvalues(fi2), fval2);

    // Test list, withUnits
    fi2 = jset(fk, fval2, JUnitsOfMeasure.degrees);
    assertSame(fi2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(fi2, 1), fval2.get(1));
    assertEquals(jvalues(fi2), fval2);
  }

  @Test
  public void testFloatArrayKey() {
    float[] a1 = {1.f, 2.f, 3.f, 4.f, 5.f};
    float[] a2 = {10.f, 20.f, 30.f, 40.f, 50.f};

    FloatArray fa1 = FloatArray(a1);
    FloatArray fa2 = FloatArray(a2);
    FloatArrayKey fk = FloatArrayKey(s1);

    // Test single item
    FloatArrayItem di = jset(fk, fa1);
    assertEquals(jvalues(di), Collections.singletonList(fa1));
    assertEquals(jvalue(di), fa1);
    assertEquals(jget(di, 0).get(), fa1);

    List<FloatArray> listIn = Arrays.asList(fa1, fa2);

    // Test list, withUnits
    FloatArrayItem di2 = jset(fk, listIn).withUnits(JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test with list, units
    di2 = jset(fk, listIn, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test using one array without and with units
    di2 = jset(fk, a1);
    assertEquals(jvalue(di2), fa1);
    di2 = jset(fk, a2, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2), fa2);

    // Test with varargs
    FloatArrayItem di3 = jset(fk, fa1, fa2);
    assertEquals(jvalue(di3, 1), fa2);
    assertEquals(jvalues(di3), listIn);

    // Test with float arrays
    float[] a = {1f, 2f, 3f};
    float[] b = {10f, 20f, 30f};
    float[] c = {100f, 200f, 300f};

    FloatArrayItem di4 = jset(fk, a, b, c).withUnits(JUnitsOfMeasure.meters);
    assertEquals(jvalues(di4).size(), 3);
    assertEquals(jvalue(di4, 2), FloatArray(c));
  }

  @Test
  public void testFloatMatrixKey() {

    float[][] m1 = {{1.f, 2.f, 3.f}, {4.f, 5.f, 6.f}, {7.f, 8.f, 9.f}};
    float[][] m2 = {{1.f, 2.f, 3.f, 4.f, 5.f}, {10.f, 20.f, 30.f, 40.f, 50.f}};

    FloatMatrix fm1 = FloatMatrix(m1);
    FloatMatrix fm2 = FloatMatrix(m2);
    FloatMatrixKey dk = FloatMatrixKey(s1);

    // Test with single item
    FloatMatrixItem di = jset(dk, fm1);
    assertEquals(jvalues(di), Collections.singletonList(fm1));
    assertEquals(jvalue(di), fm1);
    assertEquals(jget(di, 0).get(), fm1);

    // Test with list, withUnits
    List<FloatMatrix> listIn = Arrays.asList(fm1, fm2);
    FloatMatrixItem di2 = jset(dk, listIn).withUnits(JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test with list, units
    di2 = jset(dk, listIn, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test using one matrix without and with units
    di2 = jset(dk, m1);
    assertEquals(jvalue(di2), fm1);
    di2 = jset(dk, m2, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2), fm2);

    // Test using varargs as matrix
    FloatMatrixItem di3 = jset(dk, fm1, fm2).withUnits(JUnitsOfMeasure.seconds);
    assertSame(di3.units(), JUnitsOfMeasure.seconds);
    assertEquals(jvalue(di3, 1), fm2);
    assertEquals(jvalues(di3), listIn);

    // Test using varargs as arrays
    FloatMatrixItem di4 = jset(dk, m1, m2).withUnits(JUnitsOfMeasure.meters);
    assertSame(di4.units(), JUnitsOfMeasure.meters);
    assertEquals(jvalue(di4, 0), fm1);
    assertEquals(jvalues(di4), listIn);
  }

  @Test
  public void testIntKey() {
    // should allow setting from int
    int ival = 1234;
    IntKey ik = IntKey(s1);
    IntItem ii = jset(ik, ival);
    assertTrue(jvalues(ii).equals(Collections.singletonList(ival)));
    assertTrue(jvalue(ii) == ival);
    assertTrue(jget(ii, 0).get() == ival);

    List<Integer> ival2 = Arrays.asList(123, 456);
    IntItem ii2 = jset(ik, ival2, JUnitsOfMeasure.degrees);
    assertTrue(ii2.units().equals(JUnitsOfMeasure.degrees));
    assertTrue(jvalue(ii2, 1).equals(ival2.get(1)));
    assertTrue(jvalues(ii2).equals(ival2));
  }

  @Test
  public void testStaticJavaIntItem() {
    IntKey encoder1 = new IntKey("encoder1");

    IntItem a = jset(encoder1, Arrays.asList(1, 2, 3));

    Integer v = jvalue(a);
    assertTrue(v.equals(1));

    v = jvalue(a, 0);
    assert (v == 1);
    v = jvalue(a, 1);
    assertTrue(v == 2);
    v = jvalue(a, 2);
    assertTrue(v == 3);

    Optional<Integer> ov = jget(a, 2);
    assertTrue(ov.get() == 3);

    List<Integer> lv = jvalues(a);
    assertEquals(lv, Arrays.asList(1, 2, 3));

    IntItem b = JItems.jset(encoder1, 100, 200);
    assertEquals(jvalues(b), Arrays.asList(100, 200));

    b = JItems.jset(encoder1, Arrays.asList(100, 200), JUnitsOfMeasure.degrees);
    assertEquals(jvalues(b), Arrays.asList(100, 200));
  }

  @Test
  public void testIntArrayKey() {
    int[] a1 = {1, 2, 3, 4, 5};
    int[] a2 = {10, 20, 30, 40, 50};

    IntArray ia1 = IntArray(a1);
    IntArray ia2 = IntArray(a2);
    IntArrayKey ak = IntArrayKey(s1);

    // Test single item
    IntArrayItem di = jset(ak, ia1);
    assertEquals(jvalues(di), Collections.singletonList(ia1));
    assertEquals(jvalue(di), ia1);
    assertEquals(jget(di, 0).get(), ia1);

    // Test with list, withUnits
    List<IntArray> listIn = Arrays.asList(ia1, ia2);

    // Test with list, withUnits
    IntArrayItem di2 = jset(ak, listIn).withUnits(JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test with list, with units
    di2 = jset(ak, listIn, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test using one array without and with units
    di2 = jset(ak, a1);
    assertEquals(jvalue(di2), ia1);
    di2 = jset(ak, a2, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2), ia2);

    // test using var args
    IntArrayItem ia3 = jset(ak, ia1, ia2);
    assertEquals(jvalue(ia3, 1), ia2);
    assertEquals(jvalues(ia3), listIn);

    // Test using ints
    int[] a = {1, 2, 3};
    int[] b = {10, 20, 30};
    int[] c = {100, 200, 300};

    IntArrayItem di4 = jset(ak, a, b, c).withUnits(JUnitsOfMeasure.meters);
    assertTrue(jvalues(di4).size() == 3);
    assertEquals(jvalue(di4, 2), IntArray(c));
  }

  @Test
  public void testIntMatrixKey() {

    int[][] m1 = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    int[][] m2 = {{1, 2, 3, 4, 5}, {10, 20, 30, 40, 50}};

    IntMatrix im1 = IntMatrix(m1);
    IntMatrix im2 = IntMatrix(m2);
    IntMatrixKey dk = IntMatrixKey(s1);

    // Test with single item
    IntMatrixItem di = jset(dk, im1);
    assertEquals(jvalues(di), Collections.singletonList(im1));
    assertEquals(jvalue(di), im1);
    assertEquals(jget(di, 0).get(), im1);

    // Test with list, withUnits
    List<IntMatrix> listIn = Arrays.asList(im1, im2);
    IntMatrixItem di2 = jset(dk, listIn).withUnits(JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test with list, units
    di2 = jset(dk, listIn, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test using one matrix without and with units
    di2 = jset(dk, m1);
    assertEquals(jvalue(di2), im1);
    di2 = jset(dk, m2, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2), im2);

    // Test using varargs as matrix
    IntMatrixItem di3 = jset(dk, im1, im2).withUnits(JUnitsOfMeasure.seconds);
    assertSame(di3.units(), JUnitsOfMeasure.seconds);
    assertEquals(jvalue(di3, 1), im2);
    assertEquals(jvalues(di3), listIn);

    // Test using varargs as arrays
    IntMatrixItem di4 = jset(dk, m1, m2).withUnits(JUnitsOfMeasure.meters);
    assertSame(di4.units(), JUnitsOfMeasure.meters);
    assertEquals(jvalue(di4, 0), im1);
    assertEquals(jvalues(di4), listIn);
  }

  @Test
  public void testLongKey() {
    // should allow setting from Long
    long lval = 1234L;
    LongKey lk = LongKey(s1);

    // Test single val
    LongItem li = jset(lk, lval);
    assertEquals(jvalues(li), Collections.singletonList(lval));
    assertTrue(jvalue(li) == lval);
    assertTrue(jget(li, 0).get() == lval);

    List<Long> lval2 = Arrays.asList(123L, 456L);
    // Test with list, withUnits
    LongItem li2 = jset(lk, lval2).withUnits(JUnitsOfMeasure.degrees);
    assertSame(li2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(li2, 1), lval2.get(1));
    assertEquals(jvalues(li2), lval2);

    // test with list, units
    li2 = jset(lk, lval2, JUnitsOfMeasure.degrees);
    assertSame(li2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(li2, 1), lval2.get(1));
    assertEquals(jvalues(li2), lval2);
  }

  @Test
  public void testLongArrayKey() {
    long[] a1 = {1, 2, 3, 4, 5};
    long[] a2 = {10, 20, 30, 40, 50};

    LongArray la1 = LongArray(a1);
    LongArray la2 = LongArray(a2);
    LongArrayKey lk = LongArrayKey(s1);

    // Test single item
    LongArrayItem di = jset(lk, la1);
    assertEquals(jvalues(di), Collections.singletonList(la1));
    assertEquals(jvalue(di), la1);
    assertEquals(jget(di, 0).get(), la1);

    List<LongArray> listIn = Arrays.asList(la1, la2);

    // Test with list, withUnits
    LongArrayItem li2 = jset(lk, listIn).withUnits(JUnitsOfMeasure.degrees);
    assertSame(li2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(li2, 1), listIn.get(1));
    assertEquals(jvalues(li2), listIn);

    // Test with list, with units
    li2 = jset(lk, listIn, JUnitsOfMeasure.degrees);
    assertEquals(li2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(li2, 1), listIn.get(1));
    assertEquals(jvalues(li2), listIn);

    // Test using one array without and with units
    li2 = jset(lk, a1);
    assertEquals(jvalue(li2), la1);
    li2 = jset(lk, a2, JUnitsOfMeasure.degrees);
    assertEquals(li2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(li2), la2);

    // test using var args
    LongArrayItem li3 = jset(lk, la1, la2);
    assertEquals(jvalue(li3, 1), la2);
    assertEquals(jvalues(li3), listIn);

    // Test using ints
    long[] a = {1, 2, 3};
    long[] b = {10, 20, 30};
    long[] c = {100, 200, 300};

    LongArrayItem li4 = jset(lk, a, b, c).withUnits(JUnitsOfMeasure.meters);
    assertEquals(jvalues(li4).size(), 3);
    assertEquals(jvalue(li4, 2), LongArray(c));
  }

  @Test
  public void testLongMatrixKey() {

    long[][] m1 = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    long[][] m2 = {{1, 2, 3, 4, 5}, {10, 20, 30, 40, 50}};

    LongMatrix lm1 = LongMatrix(m1);
    LongMatrix lm2 = LongMatrix(m2);
    LongMatrixKey dk = LongMatrixKey(s1);

    // Test with single item
    LongMatrixItem di = jset(dk, lm1);
    assertEquals(jvalues(di), Collections.singletonList(lm1));
    assertEquals(jvalue(di), lm1);
    assertEquals(jget(di, 0).get(), lm1);

    // Test with list, withUnits
    List<LongMatrix> listIn = Arrays.asList(lm1, lm2);
    LongMatrixItem di2 = jset(dk, listIn).withUnits(JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test with list, units
    di2 = jset(dk, listIn, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test using one matrix without and with units
    di2 = jset(dk, m1);
    assertEquals(jvalue(di2), lm1);
    di2 = jset(dk, m2, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2), lm2);

    // Test using varargs as matrix
    LongMatrixItem di3 = jset(dk, lm1, lm2).withUnits(JUnitsOfMeasure.seconds);
    assertSame(di3.units(), JUnitsOfMeasure.seconds);
    assertEquals(jvalue(di3, 1), lm2);
    assertEquals(jvalues(di3), listIn);

    // Test using varargs as arrays
    LongMatrixItem di4 = jset(dk, m1, m2).withUnits(JUnitsOfMeasure.meters);
    assertSame(di4.units(), JUnitsOfMeasure.meters);
    assertEquals(jvalue(di4, 0), lm1);
    assertEquals(jvalues(di4), listIn);
  }

  @Test
  public void testShortKey() {
    // should allow setting from short
    short sval = 123;
    ShortKey ik = ShortKey(s1);

    // Test single item
    ShortItem si = jset(ik, sval);
    assertEquals(jvalues(si), Collections.singletonList(sval));
    assertTrue(jvalue(si) == sval);
    assertTrue(jget(si, 0).get() == sval);

    // Test with list, withUnits
    List<Short> sval2 = Arrays.asList((short) 12, (short) 24);
    ShortItem si2 = jset(ik, sval2).withUnits(JUnitsOfMeasure.degrees);
    assertSame(si2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(si2, 1), sval2.get(1));
    assertEquals(jvalues(si2), sval2);

    // Test with list, units
    si2 = jset(ik, sval2, JUnitsOfMeasure.degrees);
    assertSame(si2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(si2, 1), sval2.get(1));
    assertEquals(jvalues(si2), sval2);
  }

  @Test
  public void testShortArrayKey() {
    short[] a1 = {1, 2, 3, 4, 5};
    short[] a2 = {10, 20, 30, 40, 50};

    ShortArray sa1 = ShortArray(a1);
    ShortArray sa2 = ShortArray(a2);
    ShortArrayKey sk = ShortArrayKey(s1);

    // Test with single item
    ShortArrayItem si = jset(sk, sa1);
    assertEquals(jvalues(si), Collections.singletonList(sa1));
    assertEquals(jvalue(si), sa1);
    assertEquals(jget(si, 0).get(), sa1);

    List<ShortArray> listIn = Arrays.asList(sa1, sa2);

    // Test with list, withUnits
    ShortArrayItem si2 = jset(sk, listIn).withUnits(JUnitsOfMeasure.degrees);
    assertSame(si2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(si2, 1), listIn.get(1));
    assertEquals(jvalues(si2), listIn);

    // Test with list, units
    si2 = jset(sk, listIn, JUnitsOfMeasure.degrees);
    assertSame(si2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(si2, 1), listIn.get(1));
    assertEquals(jvalues(si2), listIn);

    // Test using one array without and with units
    si2 = jset(sk, a1);
    assertEquals(jvalue(si2), sa1);
    si2 = jset(sk, a2, JUnitsOfMeasure.degrees);
    assertSame(si2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(si2), sa2);

    // Test with varargs
    ShortArrayItem di3 = jset(sk, sa1, sa2);
    assertEquals(jvalue(di3, 1), sa2);
    assertEquals(jvalues(di3), listIn);

    short[] a = {1, 2, 3};
    short[] b = {10, 20, 30};
    short[] c = {100, 200, 300};

    ShortArrayItem di4 = jset(sk, a, b, c).withUnits(JUnitsOfMeasure.meters);
    assertTrue(jvalues(di4).size() == 3);
    assertEquals(jvalue(di4, 2), ShortArray(c));
  }

  @Test
  public void testShortMatrixKey() {

    short[][] m1 = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    short[][] m2 = {{1, 2, 3, 4, 5}, {10, 20, 30, 40, 50}};

    ShortMatrix sm1 = ShortMatrix(m1);
    ShortMatrix sm2 = ShortMatrix(m2);
    ShortMatrixKey dk = ShortMatrixKey(s1);

    // Test with single item
    ShortMatrixItem di = jset(dk, sm1);
    assertEquals(jvalues(di), Collections.singletonList(sm1));
    assertEquals(jvalue(di), sm1);
    assertEquals(jget(di, 0).get(), sm1);

    // Test with list, withUnits
    List<ShortMatrix> listIn = Arrays.asList(sm1, sm2);
    ShortMatrixItem di2 = jset(dk, listIn).withUnits(JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test with list, units
    di2 = jset(dk, listIn, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2, 1), listIn.get(1));
    assertEquals(jvalues(di2), listIn);

    // Test using one matrix without and with units
    di2 = jset(dk, m1);
    assertEquals(jvalue(di2), sm1);
    di2 = jset(dk, m2, JUnitsOfMeasure.degrees);
    assertSame(di2.units(), JUnitsOfMeasure.degrees);
    assertEquals(jvalue(di2), sm2);

    // Test using varargs as matrix
    ShortMatrixItem di3 = jset(dk, sm1, sm2).withUnits(JUnitsOfMeasure.seconds);
    assertSame(di3.units(), JUnitsOfMeasure.seconds);
    assertEquals(jvalue(di3, 1), sm2);
    assertEquals(jvalues(di3), listIn);

    // Test using varargs as arrays
    ShortMatrixItem di4 = jset(dk, m1, m2).withUnits(JUnitsOfMeasure.meters);
    assertSame(di4.units(), JUnitsOfMeasure.meters);
    assertEquals(jvalue(di4, 0), sm1);
    assertEquals(jvalues(di4), listIn);
  }

  @Test
  public void testStringKey() {
    StringKey filter1 = new StringKey("filter1");

    // Test single item
    StringItem si = jset(filter1, "A");
    assertEquals(jvalue(si), "A");

    // Test list withUnits
    si = jset(filter1, Arrays.asList("A", "B", "C")).withUnits(JUnitsOfMeasure.meters);
    assertEquals(jvalue(si), "A");
    assertEquals(jvalue(si, 0), "A");
    assertEquals(jvalue(si, 1), "B");
    assertEquals(jvalue(si, 2), "C");

    // Test for out of range - Junit exception testing is difficult
    Boolean exFlag = false;
    try {
      String val3 = jvalue(si, 3);
    } catch (IndexOutOfBoundsException ex) {
      exFlag = true;
    }
    assertTrue(exFlag);

    // Test list units
    si = jset(filter1, Arrays.asList("A", "B", "C"), JUnitsOfMeasure.meters);
    assertSame(si.units(), JUnitsOfMeasure.meters);
    assertEquals(jvalue(si), "A");
    assertEquals(jvalue(si, 0), "A");
    assertEquals(jvalue(si, 1), "B");
    assertEquals(jvalue(si, 2), "C");

    // Test gets
    Optional<String> ov = jget(si, 2);
    assertEquals(ov.get(), "C");
    assertEquals(jvalues(si), Arrays.asList("A", "B", "C"));

    // Test with varargs
    StringItem b = jset(filter1, "100", "200");
    assertEquals(jvalues(b), Arrays.asList("100", "200"));
  }


  @Test
  public void testChoiceItem() {
    // should allow creating with Choices object
    {
      // Choices as object with String input
      Choices choices = Choices.from("A", "B", "C");

      ChoiceKey ci1 = new ChoiceKey("mode", choices);
      assertEquals(ci1.choices(), choices);
      assertEquals(ci1.keyName(), "mode");

      ChoiceItem ci = jset(ci1, Choice("A"));
      assertEquals(jvalue(ci), Choice("A"));
      // Check that non-choice fails
      assertThatThrownBy(() -> jset(ci1, Choice("D"))).isInstanceOf(AssertionError.class);
    }

    // should allow creating with varargs of Strings
    {
      // Create directly with keyname, and Choice names
      ChoiceKey ci2 = ChoiceKey("test", "A", "B");
      assertEquals(ci2.choices(), Choices.from("A", "B"));
      assertEquals(ci2.keyName(), "test");

      ChoiceItem ci = jset(ci2, Choice("A"));
      assertEquals(jvalue(ci), Choice("A"));
      // Check that non-choice fails
      assertThatThrownBy(() -> jset(ci2, Choice("C"))).isInstanceOf(AssertionError.class);
    }

    // should allow creation with individual Choice items
    {
      // Now create with individual Choice items
      Choice uninitialized = Choice("uninitialized");
      Choice ready = Choice("ready");
      Choice busy = Choice("busy");
      Choice continuous = Choice("continuous");
      Choice error = Choice("error");

      ChoiceKey cmd = new ChoiceKey("cmd", Choices.fromChoices(uninitialized, ready, busy, continuous, error));
      assertEquals(cmd.choices(), Choices.fromChoices(uninitialized, ready, busy, continuous, error));

      // setting values
      ChoiceItem ci = jset(cmd, ready);
      assertEquals(jvalue(ci), ready);
    }
  }

  @Test
  public void testingStructItem() {
    // should allow creating Struct items
    StructKey skey = new StructKey("myStruct");

    StringKey ra = StringKey("ra");
    StringKey dec = StringKey("dec");
    DoubleKey epoch = DoubleKey("epoch");
    Struct sc1 = jadd(new Struct("probe1"), jset(ra, "12:13:14.1"), jset(dec, "32:33:34.4"), jset(epoch, 1950.0));

    StructItem citem = jset(skey, sc1);

    assertEquals(citem.size(), 1);
    Struct s = jvalue(citem);
    assertEquals(s.size(), 3);
    assertEquals(jvalue(jitem(s, ra)), "12:13:14.1");
    assertEquals(jvalue(jitem(s, dec)), "32:33:34.4");
    assertEquals(jvalue(jitem(s, epoch)), 1950.0, 0.001);
  }
}


