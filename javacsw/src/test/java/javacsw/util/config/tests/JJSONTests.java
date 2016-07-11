package javacsw.util.config.tests;

import csw.util.config.*;
import csw.util.config.Configurations.ObserveConfig;
import csw.util.config.Configurations.SetupConfig;
import csw.util.config.Events.ObserveEvent;
import csw.util.config.Events.StatusEvent;
import csw.util.config.Events.SystemEvent;
import javacsw.util.config.JSubsystem;
import javacsw.util.config.JUnitsOfMeasure;
import org.junit.Test;
import spray.json.JsValue;

import java.util.Arrays;

import static javacsw.util.config.JItems.*;
import static javacsw.util.config.JUnitsOfMeasure.*;
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

  @Test
  public void testSubsystemJSON() {
    Subsystem wfos = JSubsystem.WFOS;

    // should encode and decode
    JsValue json = ConfigJSON.subsystemFormat().write(wfos);
    Subsystem sub = ConfigJSON.subsystemFormat().read(json);
    assertTrue(sub.equals(wfos));
  }

    /*
    @Test
    public void testCustomRaDecItem() {
        GenericKey<RaDec> k1 = new GenericKey<>("RaDec", "coords", RaDec.raDecFormat());
        RaDec c1 = new RaDec(7.3, 12.1);
        RaDec c2 = new RaDec(9.1, 2.9);
        Item<RaDec> i1 = jset(k1, c1, c2);
        SetupConfig sc1 = new SetupConfig(ck).add(i1);
        assertTrue(sc1.get(k1).get().values().size() == 2);
        assertTrue(sc1.get(k1).get().jvalue(0).equals(c1));
        assertTrue(sc1.get(k1).get().jvalue(1).equals(c2));

        JsValue sc1out = ConfigJSON.writeConfig(sc1);
//        System.out.println("sc1out: " + sc1out.prettyPrint());

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assertTrue(sc1.equals(sc1in));
        assertTrue(sc1in.get(k1).get().values().size() == 2);
        assertTrue(sc1in.get(k1).get().jvalue(0).equals(c1));
        assertTrue(sc1in.get(k1).get().jvalue(1).equals(c2));
        RaDec cc1 = sc1in.get(k1).get().jvalue(0);
        assertTrue(cc1.ra() == 7.3);
        assertTrue(cc1.dec() == 12.1);
        RaDec cc2 = sc1in.get(k1).get().jvalue(1);
        assertTrue(cc2.ra() == 9.1);
        assertTrue(cc2.dec() == 2.9);

        SetupConfig sc2 = new SetupConfig(ck).jset(k1, JUnitsOfMeasure.NoUnits, c1, c2);
        assertTrue(sc2.equals(sc1));
    }
    */

  @Test
  public void testConcreteItems() {
    // char item encode/decode
    {
      CharKey k1 = CharKey(s3);
      CharItem i1 = jset(k1, 'd');
      JsValue j1 = ConfigJSON.charItemFormat().write(i1);
      CharItem in1 = ConfigJSON.charItemFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // short item encode/decode
    {
      ShortKey k1 = ShortKey(s3);
      short s = -1;
      ShortItem i1 = jset(k1, s).withUnits(JUnitsOfMeasure.NoUnits);
      JsValue j1 = ConfigJSON.shortItemFormat().write(i1);
      ShortItem in1 = ConfigJSON.shortItemFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // int item encode/decode
    {
      IntKey k1 = IntKey(s3);
      int i = -1;
      IntItem i1 = jset(k1, i).withUnits(JUnitsOfMeasure.NoUnits);
      JsValue j1 = ConfigJSON.intItemFormat().write(i1);
      IntItem in1 = ConfigJSON.intItemFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // long item encode/decode
    {
      LongKey k1 = LongKey(s3);
      long l = 123456L;
      LongItem i1 = jset(k1, l).withUnits(JUnitsOfMeasure.NoUnits);
      JsValue j1 = ConfigJSON.longItemFormat().write(i1);
      LongItem in1 = ConfigJSON.longItemFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // float item encode/decode
    {
      FloatKey k1 = FloatKey(s3);
      float f = 123.456f;
      FloatItem i1 = jset(k1, f).withUnits(JUnitsOfMeasure.NoUnits);
      JsValue j1 = ConfigJSON.floatItemFormat().write(i1);
      FloatItem in1 = ConfigJSON.floatItemFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // double item encode/decode
    {
      DoubleKey k1 = new DoubleKey(s3);
      double f = 123.456;
      DoubleItem i1 = jset(k1, f).withUnits(JUnitsOfMeasure.NoUnits);
      JsValue j1 = ConfigJSON.doubleItemFormat().write(i1);
      DoubleItem in1 = ConfigJSON.doubleItemFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // boolean item encode/decode
    {
      BooleanKey k1 = new BooleanKey(s3);
      BooleanItem i1 = jset(k1, true, false).withUnits(JUnitsOfMeasure.NoUnits);
      JsValue j1 = ConfigJSON.booleanItemFormat().write(i1);
      BooleanItem in1 = ConfigJSON.booleanItemFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
    // string item encode/decode
    {
      StringKey k1 = new StringKey(s3);
      StringItem i1 = jset(k1, "Blue", "Green").withUnits(JUnitsOfMeasure.NoUnits);
      JsValue j1 = ConfigJSON.stringItemFormat().write(i1);
      StringItem in1 = ConfigJSON.stringItemFormat().read(j1);
      assertTrue(in1.equals(i1));
    }
  }

  @Test
  public void testingItems() {
    // TODO (Is this needed from Java code?)
  }

  @Test
  public void testSetupConfigJSON() {
    CharKey k1 = new CharKey("a");
    IntKey k2 = new IntKey("b");
    LongKey k3 = new LongKey("c");
    FloatKey k4 = new FloatKey("d");
    DoubleKey k5 = new DoubleKey("e");
    BooleanKey k6 = new BooleanKey("f");
    StringKey k7 = new StringKey("g");

    CharItem i1 = jset(k1, 'd').withUnits(NoUnits);
    IntItem i2 = jset(k2, 22).withUnits(NoUnits);
    LongItem i3 = jset(k3, 1234L).withUnits(NoUnits);
    FloatItem i4 = jset(k4, 123.45f).withUnits(Deg);
    DoubleItem i5 = jset(k5, 123.456).withUnits(Meters);
    BooleanItem i6 = jset(k6, false);
    StringItem i7 = jset(k7, "GG495").withUnits(Deg);

    // Should encode/decode a SetupConfig
    {
      SetupConfig c1 = new SetupConfig(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
      assertTrue(c1.size() == 7);
      JsValue c1out = ConfigJSON.writeConfig(c1);
      SetupConfig c1in = ConfigJSON.readConfig(c1out);
      assertTrue(jvalue(jitem(c1in, k3)) == 1234L);
      assertEquals(c1, c1in);
    }
    // Should encode/decode a ObserveConfig
    {
      ObserveConfig c1 = new ObserveConfig(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
      assertTrue(c1.size() == 7);
      JsValue c1out = ConfigJSON.writeConfig(c1);
      ObserveConfig c1in = ConfigJSON.readConfig(c1out);
      assertTrue(jvalue(jitem(c1in, k3)) == 1234L);
      assertEquals(c1, c1in);
    }
    // Should encode/decode a StatusEvent
    {
      StatusEvent e1 = new StatusEvent("wfos.test").add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
      assertTrue(e1.size() == 7);
      JsValue e1out = ConfigJSON.writeEvent(e1);
      StatusEvent e1in = ConfigJSON.readEvent(e1out);
      assertTrue(jvalue(jitem(e1in, k3)) == 1234L);
      assertEquals(e1, e1in);
    }
    // Should encode/decode a ObserveEvent
    {
      ObserveEvent e1 = new ObserveEvent("wfos.test").add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
      assertTrue(e1.size() == 7);
      JsValue e1out = ConfigJSON.writeEvent(e1);
      ObserveEvent e1in = ConfigJSON.readEvent(e1out);
      assertTrue(jvalue(jitem(e1in, k3)) == 1234L);
      assertEquals(e1, e1in);
    }
    // Should encode/decode a SystemEvent
    {
      SystemEvent e1 = new SystemEvent("wfos.test").add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
      assertTrue(e1.size() == 7);
      JsValue e1out = ConfigJSON.writeEvent(e1);
      SystemEvent e1in = ConfigJSON.readEvent(e1out);
      assertTrue(jvalue(jitem(e1in, k3)) == 1234L);
      assertEquals(e1, e1in);
    }
  }

  @Test
  public void TestDoubleMatrixItem() {
    // Should allow matrix values
    DoubleMatrixKey k1 = DoubleMatrixKey("myMatrix");
    double[][] mIn = {{1.0, 2.0, 3.0}, {4.1, 5.1, 6.1}, {7.2, 8.2, 9.2}};
    DoubleMatrix m1 = DoubleMatrix(mIn);
    DoubleMatrixItem di = jset(k1, m1);
    SetupConfig sc1 = new SetupConfig(ck).add(di);
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jitem(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jitem(sc1, k1)).value()[1], mIn[1]));

    JsValue sc1out = ConfigJSON.writeConfig(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jitem(sc1in, k1), di);
  }


  @Test
  public void TestDoubleArrayItem() {
    // Should allow array values
    DoubleArrayKey k1 = new DoubleArrayKey("myVector");
    DoubleArray m1 = DoubleArray(new double[]{1.0, 2.0, 3.0});
    SetupConfig sc1 = new SetupConfig(ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jitem(sc1, k1)), m1);
    assertEquals(jvalue(jitem(sc1, k1)).value(), m1.value());

    JsValue sc1out = ConfigJSON.writeConfig(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jitem(sc1in, k1)), m1);
  }


  @Test
  public void TestFloatMatrixItem() {
    // Should allow matrix values
    FloatMatrixKey k1 = FloatMatrixKey("myMatrix");
    float[][] mIn = {{1.0f, 2.0f, 3.0f}, {4.1f, 5.1f, 6.1f}, {7.2f, 8.2f, 9.2f}};
    FloatMatrix m1 = FloatMatrix(mIn);

    SetupConfig sc1 = new SetupConfig(ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jitem(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jitem(sc1, k1)).value()[1], mIn[1]));

    JsValue sc1out = ConfigJSON.writeConfig(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jitem(sc1in, k1)), m1);
  }

  @Test
  public void TestFloatVectorItem() {
    // Should allow vector values
    FloatArrayKey k1 = FloatArrayKey("myArray");
    float[] m1In = new float[]{1.0f, 2.0f, 3.0f};
    FloatArray m1 = FloatArray(m1In);
    SetupConfig sc1 = new SetupConfig(ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jitem(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jitem(sc1, k1)).value(), m1In));

    JsValue sc1out = ConfigJSON.writeConfig(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jitem(sc1in, k1)), m1);
  }

  @Test
  public void TestIntMatrixItem() {
    // Should allow matrix values
    IntMatrixKey k1 = new IntMatrixKey("myMatrix");
    int[][] m1In = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    IntMatrix m1 = IntMatrix(m1In);

    SetupConfig sc1 = new SetupConfig(ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jitem(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jitem(sc1, k1)).value()[1], m1In[1]));

    JsValue sc1out = ConfigJSON.writeConfig(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jitem(sc1, k1)), m1);
  }


  @Test
  public void TestIntArrayItem() {
    // Should allow array values
    IntArrayKey k1 = IntArrayKey("myVector");
    int[] m1In = {1, 2, 3};
    int[] m2In = {4, 5, 6};
    SetupConfig sc1 = new SetupConfig(ck).add(jset(k1, m1In, m2In));
    assertTrue(sc1.size() == 1);
    assertTrue(jitem(sc1, k1).size() == 2);
    //System.out.println("Its: " + jitem(sc1, k1).values());
    assertEquals(jvalue(jitem(sc1, k1), 0).value(), m1In);
    assertEquals(jvalue(jitem(sc1, k1), 1).value(), m2In);

    JsValue sc1out = ConfigJSON.writeConfig(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jitem(sc1, k1)).value(), m1In);
  }

  @Test
  public void TestByteMatrixItem() {
    // Should allow matrix values
    ByteMatrixKey k1 = new ByteMatrixKey("myMatrix");
    byte[][] m1In = new byte[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    ByteMatrix m1 = ByteMatrix(m1In);
    SetupConfig sc1 = new SetupConfig(ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jitem(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jitem(sc1, k1)).value()[1], m1In[1]));
    assertEquals(jvalue(jitem(sc1, k1)).value()[2][2], 9);

    JsValue sc1out = ConfigJSON.writeConfig(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jitem(sc1in, k1)), m1);
  }

  @Test
  public void TestByteArrayItem() {
    // Should allow arraye values
    ByteArrayKey k1 = new ByteArrayKey("myArray");
    byte[] m1In = new byte[]{1, 2, 3};
    ByteArray m1 = ByteArray(m1In);
    SetupConfig sc1 = new SetupConfig(ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jitem(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jitem(sc1, k1)).value(), m1In));
    assertTrue(jvalue(jitem(sc1, k1)).value()[2] == 3);

    JsValue sc1out = ConfigJSON.writeConfig(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jitem(sc1, k1)), m1);
  }

  @Test
  public void TestShortMatrixItem() {
    // Should allow matrix values
    ShortMatrixKey k1 = new ShortMatrixKey("myMatrix");
    short[][] m1In = new short[][ ]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    ShortMatrix m1 = ShortMatrix(m1In);
    SetupConfig sc1 = jadd(SetupConfig(ck), jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jitem(sc1, k1)), m1);
    assertTrue(Arrays.equals(jvalue(jitem(sc1, k1)).value()[1], m1In[1]));
    assertTrue(jvalue(jitem(sc1, k1)).value()[2][2] == 9);

    JsValue sc1out = ConfigJSON.writeConfig(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jitem(sc1in, k1)), m1);
  }


  @Test
  public void TestShortArrayItem() {
    // Should allow array values
    ShortArrayKey k1 = ShortArrayKey("myArray");
    ShortArrayKey k2 = ShortArrayKey("myArray2");
    short[] m1In = new short[]{1, 2, 3};
    ShortArray m1 = ShortArray(m1In);
    SetupConfig sc1 = jadd(SetupConfig(ck), jset(k1, m1));
    // Just checking to see if adding second works
    sc1 = jadd(sc1, jset(k2, m1));
    assertTrue(sc1.size() == 2);
    assertEquals(jvalue(jitem(sc1, k1)), m1);
    assertEquals(jvalue(jitem(sc1, k1)).value(), m1In);
    assertTrue(jvalue(jitem(sc1, k1)).value()[2] == 3);

    JsValue sc1out = ConfigJSON.writeConfig(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jitem(sc1in, k1)), m1);
  }

  @Test
  public void TestLongMatrixItem() {
    // Should allow matrix values
    LongMatrixKey k1 = LongMatrixKey("myMatrix");
    long[][] m1In = new long[][ ]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    LongMatrix m1 = LongMatrix(m1In);
    SetupConfig sc1 = SetupConfig(ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jitem(sc1, k1)), m1);
    assertEquals(jvalue(jitem(sc1, k1)).value()[2], m1In[2]);
    assertTrue(jvalue(jitem(sc1, k1)).value()[2][2] == 9);

    JsValue sc1out = ConfigJSON.writeConfig(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jitem(sc1in, k1)), m1);
  }

  @Test
  public void TestLongArrayItem() {
    // Should allow array values
    LongArrayKey k1 = LongArrayKey("myArray");
    long[] m1In = new long[]{1, 2, 3};
    LongArray m1 = LongArray(m1In);
    SetupConfig sc1 = SetupConfig(ck).add(jset(k1, m1));
    assertTrue(sc1.size() == 1);
    assertEquals(jvalue(jitem(sc1, k1)), m1);
    assertEquals(jvalue(jitem(sc1, k1)).value(), m1In);
    assertTrue(jvalue(jitem(sc1, k1)).value()[2] == 3);

    JsValue sc1out = ConfigJSON.writeConfig(sc1);
    //System.out.println("sc1out: " + sc1out.prettyPrint());

    SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
    assertEquals(sc1, sc1in);
    assertEquals(jvalue(jitem(sc1in, k1)), m1);
  }
}
