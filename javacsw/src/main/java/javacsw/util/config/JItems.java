package javacsw.util.config;

import csw.util.config.*;
import csw.util.config.Events.*;
import csw.util.config.Configurations.ConfigType;
import csw.util.config.Configurations.*;

import java.util.*;

/**
 * Java API for configuration items.
 */
@SuppressWarnings("unused")
public class JItems {
  // BooleanItem
  public static BooleanKey BooleanKey(String name) {
    return new BooleanKey(name);
  }

  public static Boolean jvalue(BooleanItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static Boolean jvalue(BooleanItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Boolean> jvalues(BooleanItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Boolean> jget(BooleanItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static BooleanItem jset(BooleanKey key, java.util.List<Boolean> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static BooleanItem jset(BooleanKey key, java.util.List<Boolean> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static BooleanItem jset(BooleanKey key, Boolean... v) {
    return JavaHelpers.jset(key, v);
  }

  // ByteArrayItem
  public static ByteArrayKey ByteArrayKey(String name) {
    return new ByteArrayKey(name);
  }

  public static ByteArray jvalue(ByteArrayItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static ByteArray jvalue(ByteArrayItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<ByteArray> jvalues(ByteArrayItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<ByteArray> jget(ByteArrayItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static ByteArrayItem jset(ByteArrayKey key, java.util.List<ByteArray> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ByteArrayItem jset(ByteArrayKey key, java.util.List<ByteArray> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static ByteArrayItem jset(ByteArrayKey key, byte[] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(ByteArray(v)), units);
  }

  public static ByteArrayItem jset(ByteArrayKey key, byte[]... vin) {
    List<ByteArray> v = new ArrayList<>();
    for (byte[] dv : vin) {
      v.add(ByteArray(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ByteArrayItem jset(ByteArrayKey key, ByteArray... v) {
    return JavaHelpers.jset(key, v);
  }

  public static ByteArray ByteArray(byte[] v) {
    return new ByteArray(v);
  }

  // ByteMatrixItem
  public static ByteMatrixKey ByteMatrixKey(String name) {
    return new ByteMatrixKey(name);
  }

  public static ByteMatrix jvalue(ByteMatrixItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static ByteMatrix jvalue(ByteMatrixItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<ByteMatrix> jvalues(ByteMatrixItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<ByteMatrix> jget(ByteMatrixItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static ByteMatrixItem jset(ByteMatrixKey key, java.util.List<ByteMatrix> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ByteMatrixItem jset(ByteMatrixKey key, java.util.List<ByteMatrix> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static ByteMatrixItem jset(ByteMatrixKey key, ByteMatrix... v) {
    return JavaHelpers.jset(key, v);
  }

  public static ByteMatrixItem jset(ByteMatrixKey key, byte[][] v) {
    return JavaHelpers.jset(key, ByteMatrix(v));
  }

  public static ByteMatrixItem jset(ByteMatrixKey key, byte[][] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(ByteMatrix(v)), units);
  }

  public static ByteMatrixItem jset(ByteMatrixKey key, byte[][]... vin) {
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

  public static Character jvalue(CharItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static Character jvalue(CharItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Character> jvalues(CharItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Character> jget(CharItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static CharItem jset(CharKey key, java.util.List<Character> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static CharItem jset(CharKey key, java.util.List<Character> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static CharItem jset(CharKey key, Character... v) {
    return JavaHelpers.jset(key, v);
  }

  // DoubleItem
  public static DoubleKey DoubleKey(String name) {
    return new DoubleKey(name);
  }

  public static Double jvalue(DoubleItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static Double jvalue(DoubleItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Double> jvalues(DoubleItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Double> jget(DoubleItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static DoubleItem jset(DoubleKey key, java.util.List<Double> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static DoubleItem jset(DoubleKey key, java.util.List<Double> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static DoubleItem jset(DoubleKey key, Double... v) {
    return JavaHelpers.jset(key, v);
  }

  // DoubleArrayItem
  public static DoubleArrayKey DoubleArrayKey(String name) {
    return new DoubleArrayKey(name);
  }

  public static DoubleArray jvalue(DoubleArrayItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static DoubleArray jvalue(DoubleArrayItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<DoubleArray> jvalues(DoubleArrayItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<DoubleArray> jget(DoubleArrayItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static DoubleArrayItem jset(DoubleArrayKey key, java.util.List<DoubleArray> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static DoubleArrayItem jset(DoubleArrayKey key, java.util.List<DoubleArray> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static DoubleArrayItem jset(DoubleArrayKey key, double[] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(DoubleArray(v)), units);
  }

  public static DoubleArrayItem jset(DoubleArrayKey key, double[]... vin) {
    List<DoubleArray> v = new ArrayList<>();
    for (double[] dv : vin) {
      v.add(DoubleArray(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static DoubleArrayItem jset(DoubleArrayKey key, DoubleArray... v) {
    return JavaHelpers.jset(key, v);
  }

  public static DoubleArray DoubleArray(double[] v) {
    return new DoubleArray(v);
  }

  // DoubleMatrix
  public static DoubleMatrixKey DoubleMatrixKey(String name) {
    return new DoubleMatrixKey(name);
  }

  public static DoubleMatrix jvalue(DoubleMatrixItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static DoubleMatrix jvalue(DoubleMatrixItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<DoubleMatrix> jvalues(DoubleMatrixItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<DoubleMatrix> jget(DoubleMatrixItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static DoubleMatrixItem jset(DoubleMatrixKey key, java.util.List<DoubleMatrix> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static DoubleMatrixItem jset(DoubleMatrixKey key, java.util.List<DoubleMatrix> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static DoubleMatrixItem jset(DoubleMatrixKey key, DoubleMatrix... v) {
    return JavaHelpers.jset(key, v);
  }

  public static DoubleMatrixItem jset(DoubleMatrixKey key, double[][] v) {
    return JavaHelpers.jset(key, DoubleMatrix(v));
  }

  public static DoubleMatrixItem jset(DoubleMatrixKey key, double[][] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(DoubleMatrix(v)), units);
  }

  public static DoubleMatrixItem jset(DoubleMatrixKey key, double[][]... vin) {
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

  public static Float jvalue(FloatItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static Float jvalue(FloatItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Float> jvalues(FloatItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Float> jget(FloatItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static FloatItem jset(FloatKey key, java.util.List<Float> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static FloatItem jset(FloatKey key, java.util.List<Float> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static FloatItem jset(FloatKey key, Float... v) {
    return JavaHelpers.jset(key, v);
  }

  // FloatArrayItem
  public static FloatArrayKey FloatArrayKey(String name) {
    return new FloatArrayKey(name);
  }

  public static FloatArray jvalue(FloatArrayItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static FloatArray jvalue(FloatArrayItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<FloatArray> jvalues(FloatArrayItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<FloatArray> jget(FloatArrayItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static FloatArrayItem jset(FloatArrayKey key, java.util.List<FloatArray> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static FloatArrayItem jset(FloatArrayKey key, java.util.List<FloatArray> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static FloatArrayItem jset(FloatArrayKey key, float[] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(FloatArray(v)), units);
  }

  public static FloatArrayItem jset(FloatArrayKey key, float[]... vin) {
    List<FloatArray> v = new ArrayList<>();
    for (float[] dv : vin) {
      v.add(FloatArray(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static FloatArrayItem jset(FloatArrayKey key, FloatArray... v) {
    return JavaHelpers.jset(key, v);
  }

  public static FloatArray FloatArray(float[] v) {
    return new FloatArray(v);
  }


  // FloatMatrix
  public static FloatMatrixKey FloatMatrixKey(String name) {
    return new FloatMatrixKey(name);
  }

  public static FloatMatrix jvalue(FloatMatrixItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static FloatMatrix jvalue(FloatMatrixItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<FloatMatrix> jvalues(FloatMatrixItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<FloatMatrix> jget(FloatMatrixItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static FloatMatrixItem jset(FloatMatrixKey key, java.util.List<FloatMatrix> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static FloatMatrixItem jset(FloatMatrixKey key, java.util.List<FloatMatrix> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static FloatMatrixItem jset(FloatMatrixKey key, FloatMatrix... v) {
    return JavaHelpers.jset(key, v);
  }

  public static FloatMatrixItem jset(FloatMatrixKey key, float[][] v) {
    return JavaHelpers.jset(key, FloatMatrix(v));
  }

  public static FloatMatrixItem jset(FloatMatrixKey key, float[][] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(FloatMatrix(v)), units);
  }

  public static FloatMatrixItem jset(FloatMatrixKey key, float[][]... vin) {
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

  public static Integer jvalue(IntItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static Integer jvalue(IntItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Integer> jvalues(IntItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Integer> jget(IntItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static IntItem jset(IntKey key, java.util.List<Integer> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static IntItem jset(IntKey key, java.util.List<Integer> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static IntItem jset(IntKey key, Integer... v) {
    return JavaHelpers.jset(key, v);
  }

  // IntArrayItem
  public static IntArrayKey IntArrayKey(String name) {
    return new IntArrayKey(name);
  }

  public static IntArray jvalue(IntArrayItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static IntArray jvalue(IntArrayItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<IntArray> jvalues(IntArrayItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<IntArray> jget(IntArrayItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static IntArrayItem jset(IntArrayKey key, java.util.List<IntArray> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static IntArrayItem jset(IntArrayKey key, java.util.List<IntArray> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static IntArrayItem jset(IntArrayKey key, int[] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(IntArray(v)), units);
  }

  public static IntArrayItem jset(IntArrayKey key, int[]... vin) {
    List<IntArray> v = new ArrayList<>();
    for (int[] dv : vin) {
      v.add(IntArray(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static IntArrayItem jset(IntArrayKey key, IntArray... v) {
    return JavaHelpers.jset(key, v);
  }

  public static IntArray IntArray(int[] v) {
    return new IntArray(v);
  }

  // IntMatrix
  public static IntMatrixKey IntMatrixKey(String name) {
    return new IntMatrixKey(name);
  }

  public static IntMatrix jvalue(IntMatrixItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static IntMatrix jvalue(IntMatrixItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<IntMatrix> jvalues(IntMatrixItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<IntMatrix> jget(IntMatrixItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static IntMatrixItem jset(IntMatrixKey key, java.util.List<IntMatrix> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static IntMatrixItem jset(IntMatrixKey key, java.util.List<IntMatrix> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static IntMatrixItem jset(IntMatrixKey key, IntMatrix... v) {
    return JavaHelpers.jset(key, v);
  }

  public static IntMatrixItem jset(IntMatrixKey key, int[][] v) {
    return JavaHelpers.jset(key, IntMatrix(v));
  }

  public static IntMatrixItem jset(IntMatrixKey key, int[][] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(IntMatrix(v)), units);
  }

  public static IntMatrixItem jset(IntMatrixKey key, int[][]... vin) {
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

  public static Long jvalue(LongItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static Long jvalue(LongItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Long> jvalues(LongItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Long> jget(LongItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static LongItem jset(LongKey key, java.util.List<Long> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static LongItem jset(LongKey key, java.util.List<Long> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static LongItem jset(LongKey key, Long... v) {
    return JavaHelpers.jset(key, v);
  }

  // LongArrayItem
  public static LongArrayKey LongArrayKey(String name) {
    return new LongArrayKey(name);
  }

  public static LongArray jvalue(LongArrayItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static LongArray jvalue(LongArrayItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<LongArray> jvalues(LongArrayItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<LongArray> jget(LongArrayItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static LongArrayItem jset(LongArrayKey key, java.util.List<LongArray> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static LongArrayItem jset(LongArrayKey key, java.util.List<LongArray> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static LongArrayItem jset(LongArrayKey key, long[] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(LongArray(v)), units);
  }

  public static LongArrayItem jset(LongArrayKey key, long[]... vin) {
    List<LongArray> v = new ArrayList<>();
    for (long[] dv : vin) {
      v.add(LongArray(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static LongArrayItem jset(LongArrayKey key, LongArray... v) {
    return JavaHelpers.jset(key, v);
  }

  public static LongArray LongArray(long[] v) {
    return new LongArray(v);
  }

  // LongMatrix
  public static LongMatrixKey LongMatrixKey(String name) {
    return new LongMatrixKey(name);
  }

  public static LongMatrix jvalue(LongMatrixItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static LongMatrix jvalue(LongMatrixItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<LongMatrix> jvalues(LongMatrixItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<LongMatrix> jget(LongMatrixItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static LongMatrixItem jset(LongMatrixKey key, java.util.List<LongMatrix> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static LongMatrixItem jset(LongMatrixKey key, java.util.List<LongMatrix> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static LongMatrixItem jset(LongMatrixKey key, LongMatrix... v) {
    return JavaHelpers.jset(key, v);
  }

  public static LongMatrixItem jset(LongMatrixKey key, long[][] v) {
    return JavaHelpers.jset(key, LongMatrix(v));
  }

  public static LongMatrixItem jset(LongMatrixKey key, long[][] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(LongMatrix(v)), units);
  }

  public static LongMatrixItem jset(LongMatrixKey key, long[][]... vin) {
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

  public static Short jvalue(ShortItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static Short jvalue(ShortItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Short> jvalues(ShortItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Short> jget(ShortItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static ShortItem jset(ShortKey key, java.util.List<Short> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ShortItem jset(ShortKey key, java.util.List<Short> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static ShortItem jset(ShortKey key, Short... v) {
    return JavaHelpers.jset(key, v);
  }

  // ShortArrayItem
  public static ShortArrayKey ShortArrayKey(String name) {
    return new ShortArrayKey(name);
  }

  public static ShortArray jvalue(ShortArrayItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static ShortArray jvalue(ShortArrayItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<ShortArray> jvalues(ShortArrayItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<ShortArray> jget(ShortArrayItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static ShortArrayItem jset(ShortArrayKey key, java.util.List<ShortArray> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ShortArrayItem jset(ShortArrayKey key, java.util.List<ShortArray> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static ShortArrayItem jset(ShortArrayKey key, short[] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(ShortArray(v)), units);
  }

  public static ShortArrayItem jset(ShortArrayKey key, short[]... vin) {
    List<ShortArray> v = new ArrayList<>();
    for (short[] dv : vin) {
      v.add(ShortArray(dv));
    }
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ShortArrayItem jset(ShortArrayKey key, ShortArray... v) {
    return JavaHelpers.jset(key, v);
  }

  public static ShortArray ShortArray(short[] v) {
    return new ShortArray(v);
  }

  // ShortMatrix
  public static ShortMatrixKey ShortMatrixKey(String name) {
    return new ShortMatrixKey(name);
  }

  public static ShortMatrix jvalue(ShortMatrixItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static ShortMatrix jvalue(ShortMatrixItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<ShortMatrix> jvalues(ShortMatrixItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<ShortMatrix> jget(ShortMatrixItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static ShortMatrixItem jset(ShortMatrixKey key, java.util.List<ShortMatrix> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ShortMatrixItem jset(ShortMatrixKey key, java.util.List<ShortMatrix> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static ShortMatrixItem jset(ShortMatrixKey key, ShortMatrix... v) {
    return JavaHelpers.jset(key, v);
  }

  public static ShortMatrixItem jset(ShortMatrixKey key, short[][] v) {
    return JavaHelpers.jset(key, ShortMatrix(v));
  }

  public static ShortMatrixItem jset(ShortMatrixKey key, short[][] v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, Collections.singletonList(ShortMatrix(v)), units);
  }

  public static ShortMatrixItem jset(ShortMatrixKey key, short[][]... vin) {
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

  public static String jvalue(StringItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static String jvalue(StringItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<String> jvalues(StringItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<String> jget(StringItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static StringItem jset(StringKey key, java.util.List<String> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static StringItem jset(StringKey key, java.util.List<String> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static StringItem jset(StringKey key, String... v) {
    return JavaHelpers.jset(key, v);
  }

  // ChoiceItem
  public static ChoiceKey ChoiceKey(String name, String... choices) {
    return new ChoiceKey(name, Choices.from(choices));
  }

  public static Choice Choice(String s) {
    return new Choice(s);
  }

  public static Choice jvalue(ChoiceItem item) {
    return JavaHelpers.jvalue(item);
  }

  public static Choice jvalue(ChoiceItem item, int index) {
    return JavaHelpers.jvalue(item, index);
  }

  public static List<Choice> jvalues(ChoiceItem item) {
    return JavaHelpers.jvalues(item);
  }

  public static Optional<Choice> jget(ChoiceItem item, int index) {
    return JavaHelpers.jget(item, index);
  }

  public static ChoiceItem jset(ChoiceKey key, java.util.List<Choice> v) {
    return JavaHelpers.jset(key, v, JUnitsOfMeasure.none);
  }

  public static ChoiceItem jset(ChoiceKey key, java.util.List<Choice> v, UnitsOfMeasure.Units units) {
    return JavaHelpers.jset(key, v, units);
  }

  public static ChoiceItem jset(ChoiceKey key, Choice... v) {
    return JavaHelpers.jset(key, v);
  }


  // ---------------- Configuration level

  public static SetupConfig SetupConfig(String name) {
    return new SetupConfig(name);
  }

  public static SetupConfig SetupConfig(ConfigKey key) {
    return new SetupConfig(key, scala.collection.immutable.Nil.toSet());
  }

  public static ObserveConfig ObserveConfig(String name) {
    return new ObserveConfig(name);
  }

  public static WaitConfig WaitConfig(String name) {
    return new WaitConfig(name);
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
   * jgetItem returns an Item as an Optional.  The Optional is empty if the item is not present.
   *
   * @param sc  The configuration to use to search for the item
   * @param key The key for the item to be located
   * @param <S> The Scala type of the value
   * @param <I> The item type assocated with the Scala type
   * @param <T> The type of the configuration sc
   * @return Optional item
   */
  public static <S, I extends Item<S>, T extends ConfigType<T>> Optional<I> jgetItem(T sc, Key<S, I> key) {
    return JavaHelpers.jget(sc, key);
  }

  public static <S, I extends Item<S>, T extends ConfigType<T>> I jitem(T sc, Key<S, I> key) {
    Optional<I> item = JavaHelpers.jget(sc, key);
    // While Optional will throw its own exception, I choose to check and throw my own with hopefully a better message.
    if (item.isPresent()) {
      return item.get();
    }
    throw new NoSuchElementException("Key: " + key.keyName() + " was not found.");
  }

  public static <S, I extends Item<S>, T extends ConfigType<T>, J> Optional<J> jget(T sc, Key<S, I> key, int index) {
    return JavaHelpers.jget(sc, key, index);
  }

  // I believe it's okay to do this annotation since I is a reasonably known type based on items
  @SafeVarargs
  public static <I extends Item<?>, T extends ConfigType<T>> T jadd(T sc, I... itemsIn) {
    List<I> ilist = Arrays.asList(itemsIn);
    return JavaHelpers.jadd(sc, ilist);
  }

  public static <S, I extends Item<S>, T extends ConfigType<T>> T jremove(T sc, Key<S, I> key) {
    if (!sc.exists(key)) throw new NoSuchElementException("Item: " + key.keyName() + " could not be found");
    return sc.remove(key);
  }
}
