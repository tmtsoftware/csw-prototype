package javacsw.util.param;

import csw.util.param.*;
import csw.util.param.Events.*;
import csw.util.param.Parameters.ParameterSetType;
import csw.util.param.Parameters.*;

import java.util.*;

/**
 * Java API for working with parameters and parameter lists.
 */
@SuppressWarnings("unused")
public class JParameters {
  // BooleanItem
  public static BooleanKey BooleanKey(String name) {
    return new BooleanKey(name);
  }

  public static Boolean jvalue(BooleanParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static Boolean jvalue(BooleanParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Boolean> jvalues(BooleanParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Boolean> jget(BooleanParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static BooleanParameter jset(BooleanKey key, java.util.List<Boolean> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static BooleanParameter jset(BooleanKey key, java.util.List<Boolean> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static BooleanParameter jset(BooleanKey key, Boolean... v) {
    return JavaHelpers.jset(key, v);
  }

  // ByteArrayItem
  public static ByteArrayKey ByteArrayKey(String name) {
    return new ByteArrayKey(name);
  }

  public static ByteArray jvalue(ByteArrayParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static ByteArray jvalue(ByteArrayParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<ByteArray> jvalues(ByteArrayParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<ByteArray> jget(ByteArrayParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static ByteArrayParameter jset(ByteArrayKey key, java.util.List<ByteArray> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ByteArrayParameter jset(ByteArrayKey key, java.util.List<ByteArray> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static ByteArrayParameter jset(ByteArrayKey key, byte[] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(ByteArray(v)), units);
  }

  public static ByteArrayParameter jset(ByteArrayKey key, byte[]... vin) {
    List<ByteArray> v = new ArrayList<>();
    for (byte[] dv : vin) {
      v.add(ByteArray(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ByteArrayParameter jset(ByteArrayKey key, ByteArray... v) {
    return JavaHelpers.jset(key, v);
  }

  public static ByteArray ByteArray(byte[] v) {
    return new ByteArray(v);
  }

  // ByteMatrixItem
  public static ByteMatrixKey ByteMatrixKey(String name) {
    return new ByteMatrixKey(name);
  }

  public static ByteMatrix jvalue(ByteMatrixParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static ByteMatrix jvalue(ByteMatrixParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<ByteMatrix> jvalues(ByteMatrixParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<ByteMatrix> jget(ByteMatrixParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static ByteMatrixParameter jset(ByteMatrixKey key, java.util.List<ByteMatrix> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ByteMatrixParameter jset(ByteMatrixKey key, java.util.List<ByteMatrix> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static ByteMatrixParameter jset(ByteMatrixKey key, ByteMatrix... v) {
    return JavaHelpers.jset(key, v);
  }

  public static ByteMatrixParameter jset(ByteMatrixKey key, byte[][] v) {
    return JavaHelpers.jset(key, ByteMatrix(v));
  }

  public static ByteMatrixParameter jset(ByteMatrixKey key, byte[][] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(ByteMatrix(v)), units);
  }

  public static ByteMatrixParameter jset(ByteMatrixKey key, byte[][]... vin) {
    List<ByteMatrix> v = new ArrayList<>();
    for (byte[][] dv : vin) {
      v.add(ByteMatrix(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ByteMatrix ByteMatrix(byte[][] value) {
    return new ByteMatrix(value);
  }


  // CharItem
  public static CharKey CharKey(String name) {
    return new CharKey(name);
  }

  public static Character jvalue(CharParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static Character jvalue(CharParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Character> jvalues(CharParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Character> jget(CharParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static CharParameter jset(CharKey key, java.util.List<Character> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static CharParameter jset(CharKey key, java.util.List<Character> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static CharParameter jset(CharKey key, Character... v) {
    return JavaHelpers.jset(key, v);
  }

  // DoubleItem
  public static DoubleKey DoubleKey(String name) {
    return new DoubleKey(name);
  }

  public static Double jvalue(DoubleParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static Double jvalue(DoubleParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Double> jvalues(DoubleParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Double> jget(DoubleParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static DoubleParameter jset(DoubleKey key, java.util.List<Double> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static DoubleParameter jset(DoubleKey key, java.util.List<Double> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static DoubleParameter jset(DoubleKey key, Double... v) {
    return JavaHelpers.jset(key, v);
  }

  // DoubleArrayItem
  public static DoubleArrayKey DoubleArrayKey(String name) {
    return new DoubleArrayKey(name);
  }

  public static DoubleArray jvalue(DoubleArrayParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static DoubleArray jvalue(DoubleArrayParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<DoubleArray> jvalues(DoubleArrayParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<DoubleArray> jget(DoubleArrayParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static DoubleArrayParameter jset(DoubleArrayKey key, java.util.List<DoubleArray> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static DoubleArrayParameter jset(DoubleArrayKey key, java.util.List<DoubleArray> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static DoubleArrayParameter jset(DoubleArrayKey key, double[] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(DoubleArray(v)), units);
  }

  public static DoubleArrayParameter jset(DoubleArrayKey key, double[]... vin) {
    List<DoubleArray> v = new ArrayList<>();
    for (double[] dv : vin) {
      v.add(DoubleArray(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static DoubleArrayParameter jset(DoubleArrayKey key, DoubleArray... v) {
    return JavaHelpers.jset(key, v);
  }

  public static DoubleArray DoubleArray(double[] v) {
    return new DoubleArray(v);
  }

  // DoubleMatrix
  public static DoubleMatrixKey DoubleMatrixKey(String name) {
    return new DoubleMatrixKey(name);
  }

  public static DoubleMatrix jvalue(DoubleMatrixParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static DoubleMatrix jvalue(DoubleMatrixParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<DoubleMatrix> jvalues(DoubleMatrixParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<DoubleMatrix> jget(DoubleMatrixParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static DoubleMatrixParameter jset(DoubleMatrixKey key, java.util.List<DoubleMatrix> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static DoubleMatrixParameter jset(DoubleMatrixKey key, java.util.List<DoubleMatrix> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static DoubleMatrixParameter jset(DoubleMatrixKey key, DoubleMatrix... v) {
    return JavaHelpers.jset(key, v);
  }

  public static DoubleMatrixParameter jset(DoubleMatrixKey key, double[][] v) {
    return JavaHelpers.jset(key, DoubleMatrix(v));
  }

  public static DoubleMatrixParameter jset(DoubleMatrixKey key, double[][] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(DoubleMatrix(v)), units);
  }

  public static DoubleMatrixParameter jset(DoubleMatrixKey key, double[][]... vin) {
    List<DoubleMatrix> v = new ArrayList<>();
    for (double[][] dv : vin) {
      v.add(DoubleMatrix(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static DoubleMatrix DoubleMatrix(double[][] value) {
    return new DoubleMatrix(value);
  }

  // FloatItem
  public static FloatKey FloatKey(String name) {
    return new FloatKey(name);
  }

  public static Float jvalue(FloatParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static Float jvalue(FloatParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Float> jvalues(FloatParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Float> jget(FloatParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static FloatParameter jset(FloatKey key, java.util.List<Float> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static FloatParameter jset(FloatKey key, java.util.List<Float> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static FloatParameter jset(FloatKey key, Float... v) {
    return JavaHelpers.jset(key, v);
  }

  // FloatArrayItem
  public static FloatArrayKey FloatArrayKey(String name) {
    return new FloatArrayKey(name);
  }

  public static FloatArray jvalue(FloatArrayParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static FloatArray jvalue(FloatArrayParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<FloatArray> jvalues(FloatArrayParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<FloatArray> jget(FloatArrayParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static FloatArrayParameter jset(FloatArrayKey key, java.util.List<FloatArray> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static FloatArrayParameter jset(FloatArrayKey key, java.util.List<FloatArray> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static FloatArrayParameter jset(FloatArrayKey key, float[] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(FloatArray(v)), units);
  }

  public static FloatArrayParameter jset(FloatArrayKey key, float[]... vin) {
    List<FloatArray> v = new ArrayList<>();
    for (float[] dv : vin) {
      v.add(FloatArray(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static FloatArrayParameter jset(FloatArrayKey key, FloatArray... v) {
    return JavaHelpers.jset(key, v);
  }

  public static FloatArray FloatArray(float[] v) {
    return new FloatArray(v);
  }


  // FloatMatrix
  public static FloatMatrixKey FloatMatrixKey(String name) {
    return new FloatMatrixKey(name);
  }

  public static FloatMatrix jvalue(FloatMatrixParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static FloatMatrix jvalue(FloatMatrixParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<FloatMatrix> jvalues(FloatMatrixParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<FloatMatrix> jget(FloatMatrixParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static FloatMatrixParameter jset(FloatMatrixKey key, java.util.List<FloatMatrix> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static FloatMatrixParameter jset(FloatMatrixKey key, java.util.List<FloatMatrix> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static FloatMatrixParameter jset(FloatMatrixKey key, FloatMatrix... v) {
    return JavaHelpers.jset(key, v);
  }

  public static FloatMatrixParameter jset(FloatMatrixKey key, float[][] v) {
    return JavaHelpers.jset(key, FloatMatrix(v));
  }

  public static FloatMatrixParameter jset(FloatMatrixKey key, float[][] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(FloatMatrix(v)), units);
  }

  public static FloatMatrixParameter jset(FloatMatrixKey key, float[][]... vin) {
    List<FloatMatrix> v = new ArrayList<>();
    for (float[][] dv : vin) {
      v.add(FloatMatrix(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static FloatMatrix FloatMatrix(float[][] value) {
    return new FloatMatrix(value);
  }

  // IntItem
  public static IntKey IntKey(String name) {
    return new IntKey(name);
  }

  public static Integer jvalue(IntParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static Integer jvalue(IntParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Integer> jvalues(IntParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Integer> jget(IntParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static IntParameter jset(IntKey key, java.util.List<Integer> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static IntParameter jset(IntKey key, java.util.List<Integer> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static IntParameter jset(IntKey key, Integer... v) {
    return JavaHelpers.jset(key, v);
  }

  // IntArrayItem
  public static IntArrayKey IntArrayKey(String name) {
    return new IntArrayKey(name);
  }

  public static IntArray jvalue(IntArrayParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static IntArray jvalue(IntArrayParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<IntArray> jvalues(IntArrayParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<IntArray> jget(IntArrayParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static IntArrayParameter jset(IntArrayKey key, java.util.List<IntArray> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static IntArrayParameter jset(IntArrayKey key, java.util.List<IntArray> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static IntArrayParameter jset(IntArrayKey key, int[] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(IntArray(v)), units);
  }

  public static IntArrayParameter jset(IntArrayKey key, int[]... vin) {
    List<IntArray> v = new ArrayList<>();
    for (int[] dv : vin) {
      v.add(IntArray(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static IntArrayParameter jset(IntArrayKey key, IntArray... v) {
    return JavaHelpers.jset(key, v);
  }

  public static IntArray IntArray(int[] v) {
    return new IntArray(v);
  }

  // IntMatrix
  public static IntMatrixKey IntMatrixKey(String name) {
    return new IntMatrixKey(name);
  }

  public static IntMatrix jvalue(IntMatrixParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static IntMatrix jvalue(IntMatrixParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<IntMatrix> jvalues(IntMatrixParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<IntMatrix> jget(IntMatrixParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static IntMatrixParameter jset(IntMatrixKey key, java.util.List<IntMatrix> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static IntMatrixParameter jset(IntMatrixKey key, java.util.List<IntMatrix> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static IntMatrixParameter jset(IntMatrixKey key, IntMatrix... v) {
    return JavaHelpers.jset(key, v);
  }

  public static IntMatrixParameter jset(IntMatrixKey key, int[][] v) {
    return JavaHelpers.jset(key, IntMatrix(v));
  }

  public static IntMatrixParameter jset(IntMatrixKey key, int[][] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(IntMatrix(v)), units);
  }

  public static IntMatrixParameter jset(IntMatrixKey key, int[][]... vin) {
    List<IntMatrix> v = new ArrayList<>();
    for (int[][] dv : vin) {
      v.add(IntMatrix(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static IntMatrix IntMatrix(int[][] value) {
    return new IntMatrix(value);
  }


  // LongItem
  public static LongKey LongKey(String name) {
    return new LongKey(name);
  }

  public static Long jvalue(LongParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static Long jvalue(LongParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Long> jvalues(LongParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Long> jget(LongParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static LongParameter jset(LongKey key, java.util.List<Long> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static LongParameter jset(LongKey key, java.util.List<Long> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static LongParameter jset(LongKey key, Long... v) {
    return JavaHelpers.jset(key, v);
  }

  // LongArrayItem
  public static LongArrayKey LongArrayKey(String name) {
    return new LongArrayKey(name);
  }

  public static LongArray jvalue(LongArrayParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static LongArray jvalue(LongArrayParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<LongArray> jvalues(LongArrayParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<LongArray> jget(LongArrayParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static LongArrayParameter jset(LongArrayKey key, java.util.List<LongArray> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static LongArrayParameter jset(LongArrayKey key, java.util.List<LongArray> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static LongArrayParameter jset(LongArrayKey key, long[] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(LongArray(v)), units);
  }

  public static LongArrayParameter jset(LongArrayKey key, long[]... vin) {
    List<LongArray> v = new ArrayList<>();
    for (long[] dv : vin) {
      v.add(LongArray(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static LongArrayParameter jset(LongArrayKey key, LongArray... v) {
    return JavaHelpers.jset(key, v);
  }

  public static LongArray LongArray(long[] v) {
    return new LongArray(v);
  }

  // LongMatrix
  public static LongMatrixKey LongMatrixKey(String name) {
    return new LongMatrixKey(name);
  }

  public static LongMatrix jvalue(LongMatrixParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static LongMatrix jvalue(LongMatrixParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<LongMatrix> jvalues(LongMatrixParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<LongMatrix> jget(LongMatrixParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static LongMatrixParameter jset(LongMatrixKey key, java.util.List<LongMatrix> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static LongMatrixParameter jset(LongMatrixKey key, java.util.List<LongMatrix> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static LongMatrixParameter jset(LongMatrixKey key, LongMatrix... v) {
    return JavaHelpers.jset(key, v);
  }

  public static LongMatrixParameter jset(LongMatrixKey key, long[][] v) {
    return JavaHelpers.jset(key, LongMatrix(v));
  }

  public static LongMatrixParameter jset(LongMatrixKey key, long[][] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(LongMatrix(v)), units);
  }

  public static LongMatrixParameter jset(LongMatrixKey key, long[][]... vin) {
    List<LongMatrix> v = new ArrayList<>();
    for (long[][] dv : vin) {
      v.add(LongMatrix(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static LongMatrix LongMatrix(long[][] value) {
    return new LongMatrix(value);
  }

  // ShortItem
  public static ShortKey ShortKey(String name) {
    return new ShortKey(name);
  }

  public static Short jvalue(ShortParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static Short jvalue(ShortParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Short> jvalues(ShortParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Short> jget(ShortParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static ShortParameter jset(ShortKey key, java.util.List<Short> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ShortParameter jset(ShortKey key, java.util.List<Short> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static ShortParameter jset(ShortKey key, Short... v) {
    return JavaHelpers.jset(key, v);
  }

  // ShortArrayItem
  public static ShortArrayKey ShortArrayKey(String name) {
    return new ShortArrayKey(name);
  }

  public static ShortArray jvalue(ShortArrayParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static ShortArray jvalue(ShortArrayParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<ShortArray> jvalues(ShortArrayParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<ShortArray> jget(ShortArrayParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static ShortArrayParameter jset(ShortArrayKey key, java.util.List<ShortArray> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ShortArrayParameter jset(ShortArrayKey key, java.util.List<ShortArray> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static ShortArrayParameter jset(ShortArrayKey key, short[] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(ShortArray(v)), units);
  }

  public static ShortArrayParameter jset(ShortArrayKey key, short[]... vin) {
    List<ShortArray> v = new ArrayList<>();
    for (short[] dv : vin) {
      v.add(ShortArray(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ShortArrayParameter jset(ShortArrayKey key, ShortArray... v) {
    return JavaHelpers.jset(key, v);
  }

  public static ShortArray ShortArray(short[] v) {
    return new ShortArray(v);
  }

  // ShortMatrix
  public static ShortMatrixKey ShortMatrixKey(String name) {
    return new ShortMatrixKey(name);
  }

  public static ShortMatrix jvalue(ShortMatrixParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static ShortMatrix jvalue(ShortMatrixParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<ShortMatrix> jvalues(ShortMatrixParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<ShortMatrix> jget(ShortMatrixParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static ShortMatrixParameter jset(ShortMatrixKey key, java.util.List<ShortMatrix> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ShortMatrixParameter jset(ShortMatrixKey key, java.util.List<ShortMatrix> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static ShortMatrixParameter jset(ShortMatrixKey key, ShortMatrix... v) {
    return JavaHelpers.jset(key, v);
  }

  public static ShortMatrixParameter jset(ShortMatrixKey key, short[][] v) {
    return JavaHelpers.jset(key, ShortMatrix(v));
  }

  public static ShortMatrixParameter jset(ShortMatrixKey key, short[][] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(ShortMatrix(v)), units);
  }

  public static ShortMatrixParameter jset(ShortMatrixKey key, short[][]... vin) {
    List<ShortMatrix> v = new ArrayList<>();
    for (short[][] dv : vin) {
      v.add(ShortMatrix(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ShortMatrix ShortMatrix(short[][] value) {
    return new ShortMatrix(value);
  }

  // StringItem
  public static StringKey StringKey(String name) {
    return new StringKey(name);
  }

  public static String jvalue(StringParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static String jvalue(StringParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<String> jvalues(StringParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<String> jget(StringParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static StringParameter jset(StringKey key, java.util.List<String> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static StringParameter jset(StringKey key, java.util.List<String> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static StringParameter jset(StringKey key, String... v) {
    return JavaHelpers.jset(key, v);
  }

  // ChoiceItem
  public static ChoiceKey ChoiceKey(String name, String... choices) {
    return new ChoiceKey(name, Choices.from(choices));
  }

  public static Choice Choice(String s) {
    return new Choice(s);
  }

  public static Choice jvalue(ChoiceParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static Choice jvalue(ChoiceParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Choice> jvalues(ChoiceParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Choice> jget(ChoiceParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static ChoiceParameter jset(ChoiceKey key, java.util.List<Choice> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ChoiceParameter jset(ChoiceKey key, java.util.List<Choice> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static ChoiceParameter jset(ChoiceKey key, Choice... v) {
    return JavaHelpers.jset(key, v);
  }

  // StructItem
  public static StructKey StructKey(String name) {
    return new StructKey(name);
  }

  public static Struct jvalue(StructParameter item) {
    return JavaHelpers.jvalue(item);
  }

  public static Struct jvalue(StructParameter item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Struct> jvalues(StructParameter item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Struct> jget(StructParameter item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static StructParameter jset(StructKey key, java.util.List<Struct> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static StructParameter jset(StructKey key, java.util.List<Struct> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static StructParameter jset(StructKey key, Struct... v) {
    return JavaHelpers.jset(key, v);
  }


  // ---------------- Commands --------------

  public static Setup Setup(CommandInfo info, String name) {
    return new Setup(info, name);
  }

  public static Setup Setup(CommandInfo info, Prefix key) {
    return new Setup(info, key, new scala.collection.immutable.HashSet<>());
  }

  public static Observe Observe(CommandInfo info, String name) {
    return new Observe(info, name);
  }

  public static Wait Wait(CommandInfo info, String name) {
    return new Wait(info, name);
  }

  public static StatusEvent StatusEvent(String name) {
    return new StatusEvent(name);
  }

  public static SystemEvent SystemEvent(String name) {
    return new SystemEvent(name);
  }

  public static ObserveEvent ObserveEvent(String name) {
    return new ObserveEvent(name);
  }

  /**
   * jgetParameter returns an Item as an Optional.  The Optional is empty if the item is not present.
   *
   * @param sc  The configuration to use to search for the item
   * @param key The key for the item to be located
   * @param <S> The Scala type of the value
   * @param <I> The item type assocated with the Scala type
   * @param <T> The type of the configuration setup
   * @return Optional item
   */
  public static <S, I extends Parameter<S>, T extends ParameterSetType<T>> Optional<I> jgetParameter(T sc, Key<S, I> key) {
    return JavaHelpers.jget(sc, key);
  }

  public static <S, I extends Parameter<S>, T extends ParameterSetType<T>> I jparameter(T sc, Key<S, I> key) {
    Optional<I> item = JavaHelpers.jget(sc, key);
    // While Optional will throw its own exception, I choose to check and throw my own with hopefully a better message.
    if (item.isPresent()) {
      return item.get();
    }
    throw new NoSuchElementException("Key: " + key.keyName() + " was not found.");
  }

  public static <S, I extends Parameter<S>, T extends ParameterSetType<T>, J> Optional<J> jget(T sc, Key<S, I> key, int index) {
    return JavaHelpers.jget(sc, key, index);
  }

  // I believe it's okay to do this annotation since I is a reasonably known type based on items
  @SafeVarargs
  public static <I extends Parameter<?>, T extends ParameterSetType<T>> T jadd(T sc, I... itemsIn) {
    List<I> ilist = Arrays.asList(itemsIn);
    return JavaHelpers.jadd(sc, ilist);
  }

  public static <S, I extends Parameter<S>, T extends ParameterSetType<T>> T jremove(T sc, Key<S, I> key) {
    if (!sc.exists(key)) throw new NoSuchElementException("Item: " + key.keyName() + " could not be found");
    return sc.remove(key);
  }
}
