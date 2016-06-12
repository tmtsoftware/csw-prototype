package javacsw.util.config;

import csw.util.config.*;
import csw.util.config.Configurations.*;
import csw.util.config.Events.*;
import org.junit.Test;
import spray.json.JsValue;

import java.util.Arrays;

import static javacsw.util.config.JUnitsOfMeasure.*;
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

    @Test
    public void testCustomRaDecItem() {
        GenericKey<RaDec> k1 = new GenericKey<>("RaDec", "coords", RaDec.raDecFormat());
        RaDec c1 = new RaDec(7.3, 12.1);
        RaDec c2 = new RaDec(9.1, 2.9);
        Item<RaDec, RaDec> i1 = k1.jset(c1, c2);
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

    @Test
    public void testConcreteItems() {
        // char item encode/decode
        {
            CharKey k1 = new CharKey(s3);
            CharItem i1 = k1.jset('d');
            JsValue j1 = ConfigJSON.charItemFormat().write(i1);
            CharItem in1 = ConfigJSON.charItemFormat().read(j1);
            assertTrue(in1.equals(i1));
        }
        // short item encode/decode
        {
            ShortKey k1 = new ShortKey(s3);
            short s = -1;
            ShortItem i1 = k1.jset(s).withUnits(JUnitsOfMeasure.NoUnits);
            JsValue j1 = ConfigJSON.shortItemFormat().write(i1);
            ShortItem in1 = ConfigJSON.shortItemFormat().read(j1);
            assertTrue(in1.equals(i1));
        }
        // int item encode/decode
        {
            IntKey k1 = new IntKey(s3);
            int i = -1;
            IntItem i1 = k1.jset(i).withUnits(JUnitsOfMeasure.NoUnits);
            JsValue j1 = ConfigJSON.intItemFormat().write(i1);
            IntItem in1 = ConfigJSON.intItemFormat().read(j1);
            assertTrue(in1.equals(i1));
        }
        // long item encode/decode
        {
            LongKey k1 = new LongKey(s3);
            long l = 123456L;
            LongItem i1 = k1.jset(l).withUnits(JUnitsOfMeasure.NoUnits);
            JsValue j1 = ConfigJSON.longItemFormat().write(i1);
            LongItem in1 = ConfigJSON.longItemFormat().read(j1);
            assertTrue(in1.equals(i1));
        }
        // float item encode/decode
        {
            FloatKey k1 = new FloatKey(s3);
            float f = 123.456f;
            FloatItem i1 = k1.jset(f).withUnits(JUnitsOfMeasure.NoUnits);
            JsValue j1 = ConfigJSON.floatItemFormat().write(i1);
            FloatItem in1 = ConfigJSON.floatItemFormat().read(j1);
            assertTrue(in1.equals(i1));
        }
        // double item encode/decode
        {
            DoubleKey k1 = new DoubleKey(s3);
            double f = 123.456;
            DoubleItem i1 = k1.jset(f).withUnits(JUnitsOfMeasure.NoUnits);
            JsValue j1 = ConfigJSON.doubleItemFormat().write(i1);
            DoubleItem in1 = ConfigJSON.doubleItemFormat().read(j1);
            assertTrue(in1.equals(i1));
        }
        // boolean item encode/decode
        {
            BooleanKey k1 = new BooleanKey(s3);
            BooleanItem i1 = k1.jset(true, false).withUnits(JUnitsOfMeasure.NoUnits);
            JsValue j1 = ConfigJSON.booleanItemFormat().write(i1);
            BooleanItem in1 = ConfigJSON.booleanItemFormat().read(j1);
            assertTrue(in1.equals(i1));
        }
        // string item encode/decode
        {
            StringKey k1 = new StringKey(s3);
            StringItem i1 = k1.jset("Blue", "Green").withUnits(JUnitsOfMeasure.NoUnits);
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

        CharItem i1 = k1.jset('d').withUnits(NoUnits);
        IntItem i2 = k2.jset(22).withUnits(NoUnits);
        LongItem i3 = k3.jset(1234L).withUnits(NoUnits);
        FloatItem i4 = k4.jset(123.45f).withUnits(Deg);
        DoubleItem i5 = k5.jset(123.456).withUnits(Meters);
        BooleanItem i6 = k6.jset(false);
        StringItem i7 = k7.jset("GG495").withUnits(Deg);

        // Should encode/decode a SetupConfig
        {
            SetupConfig c1 = new SetupConfig(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
            assertTrue(c1.size() == 7);
            JsValue c1out = ConfigJSON.writeConfig(c1);
            SetupConfig c1in = ConfigJSON.readConfig(c1out);
            assertTrue(c1in.jvalue(k3) == 1234L);
            assertTrue(c1.equals(c1in));
        }
        // Should encode/decode a ObserveConfig
        {
            ObserveConfig c1 = new ObserveConfig(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
            assertTrue(c1.size() == 7);
            JsValue c1out = ConfigJSON.writeConfig(c1);
            ObserveConfig c1in = ConfigJSON.readConfig(c1out);
            assertTrue(c1in.jvalue(k3) == 1234L);
            assertTrue(c1.equals(c1in));
        }
        // Should encode/decode a StatusEvent
        {
            StatusEvent e1 = new StatusEvent("wfos.test").add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
            assertTrue(e1.size() == 7);
            JsValue e1out = ConfigJSON.writeEvent(e1);
            StatusEvent e1in = ConfigJSON.readEvent(e1out);
            assertTrue(e1in.jvalue(k3) == 1234L);
            assertTrue(e1.equals(e1in));
        }
        // Should encode/decode a ObserveEvent
        {
            ObserveEvent e1 = new ObserveEvent("wfos.test").add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
            assertTrue(e1.size() == 7);
            JsValue e1out = ConfigJSON.writeEvent(e1);
            ObserveEvent e1in = ConfigJSON.readEvent(e1out);
            assertTrue(e1in.jvalue(k3) == 1234L);
            assertTrue(e1.equals(e1in));
        }
        // Should encode/decode a SystemEvent
        {
            SystemEvent e1 = new SystemEvent("wfos.test").add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7);
            assertTrue(e1.size() == 7);
            JsValue e1out = ConfigJSON.writeEvent(e1);
            SystemEvent e1in = ConfigJSON.readEvent(e1out);
            assertTrue(e1in.jvalue(k3) == 1234L);
            assertTrue(e1.equals(e1in));
        }
    }

    @Test
    public void TestDoubleMatrixItem() {
        // Should allow matrix values
        DoubleMatrixKey k1 = new DoubleMatrixKey("myMatrix");
        JDoubleMatrix m1 = new JDoubleMatrix(Arrays.asList(
                Arrays.asList(1.0, 2.0, 3.0),
                Arrays.asList(4.1, 5.1, 6.1),
                Arrays.asList(7.2, 8.2, 9.2)));
        SetupConfig sc1 = new SetupConfig(ck).jset(k1, m1);
        assertTrue(sc1.size() == 1);
        assertTrue(sc1.jvalue(k1).equals(m1));
        assertTrue(sc1.jvalue(k1).value().get(1).equals(Arrays.asList(4.1, 5.1, 6.1)));

        JsValue sc1out = ConfigJSON.writeConfig(sc1);
//        System.out.println("sc1out: " + sc1out.prettyPrint());

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assertTrue(sc1.equals(sc1in));
        assertTrue(sc1in.jvalue(k1).equals(m1));
    }

    @Test
    public void TestDoubleVectorItem() {
        // Should allow vector values
        DoubleVectorKey k1 = new DoubleVectorKey("myVector");
        JDoubleVector m1 = JDoubleVector.fromArray(new double[]{1.0, 2.0, 3.0});
        SetupConfig sc1 = new SetupConfig(ck).jset(k1, m1);
        assertTrue(sc1.size() == 1);
        assertTrue(sc1.jvalue(k1).equals(m1));
        assertTrue(sc1.jvalue(k1).value().equals(Arrays.asList(1.0, 2.0, 3.0)));

        JsValue sc1out = ConfigJSON.writeConfig(sc1);
//        System.out.println("sc1out: " + sc1out.prettyPrint());

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assertTrue(sc1.equals(sc1in));
        assertTrue(sc1in.jvalue(k1).equals(m1));
    }

    @Test
    public void TestFloatMatrixItem() {
        // Should allow matrix values
        FloatMatrixKey k1 = new FloatMatrixKey("myMatrix");
        JFloatMatrix m1 = new JFloatMatrix(Arrays.asList(
                Arrays.asList(1.0f, 2.0f, 3.0f),
                Arrays.asList(4.1f, 5.1f, 6.1f),
                Arrays.asList(7.2f, 8.2f, 9.2f)));
        SetupConfig sc1 = new SetupConfig(ck).jset(k1, m1);
        assertTrue(sc1.size() == 1);
        assertTrue(sc1.jvalue(k1).equals(m1));
        assertTrue(sc1.jvalue(k1).value().get(1).equals(Arrays.asList(4.1f, 5.1f, 6.1f)));

        JsValue sc1out = ConfigJSON.writeConfig(sc1);
//        System.out.println("sc1out: " + sc1out.prettyPrint());

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assertTrue(sc1.equals(sc1in));
        assertTrue(sc1in.jvalue(k1).equals(m1));
    }

    @Test
    public void TestFloatVectorItem() {
        // Should allow vector values
        FloatVectorKey k1 = new FloatVectorKey("myVector");
        JFloatVector m1 = JFloatVector.fromArray(new float[]{1.0f, 2.0f, 3.0f});
        SetupConfig sc1 = new SetupConfig(ck).jset(k1, m1);
        assertTrue(sc1.size() == 1);
        assertTrue(sc1.jvalue(k1).equals(m1));
        assertTrue(sc1.jvalue(k1).value().equals(Arrays.asList(1.0f, 2.0f, 3.0f)));

        JsValue sc1out = ConfigJSON.writeConfig(sc1);
//        System.out.println("sc1out: " + sc1out.prettyPrint());

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assertTrue(sc1.equals(sc1in));
        assertTrue(sc1in.jvalue(k1).equals(m1));
    }

    @Test
    public void TestIntMatrixItem() {
        // Should allow matrix values
        IntMatrixKey k1 = new IntMatrixKey("myMatrix");
        JIntMatrix m1 = new JIntMatrix(Arrays.asList(
                Arrays.asList(1, 2, 3),
                Arrays.asList(4, 5, 6),
                Arrays.asList(7, 8, 9)));
        SetupConfig sc1 = new SetupConfig(ck).jset(k1, m1);
        assertTrue(sc1.size() == 1);
        assertTrue(sc1.jvalue(k1).equals(m1));
        assertTrue(sc1.jvalue(k1).value().get(1).equals(Arrays.asList(4, 5, 6)));

        JsValue sc1out = ConfigJSON.writeConfig(sc1);
//        System.out.println("sc1out: " + sc1out.prettyPrint());

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assertTrue(sc1.equals(sc1in));
        assertTrue(sc1in.jvalue(k1).equals(m1));
    }

    @Test
    public void TestIntVectorItem() {
        // Should allow vector values
        IntVectorKey k1 = new IntVectorKey("myVector");
        JIntVector m1 = new JIntVector(Arrays.asList(1, 2, 3));
        SetupConfig sc1 = new SetupConfig(ck).jset(k1, m1);
        assertTrue(sc1.size() == 1);
        assertTrue(sc1.jvalue(k1).equals(m1));
        assertTrue(sc1.jvalue(k1).value().equals(Arrays.asList(1, 2, 3)));

        JsValue sc1out = ConfigJSON.writeConfig(sc1);
//        System.out.println("sc1out: " + sc1out.prettyPrint());

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assertTrue(sc1.equals(sc1in));
        assertTrue(sc1in.jvalue(k1).equals(m1));
    }

    @Test
    public void TestByteMatrixItem() {
        // Should allow matrix values
        ByteMatrixKey k1 = new ByteMatrixKey("myMatrix");
        JByteMatrix m1 = JByteMatrix.fromArray(new byte[][]{
                {1, 2, 3},
                {4, 5, 6},
                {7, 8, 9}});
        SetupConfig sc1 = new SetupConfig(ck).jset(k1, m1);
        assertTrue(sc1.size() == 1);
        assertTrue(sc1.jvalue(k1).equals(m1));
        assertTrue(sc1.jvalue(k1).value().get(1).equals(Arrays.asList((byte)4, (byte)5, (byte)6)));
        assertTrue(sc1.jvalue(k1).value().get(2).get(2) == 9);

        JsValue sc1out = ConfigJSON.writeConfig(sc1);

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assertTrue(sc1.equals(sc1in));
        assertTrue(sc1in.jvalue(k1).equals(m1));
    }

    @Test
    public void TestByteVectorItem() {
        // Should allow vector values
        ByteVectorKey k1 = new ByteVectorKey("myVector");
        JByteVector m1 = JByteVector.fromArray(new byte[]{1, 2, 3});
        SetupConfig sc1 = new SetupConfig(ck).jset(k1, m1);
        assertTrue(sc1.size() == 1);
        assertTrue(sc1.jvalue(k1).equals(m1));
        assertTrue(sc1.jvalue(k1).value().equals(Arrays.asList((byte) 1, (byte) 2, (byte) 3)));
        assertTrue(sc1.jvalue(k1).value().get(2) == 3);

        JsValue sc1out = ConfigJSON.writeConfig(sc1);
//        System.out.println("sc1out: " + sc1out.prettyPrint());

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assertTrue(sc1.equals(sc1in));
        assertTrue(sc1in.jvalue(k1).equals(m1));
    }

    @Test
    public void TestShortMatrixItem() {
        // Should allow matrix values
        ShortMatrixKey k1 = new ShortMatrixKey("myMatrix");
        JShortMatrix m1 = JShortMatrix.fromArray(new short[][]{
                {1, 2, 3},
                {4, 5, 6},
                {7, 8, 9}});
        SetupConfig sc1 = new SetupConfig(ck).jset(k1, m1);
        assertTrue(sc1.size() == 1);
        assertTrue(sc1.jvalue(k1).equals(m1));
        assertTrue(sc1.jvalue(k1).value().get(1).equals(Arrays.asList((short)4, (short)5, (short)6)));
        assertTrue(sc1.jvalue(k1).value().get(2).get(2) == 9);

        JsValue sc1out = ConfigJSON.writeConfig(sc1);

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assertTrue(sc1.equals(sc1in));
        assertTrue(sc1in.jvalue(k1).equals(m1));
    }

    @Test
    public void TestShortVectorItem() {
        // Should allow vector values
        ShortVectorKey k1 = new ShortVectorKey("myVector");
        JShortVector m1 = JShortVector.fromArray(new short[]{1, 2, 3});
        SetupConfig sc1 = new SetupConfig(ck).jset(k1, m1);
        assertTrue(sc1.size() == 1);
        assertTrue(sc1.jvalue(k1).equals(m1));
        assertTrue(sc1.jvalue(k1).value().equals(Arrays.asList((short) 1, (short) 2, (short) 3)));
        assertTrue(sc1.jvalue(k1).value().get(2) == 3);

        JsValue sc1out = ConfigJSON.writeConfig(sc1);
//        System.out.println("sc1out: " + sc1out.prettyPrint());

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assertTrue(sc1.equals(sc1in));
        assertTrue(sc1in.jvalue(k1).equals(m1));
    }


    @Test
    public void TestLongMatrixItem() {
        // Should allow matrix values
        LongMatrixKey k1 = new LongMatrixKey("myMatrix");
        JLongMatrix m1 = JLongMatrix.fromArray(new long[][]{
                {1, 2, 3},
                {4, 5, 6},
                {7, 8, 9}});
        SetupConfig sc1 = new SetupConfig(ck).jset(k1, m1);
        assertTrue(sc1.size() == 1);
        assertTrue(sc1.jvalue(k1).equals(m1));
        assertTrue(sc1.jvalue(k1).value().get(1).equals(Arrays.asList((long)4, (long)5, (long)6)));
        assertTrue(sc1.jvalue(k1).value().get(2).get(2) == 9);

        JsValue sc1out = ConfigJSON.writeConfig(sc1);

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assertTrue(sc1.equals(sc1in));
        assertTrue(sc1in.jvalue(k1).equals(m1));
    }

    @Test
    public void TestLongVectorItem() {
        // Should allow vector values
        LongVectorKey k1 = new LongVectorKey("myVector");
        JLongVector m1 = JLongVector.fromArray(new long[]{1, 2, 3});
        SetupConfig sc1 = new SetupConfig(ck).jset(k1, m1);
        assertTrue(sc1.size() == 1);
        assertTrue(sc1.jvalue(k1).equals(m1));
        assertTrue(sc1.jvalue(k1).value().equals(Arrays.asList((long) 1, (long) 2, (long) 3)));
        assertTrue(sc1.jvalue(k1).value().get(2) == 3);

        JsValue sc1out = ConfigJSON.writeConfig(sc1);
//        System.out.println("sc1out: " + sc1out.prettyPrint());

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assertTrue(sc1.equals(sc1in));
        assertTrue(sc1in.jvalue(k1).equals(m1));
    }

}
