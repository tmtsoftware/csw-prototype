package javacsw.util.config3;

import csw.util.config3.ConfigItems.CharItem;
import csw.util.config3.ConfigItems.CharKey;
import csw.util.config3.ConfigJSON;
import csw.util.config3.Subsystem;
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
    public void TestConcreteItems() {
        // char item encode/decode
        CharKey k1 = new CharKey(s3);
        CharItem i1 = k1.jset('d');
        JsValue j1 = ConfigJSON.charItemFormat().write(i1);
        CharItem in1 = ConfigJSON.charItemFormat().read(j1);
        assert(in1.equals(i1));

        // short item encode/decode
    }
}
//  describe("Test concrete items") {
//
//    it("short item encode/decode") {
//      val k1 = ShortKey(s3)
//      val i1 = k1.set(Vector[Short](-1), UnitsOfMeasure.NoUnits)
//
//      val j1 = i1.toJson
//      val in1 = j1.convertTo[ShortItem]
//      assert(in1 == i1)
//    }
//
//    it("int item encode/decode") {
//      val k1 = IntKey(s3)
//      val i1 = k1.set(Vector[Int](123), UnitsOfMeasure.NoUnits)
//
//      val j1 = i1.toJson
//      val in1 = j1.convertTo[IntItem]
//      assert(in1 == i1)
//    }
//
//    it("long item encode/decode") {
//      val k1 = LongKey(s1)
//      val i1 = k1.set(Vector(123456L), UnitsOfMeasure.NoUnits)
//
//      val j1 = i1.toJson
//      val in1 = j1.convertTo[LongItem]
//      assert(in1 == i1)
//    }
//
//    it("float item encode/decode") {
//      val k1 = FloatKey(s1)
//      val i1 = k1.set(Vector[Float](123.456f), UnitsOfMeasure.NoUnits)
//
//      val j1 = i1.toJson
//      val in1 = j1.convertTo[FloatItem]
//      assert(in1 == i1)
//    }
//
//    it("double item encode/decode") {
//      val k1 = DoubleKey(s1)
//      val i1 = k1.set(Vector[Double](123.456), UnitsOfMeasure.NoUnits)
//
//      val j1 = i1.toJson
//      val in1 = j1.convertTo[DoubleItem]
//      assert(in1 == i1)
//    }
//
//    it("boolean item encode/decode") {
//      val k1 = BooleanKey(s1)
//      val i1 = k1.set(Vector(true, false), UnitsOfMeasure.NoUnits)
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
//      val i1 = k1.set(Vector("Blue", "Green"), UnitsOfMeasure.NoUnits)
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
//    val i2 = k2.set(Vector("a", "b", "c"), UnitsOfMeasure.Deg)
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
//    val i1 = k1.set(Vector('d'), UnitsOfMeasure.NoUnits)
//    val i2 = k2.set(Vector(22), UnitsOfMeasure.NoUnits)
//    val i3 = k3.set(Vector(1234L), UnitsOfMeasure.NoUnits)
//    val i4 = k4.set(Vector(123.45f), UnitsOfMeasure.Deg)
//    val i5 = k5.set(Vector(123.456), UnitsOfMeasure.Meters)
//    val i6 = k6.set(false)
//    val i7 = k7.set(Vector("GG495"), UnitsOfMeasure.Deg)
//
//    it ("Should encode/decode a setupconfig") {
//      val sc1 = SetupConfig(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7)
//      assert(sc1.size == 7)
//      //info("sc1: " + sc1)
//
//      val sc1out = ConfigJSON.writeConfig(sc1)
//      info("sc1out: " + sc1out.prettyPrint)
//      val sc1in = ConfigJSON.readConfig(sc1out)
//      //assert(sc1 == sc1in)
//    }
//  }
//
//  describe("Trying to understand CItem") {
//    it("Should allow a citem") {
//      val k1 = SingleKey[String]("bob")
//      val i1 = k1.set(Vector("1", "2", "3"), UnitsOfMeasure.NoUnits)
//      info("j1: " + i1)
//
//      val j1 = i1.toJson
//      info("j1citem: " + j1.prettyPrint)
//      val in1 = j1.convertTo[CItem[String]]
//      info("j1in: " + in1)
//    }
//  }
//}
//
