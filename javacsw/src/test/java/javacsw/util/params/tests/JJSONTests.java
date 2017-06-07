package javacsw.util.params.tests;

import csw.util.param.*;
import csw.util.param.Parameters.Observe;
import csw.util.param.Parameters.Setup;
import csw.util.param.Parameters.CommandInfo;
import csw.util.param.Events.ObserveEvent;
import csw.util.param.Events.StatusEvent;
import csw.util.param.Events.SystemEvent;
import javacsw.util.params.JParameters;
import javacsw.util.params.JSubsystem;
import org.junit.Test;
import spray.json.JsValue;

import java.util.Arrays;

import static javacsw.util.params.JParameters.*;
import static javacsw.util.params.JUnitsOfMeasure.*;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 */
@SuppressWarnings("unused")
public class JJSONTests {
  private static final String s1 = "encoder";
  private static final String s2 = "filter";
  private static final String s3 = "detectorTemp";

  private static final String ck = "wfos.blue.filter";
  private static final String ck1 = "wfos.prog.cloudcover";
  private static final String ck2 = "wfos.red.filter";
  private static final String ck3 = "wfos.red.detector";

  private static final Parameters.CommandInfo info = new CommandInfo("Obs001");


  @Test
  public void testSubsystemJSON() {
    Subsystem wfos = JSubsystem.WFOS;

    // should encode and decode
    JsValue json = ParameterSetJson.subsystemFormat().write(wfos);
    Subsystem sub = ParameterSetJson.subsystemFormat().read(json);
    assertTrue(sub.equals(wfos));
  }

  @Test
  public void testConcreteItems() {
    // char item encode/decode
    {
      CharKey k1 = CharKey(s3);
      CharParameter i1 = jset(k1, 'd');
      JsValue j1 = ParameterSetJson.charParameterFormat().write(i1);
      CharParameter in1 = ParameterSetJson.charParameterFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // short item encode/decode
    {
      ShortKey k1 = ShortKey(s3);
      short s = -1;
      ShortParameter i1 = jset(k1, s).withUnits(none);
      JsValue j1 = ParameterSetJson.shortParameterFormat().write(i1);
      ShortParameter in1 = ParameterSetJson.shortParameterFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // int item encode/decode
    {
      IntKey k1 = IntKey(s3);
      int i = -1;
      IntParameter i1 = jset(k1, i).withUnits(none);
      JsValue j1 = ParameterSetJson.intParameterFormat().write(i1);
      IntParameter in1 = ParameterSetJson.intParameterFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // long item encode/decode
    {
      LongKey k1 = LongKey(s3);
      long l = 123456L;
      LongParameter i1 = jset(k1, l).withUnits(none);
      JsValue j1 = ParameterSetJson.longParameterFormat().write(i1);
      LongParameter in1 = ParameterSetJson.longParameterFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // float item encode/decode
    {
      FloatKey k1 = FloatKey(s3);
      float f = 123.456f;
      FloatParameter i1 = jset(k1, f).withUnits(none);
      JsValue j1 = ParameterSetJson.floatParameterFormat().write(i1);
      FloatParameter in1 = ParameterSetJson.floatParameterFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // double item encode/decode
    {
      DoubleKey k1 = new DoubleKey(s3);
      double f = 123.456;
      DoubleParameter i1 = jset(k1, f).withUnits(none);
      JsValue j1 = ParameterSetJson.doubleParameterFormat().write(i1);
      DoubleParameter in1 = ParameterSetJson.doubleParameterFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // boolean item encode/decode
    {
      BooleanKey k1 = new BooleanKey(s3);
      BooleanParameter i1 = jset(k1, true, false).withUnits(none);
      JsValue j1 = ParameterSetJson.booleanParameterFormat().write(i1);
      BooleanParameter in1 = ParameterSetJson.booleanParameterFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // string item encode/decode
    {
      StringKey k1 = new StringKey(s3);
      StringParameter i1 = jset(k1, "Blue", "Green").withUnits(none);
      JsValue j1 = ParameterSetJson.stringParameterFormat().write(i1);
      StringParameter in1 = ParameterSetJson.stringParameterFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
  }

  @Test
  public void testingItems() {
    // TODO (Is this needed from Java code?)
  }

  @Test
  public void testSetupJSON() {
    CharKey k1 = new CharKey("a");
    IntKey k2 = new IntKey("b");
    LongKey k3 = new LongKey("c");
    FloatKey k4 = new FloatKey("d");
    DoubleKey k5 = new DoubleKey("e");
    BooleanKey k6 = new BooleanKey("f");
    StringKey k7 = new StringKey("g");

    CharParameter i1 = jset(k1, 'd').withUnits(none);
    IntParameter i2 = jset(k2, 22).withUnits(none);
    LongParameter i3 = jset(k3, 1234L).withUnits(none);
    FloatParameter i4 = jset(k4, 123.45f).withUnits(degrees);
    DoubleParameter i5 = jset(k5, 123.456).withUnits(meters);
    BooleanParameter i6 = jset(k6, false);
    StringParameter i7 = jset(k7, "GG495").withUnits(degrees);

    // Should encode/decode a Setup
    {
      Setup c1 = new Setup(info, ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
      assertTrue(c1.size() == 7);
      JsValue c1out = ParameterSetJson.writeSequenceCommand(c1);
      Setup c1in = ParameterSetJson.readSequenceCommand(c1out);
      assertTrue(jvalue(jparameter(c1in, k3)) == 1234L);
      assertEquals(c1, c1in);
    }
    // Should encode/decode a Observe
    {
      Observe c1 = new Observe(info, ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
      assertTrue(c1.size() == 7);
      JsValue c1out = ParameterSetJson.writeSequenceCommand(c1);
      Observe c1in = ParameterSetJson.readSequenceCommand(c1out);
      assertTrue(jvalue(jparameter(c1in, k3)) == 1234L);
      assertEquals(c1, c1in);
    }
    // Should encode/decode a StatusEvent
    {
      StatusEvent e1 = new StatusEvent("wfos.test").add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
      assertTrue(e1.size() == 7);
      JsValue e1out = ParameterSetJson.writeEvent(e1);
      StatusEvent e1in = ParameterSetJson.readEvent(e1out);
      assertTrue(jvalue(jparameter(e1in, k3)) == 1234L);
      assertEquals(e1, e1in);
    }
    // Should encode/decode a ObserveEvent
    {
      ObserveEvent e1 = new ObserveEvent("wfos.test").add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
      assertTrue(e1.size() == 7);
      JsValue e1out = ParameterSetJson.writeEvent(e1);
      ObserveEvent e1in = ParameterSetJson.readEvent(e1out);
      assertTrue(jvalue(jparameter(e1in, k3)) == 1234L);
      assertEquals(e1, e1in);
    }
    // Should encode/decode a SystemEvent
    {
      SystemEvent e1 = new SystemEvent("wfos.test").add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
      assertTrue(e1.size() == 7);
      JsValue e1out = ParameterSetJson.writeEvent(e1);
      SystemEvent e1in = ParameterSetJson.readEvent(e1out);
      assertTrue(jvalue(jparameter(e1in, k3)) == 1234L);
      assertEquals(e1, e1in);
    }
  }

  @Test
  public void TestDoubleMatrixItem() {
    // Should allow matrix values
    DoubleMatrixKey k1 = DoubleMatrixKey("myMatrix");
    double[][] mIn = {{1.0, 2.0, 3.0}, {4.1, 5.1, 6.1}, {7.2, 8.2, 9.2}};
    DoubleMatrix m1 = DoubleMatrix(mIn);
    DoubleMatrixParameter di = jset(k1, m1);
    Setup sc1 = new Setup(info, ck).add(di);
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jparameter(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jparameter(sc1, k1)).data()[1], mIn[1]));

    JsValue sc1out = ParameterSetJson.writeSequenceCommand(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    Setup sc1in = ParameterSetJson.readSequenceCommand(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jparameter(sc1in, k1), di);
  }


  @Test
  public void TestDoubleArrayItem() {
    // Should allow array values
    DoubleArrayKey k1 = new DoubleArrayKey("myVector");
    DoubleArray m1 = DoubleArray(new double[]{1.0, 2.0, 3.0});
    Setup sc1 = new Setup(info, ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jparameter(sc1, k1)), m1);
    assertEquals(jvalue(jparameter(sc1, k1)).data(), m1.data());

    JsValue sc1out = ParameterSetJson.writeSequenceCommand(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    Setup sc1in = ParameterSetJson.readSequenceCommand(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jparameter(sc1in, k1)), m1);
  }


  @Test
  public void TestFloatMatrixItem() {
    // Should allow matrix values
    FloatMatrixKey k1 = FloatMatrixKey("myMatrix");
    float[][] mIn = {{1.0f, 2.0f, 3.0f}, {4.1f, 5.1f, 6.1f}, {7.2f, 8.2f, 9.2f}};
    FloatMatrix m1 = FloatMatrix(mIn);

    Setup sc1 = new Setup(info, ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jparameter(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jparameter(sc1, k1)).data()[1], mIn[1]));

    JsValue sc1out = ParameterSetJson.writeSequenceCommand(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    Setup sc1in = ParameterSetJson.readSequenceCommand(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jparameter(sc1in, k1)), m1);
  }

  @Test
  public void TestFloatVectorItem() {
    // Should allow vector values
    FloatArrayKey k1 = FloatArrayKey("myArray");
    float[] m1In = new float[]{1.0f, 2.0f, 3.0f};
    FloatArray m1 = FloatArray(m1In);
    Setup sc1 = new Setup(info, ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jparameter(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jparameter(sc1, k1)).data(), m1In));

    JsValue sc1out = ParameterSetJson.writeSequenceCommand(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    Setup sc1in = ParameterSetJson.readSequenceCommand(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jparameter(sc1in, k1)), m1);
  }

  @Test
  public void TestIntMatrixItem() {
    // Should allow matrix values
    IntMatrixKey k1 = new IntMatrixKey("myMatrix");
    int[][] m1In = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    IntMatrix m1 = IntMatrix(m1In);

    Setup sc1 = new Setup(info, ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jparameter(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jparameter(sc1, k1)).data()[1], m1In[1]));

    JsValue sc1out = ParameterSetJson.writeSequenceCommand(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    Setup sc1in = ParameterSetJson.readSequenceCommand(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jparameter(sc1, k1)), m1);
  }


  @Test
  public void TestIntArrayItem() {
    // Should allow array values
    IntArrayKey k1 = IntArrayKey("myVector");
    int[] m1In = {1, 2, 3};
    int[] m2In = {4, 5, 6};
    Setup sc1 = new Setup(info, ck).add(jset(k1, m1In, m2In));
    assertTrue(sc1.size() == 1);
    assertTrue(jparameter(sc1, k1).size() == 2);
    //System.out.println("Its: " + jparameter(sc1, k1).values());
    assertEquals(jvalue(jparameter(sc1, k1), 0).data(), m1In);
    assertEquals(jvalue(jparameter(sc1, k1), 1).data(), m2In);

    JsValue sc1out = ParameterSetJson.writeSequenceCommand(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    Setup sc1in = ParameterSetJson.readSequenceCommand(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jparameter(sc1, k1)).data(), m1In);
  }

  @Test
  public void TestByteMatrixItem() {
    // Should allow matrix values
    ByteMatrixKey k1 = new ByteMatrixKey("myMatrix");
    byte[][] m1In = new byte[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    ByteMatrix m1 = ByteMatrix(m1In);
    Setup sc1 = new Setup(info, ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jparameter(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jparameter(sc1, k1)).data()[1], m1In[1]));
    assertEquals(jvalue(jparameter(sc1, k1)).data()[2][2], 9);

    JsValue sc1out = ParameterSetJson.writeSequenceCommand(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    Setup sc1in = ParameterSetJson.readSequenceCommand(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jparameter(sc1in, k1)), m1);
  }

  @Test
  public void TestByteArrayItem() {
    // Should allow arraye values
    ByteArrayKey k1 = new ByteArrayKey("myArray");
    byte[] m1In = new byte[]{1, 2, 3};
    ByteArray m1 = ByteArray(m1In);
    Setup sc1 = new Setup(info, ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jparameter(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jparameter(sc1, k1)).data(), m1In));
    assertTrue(jvalue(jparameter(sc1, k1)).data()[2] == 3);

    JsValue sc1out = ParameterSetJson.writeSequenceCommand(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    Setup sc1in = ParameterSetJson.readSequenceCommand(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jparameter(sc1, k1)), m1);
  }

  @Test
  public void TestShortMatrixItem() {
    // Should allow matrix values
    ShortMatrixKey k1 = new ShortMatrixKey("myMatrix");
    short[][] m1In = new short[][ ]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    ShortMatrix m1 = ShortMatrix(m1In);
    Setup sc1 = jadd(JParameters.Setup(info, ck), jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jparameter(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jparameter(sc1, k1)).data()[1], m1In[1]));
    assertTrue(jvalue(jparameter(sc1, k1)).data()[2][2] == 9);

    JsValue sc1out = ParameterSetJson.writeSequenceCommand(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    Setup sc1in = ParameterSetJson.readSequenceCommand(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jparameter(sc1in, k1)), m1);
  }


  @Test
  public void TestShortArrayItem() {
    // Should allow array values
    ShortArrayKey k1 = ShortArrayKey("myArray");
    ShortArrayKey k2 = ShortArrayKey("myArray2");
    short[] m1In = new short[]{1, 2, 3};
    ShortArray m1 = ShortArray(m1In);
    Setup sc1 = jadd(JParameters.Setup(info, ck), jset(k1, m1));
    // Just checking to see if adding second works
    sc1 = jadd(sc1, jset(k2, m1));
    assertTrue(sc1.size() == 2);
    assertEquals(jvalue(jparameter(sc1, k1)), m1);
    assertEquals(jvalue(jparameter(sc1, k1)).data(), m1In);
    assertTrue(jvalue(jparameter(sc1, k1)).data()[2] == 3);

    JsValue sc1out = ParameterSetJson.writeSequenceCommand(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    Setup sc1in = ParameterSetJson.readSequenceCommand(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jparameter(sc1in, k1)), m1);
  }

  @Test
  public void TestLongMatrixItem() {
    // Should allow matrix values
    LongMatrixKey k1 = LongMatrixKey("myMatrix");
    long[][] m1In = new long[][ ]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    LongMatrix m1 = LongMatrix(m1In);
    Setup sc1 = JParameters.Setup(info, ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jparameter(sc1, k1)), m1);
    assertEquals(jvalue(jparameter(sc1, k1)).data()[2], m1In[2]);
    assertTrue(jvalue(jparameter(sc1, k1)).data()[2][2] == 9);

    JsValue sc1out = ParameterSetJson.writeSequenceCommand(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    Setup sc1in = ParameterSetJson.readSequenceCommand(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jparameter(sc1in, k1)), m1);
  }

  @Test
  public void TestLongArrayItem() {
    // Should allow array values
    LongArrayKey k1 = LongArrayKey("myArray");
    long[] m1In = new long[]{1, 2, 3};
    LongArray m1 = LongArray(m1In);
    Setup sc1 = JParameters.Setup(info, ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jparameter(sc1, k1)), m1);
    assertEquals(jvalue(jparameter(sc1, k1)).data(), m1In);
    assertTrue(jvalue(jparameter(sc1, k1)).data()[2] == 3);

    JsValue sc1out = ParameterSetJson.writeSequenceCommand(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    Setup sc1in = ParameterSetJson.readSequenceCommand(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jparameter(sc1in, k1)), m1);
  }
}
