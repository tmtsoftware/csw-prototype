package javacsw.util.config3;

import csw.util.config3.*;
import csw.util.config3.Configurations.SetupConfig;
import org.junit.Test;
import spray.json.JsValue;

/**
 */
@SuppressWarnings("unused")
public class JSONTests {
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
        assert(sub.equals(wfos));
    }

    @Test
    public void TestCustomRaDecItem() {
        GenericKey<RaDec> k1 = new GenericKey<>("RaDec", "coords", RaDec.raDecFormat());
        RaDec c1 = new RaDec(7.3, 12.1);
        RaDec c2 = new RaDec(9.1, 2.9);
        Item<RaDec, RaDec> i1 = k1.jset(c1, c2);
        SetupConfig sc1 = new SetupConfig(ck).add(i1);
        assert(sc1.get(k1).get().values().size() == 2);
        assert(sc1.get(k1).get().jget(0).equals(c1));
        assert(sc1.get(k1).get().jget(1).equals(c2));

        JsValue sc1out = ConfigJSON.writeConfig(sc1);
        System.out.println("sc1out: " + sc1out.prettyPrint());

        SetupConfig sc1in = ConfigJSON.readConfig(sc1out);
        assert(sc1.equals(sc1in));
        assert(sc1in.get(k1).get().values().size() == 2);
        assert(sc1in.get(k1).get().jget(0).equals(c1));
        assert(sc1in.get(k1).get().jget(1).equals(c2));
        RaDec cc1 = sc1in.get(k1).get().jget(0);
        assert(cc1.ra() == 7.3);
        assert(cc1.dec() == 12.1);
        RaDec cc2 = sc1in.get(k1).get().jget(1);
        assert(cc2.ra() == 9.1);
        assert(cc2.dec() == 2.9);

        SetupConfig sc2 = new SetupConfig(ck).jset(k1, JUnitsOfMeasure.NoUnits, c1, c2);
        assert(sc2.equals(sc1));
    }

    @Test
    public void TestConcreteItems() {
        // char item encode/decode
        CharKey k1 = new CharKey(s3);
        CharItem i1 = k1.jset('d');
        JsValue j1 = ConfigJSON.charItemFormat().write(i1);
        CharItem in1 = ConfigJSON.charItemFormat().read(j1);
        assert (in1.equals(i1));
    }

    @Test
    public void TestConcreteItemsShortItemEncodeDecode() {
        ShortKey k1 = new ShortKey(s3);
        short s = -1;
        ShortItem i1 = k1.jset(s).withUnits(JUnitsOfMeasure.NoUnits);

//        val j1 = i1.toJson
//        val in1 = j1.convertTo[ShortItem]
//        assert (in1 == i1)
    }

    //  describe("Test concrete items") {
//    it("short item encode/decode") {
//      val k1 = ShortKey(s3)
//      val i1 = k1.set(-1).withUnits(UnitsOfMeasure.NoUnits)
//
//      val j1 = i1.toJson
//      val in1 = j1.convertTo[ShortItem]
//      assert(in1 == i1)
//    }
//
//    it("int item encode/decode") {
//      val k1 = IntKey(s3)
//      val i1 = k1.set(23).withUnits(UnitsOfMeasure.NoUnits)
//
//      val j1 = i1.toJson
//      val in1 = j1.convertTo[IntItem]
//      assert(in1 == i1)
//    }
//
//    it("long item encode/decode") {
//      val k1 = LongKey(s1)
//      val i1 = k1.set(123456L).withUnits(UnitsOfMeasure.NoUnits)
//
//      val j1 = i1.toJson
//      val in1 = j1.convertTo[LongItem]
//      assert(in1 == i1)
//    }
//
//    it("float item encode/decode") {
//      val k1 = FloatKey(s1)
//      val i1 = k1.set(123.456f).withUnits(UnitsOfMeasure.NoUnits)
//
//      val j1 = i1.toJson
//      val in1 = j1.convertTo[FloatItem]
//      assert(in1 == i1)
//    }
//
//    it("double item encode/decode") {
//      val k1 = DoubleKey(s1)
//      val i1 = k1.set(123.456).withUnits(UnitsOfMeasure.NoUnits)
//
//      val j1 = i1.toJson
//      val in1 = j1.convertTo[DoubleItem]
//      assert(in1 == i1)
//    }
//
//    it("boolean item encode/decode") {
//      val k1 = BooleanKey(s1)
//      val i1 = k1.set(true, false).withUnits(UnitsOfMeasure.NoUnits)
//
//      val j1 = i1.toJson
//      info("j1: " + j1)
//      val in1 = j1.convertTo[BooleanItem]
//      assert(in1 == i1)
//
//      val i2 = k1.set(true)
//
//      val j2 = i2.toJson
//      val in2 = j2.convertTo[BooleanItem]
//      assert(in2 == i2)
//    }
//
//    it("string item encode/decode") {
//      val k1 = StringKey(s2)
//      val i1 = k1.set("Blue", "Green").withUnits(UnitsOfMeasure.NoUnits)
//
//      val j1 = i1.toJson
//      val in1 = j1.convertTo[StringItem]
//      assert(in1 == i1)
//    }
//  }
//
//  describe("Testing Items") {
//
//    val k1 = IntKey(s1)
//    val k2 = StringKey(s2)
//
//    val i1 = k1.set(22, 33, 44)
//    val i2 = k2.set("a", "b", "c").withUnits(UnitsOfMeasure.Deg)
//
//    it("should encode and decode items list") {
//      // Use this to get a list to test
//      val sc1 = SetupConfig(ck).add(i1).add(i2)
//      val items = sc1.items
//
//      val js3 = ConfigJSON.itemsFormat.write(items)
//      val in1 = ConfigJSON.itemsFormat.read(js3)
//      assert(in1 == items)
//    }
//  }
//
//  describe("SetupConfig JSON") {
//
//    val k1 = CharKey("a")
//    val k2 = IntKey("b")
//    val k3 = LongKey("c")
//    val k4 = FloatKey("d")
//    val k5 = DoubleKey("e")
//    val k6 = BooleanKey("f")
//    val k7 = StringKey("g")
//
//    val i1 = k1.set('d').withUnits(UnitsOfMeasure.NoUnits)
//    val i2 = k2.set(22).withUnits(UnitsOfMeasure.NoUnits)
//    val i3 = k3.set(1234L).withUnits(UnitsOfMeasure.NoUnits)
//    val i4 = k4.set(123.45f).withUnits(UnitsOfMeasure.Deg)
//    val i5 = k5.set(123.456).withUnits(UnitsOfMeasure.Meters)
//    val i6 = k6.set(false)
//    val i7 = k7.set("GG495").withUnits(UnitsOfMeasure.Deg)
//
//    it("Should encode/decode a setupconfig") {
//      val sc1 = SetupConfig(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7)
//      assert(sc1.size == 7)
//
//      val sc1out = ConfigJSON.writeConfig(sc1)
//      info("sc1out: " + sc1out.prettyPrint)
//      val sc1in = ConfigJSON.readConfig(sc1out)
//    }
//  }
//
//  describe("Test GenericItem") {
//    it("Should allow a GenericItem with a custom type") {
//      val k1 = GenericKey[MyData2]("MyData2", "testData")
//      val d1 = MyData2(1, 2.0f, 3.0, "4")
//      val d2 = MyData2(10, 20.0f, 30.0, "40")
//      val i1 = k1.set(d1, d2).withUnits(UnitsOfMeasure.Meters)
//      val sc1 = SetupConfig(ck).add(i1)
//      assert(sc1.get(k1).get.value.size == 2)
//      assert(sc1.get(k1).get.value(0) == d1)
//      assert(sc1.get(k1).get.value(1) == d2)
//      assert(sc1.get(k1).get.units == UnitsOfMeasure.Meters)
//
//      val sc1out = ConfigJSON.writeConfig(sc1)
//      info("sc1out: " + sc1out.prettyPrint)
//
//      val sc1in = ConfigJSON.readConfig(sc1out)
//      assert(sc1.equals(sc1in))
//      assert(sc1in.get(k1).get.value.size == 2)
//      assert(sc1in.get(k1).get.value(0) == d1)
//      assert(sc1in.get(k1).get.value(1) == d2)
//      assert(sc1in.get(k1).get.units == UnitsOfMeasure.Meters)
//
//      val sc2 = SetupConfig(ck).set(k1, UnitsOfMeasure.Meters, d1, d2)
//      assert(sc2 == sc1)
//    }
//
//    describe("Test Custom RaDecItem") {
//      it("Should allow cutom RaDecItem") {
//        val k1 = GenericKey[RaDec]("RaDec", "coords")
//        val c1 = RaDec(7.3, 12.1)
//        val c2 = RaDec(9.1, 2.9)
//        val i1 = k1.set(c1, c2)
//        val sc1 = SetupConfig(ck).add(i1)
//        assert(sc1.get(k1).get.value.size == 2)
//        assert(sc1.get(k1).get.value(0) == c1)
//        assert(sc1.get(k1).get.value(1) == c2)
//
//        val sc1out = ConfigJSON.writeConfig(sc1)
//        info("sc1out: " + sc1out.prettyPrint)
//
//        val sc1in = ConfigJSON.readConfig(sc1out)
//        assert(sc1.equals(sc1in))
//        assert(sc1in.get(k1).get.value.size == 2)
//        assert(sc1in.get(k1).get.value(0) == c1)
//        assert(sc1in.get(k1).get.value(1) == c2)
//
//        val sc2 = SetupConfig(ck).set(k1, UnitsOfMeasure.NoUnits, c1, c2)
//        assert(sc2 == sc1)
//      }
//    }
//  }

}
