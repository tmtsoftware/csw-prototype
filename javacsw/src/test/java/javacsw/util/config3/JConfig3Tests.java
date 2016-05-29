package javacsw.util.config3;

import csw.util.config3.ConfigItems.*;
import csw.util.config3.Configurations.SetupConfig;
import org.junit.Test;

import java.util.Arrays;
import java.util.Objects;

/**
 * Tests the Java API to the config classes
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class JConfig3Tests {
    private static final String s1 = "encoder";
    private static final String s2 = "filter";
    private static final String s3 = "detectorTemp";

    private static final String ck = "wfos.blue.filter";
    private static final String ck1 = "wfos.prog.cloudcover";
    private static final String ck2 = "wfos.red.filter";
    private static final String ck3 = "wfos.red.detector";

    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    @Test
    public void basicKeyTests() {
        // Should be constructed properly
        IntKey k1 = new IntKey(s1);
        StringKey k2 = new StringKey(s2);
        assert (Objects.equals(k1.keyName(), s1));

        // Should use set properly
        IntItem i = k1.jset(22);
        assert (Objects.equals(i.keyName(), s1));
        assert (i.jvalue().get(0) == 22);
        assert (i.jvalue(0) == 22);
        assert (i.units() == JUnitsOfMeasure.NoUnits);

        assert (Objects.equals(k2.keyName(), s2));
        StringItem j = k2.jset("Bob");
        assert (Objects.equals(j.jvalue(0), "Bob"));

        // Should support equality of keys
        IntKey k3 = new IntKey(s1);
        assert (k3.equals(k1));
        assert (!k3.equals(k2));
        assert (!k1.equals(k2));
    }

    @Test
    public void basicArrayTests() {
        SingleKey<Integer> k1 = new SingleKey<>("atest");

        // "Should allow an Int array"
        CItem<Integer> i1 = k1.jset(Arrays.asList(1, 2, 3), JUnitsOfMeasure.NoUnits);
        assert(i1.value().size() == 3);
        assert(i1.jvalue(0) == 1);
        assert(i1.jvalue(1) == 2);
        assert(i1.jvalue(2) == 3);
        CItem<Integer> i2 = k1.jset(1, 2, 3);
        assert(i2.value().equals(i1.value()));
        assert(i2.units() == JUnitsOfMeasure.NoUnits);

        // Should use key equals
        SingleKey<Integer> k2 = new SingleKey<>("atest1");
        SingleKey<Integer> k3 = new SingleKey<>("atest");
        SingleKey<Float> k4 = new SingleKey<>("atest");
        assert (!k1.equals(k2));
        assert (!k2.equals(k3));
    }

    @Test
    public void CheckingKeyUpdates() {
        IntKey k1 = new IntKey("atest");

        // Should allow updates
        IntItem i1 = k1.jset(22);
        assert (i1.jvalue(0) == 22);
        assert (i1.units() == JUnitsOfMeasure.NoUnits);
        IntItem i2 = k1.jset(33);
        assert (i2.jvalue(0) == 33);
        assert (i2.units() == JUnitsOfMeasure.NoUnits);

        SetupConfig sc = new SetupConfig(ck1).add(i1);
//        JSetupConfig sc = JConfigurations.createSetupConfig(ck1).set(k1, Collections.singletonList(22), JUnitsOfMeasure.NoUnits);
        scala.Int i = sc.jget(k1, 0);
//        assert (sc.jget(k1).get() == 22);
//        sc = sc.add(i2)
//        assert (sc.get(k1).get.value == Vector(33))
    }
}

//    }
//  }
//
//  describe("Test for conversions from Java") {
//    it("should allow setting from Java objects") {
//      val tval = new java.lang.Long(1234)
//      val k1 = LongKey(s1)
//      val i1 = k1.set(tval)
//      assert(i1.value == Vector(1234L))
//
//      val tval2 = 4567L
//      val k2 = LongKey(s1)
//      val i2 = k2.set(tval2)
//      assert(i2.value == Vector(4567L))
//    }
//  }
//
//  describe("SC Test") {
//
//    val k1 = IntKey("encoder")
//    val k2 = IntKey("windspeed")
//    it("Should allow adding") {
//      var sc1 = SetupConfig(ck3)
//      val i1 = k1.set(Vector(22), UnitsOfMeasure.NoUnits)
//      val i2 = k2.set(Vector(44), UnitsOfMeasure.NoUnits)
//      sc1 = sc1.add(i1).add(i2)
//      assert(sc1.size == 2)
//      assert(sc1.exists(k1))
//      assert(sc1.exists(k2))
//    }
//
//    it("Should allow setting") {
//      var sc1 = SetupConfig(ck1)
//      sc1 = sc1.set(k1, Vector(22), UnitsOfMeasure.NoUnits).set(k2, Vector(44), UnitsOfMeasure.NoUnits)
//      assert(sc1.size == 2)
//      assert(sc1.exists(k1))
//      assert(sc1.exists(k2))
//    }
//
//    it("Should allow apply") {
//      var sc1 = SetupConfig(ck1)
//      sc1 = sc1.set(k1, Vector(22), UnitsOfMeasure.NoUnits).set(k2, Vector(44), UnitsOfMeasure.NoUnits)
//
//      val v1 = sc1(k1)
//      val v2 = sc1(k2)
//      assert(sc1.get(k1) != None)
//      assert(sc1.get(k2) != None)
//      assert(v1 == Vector(22))
//      assert(v2 == Vector(44))
//    }
//
//    it ("should update for the same key with set") {
//      var sc1 = SetupConfig(ck1)
//      sc1 = sc1.set(k2, Vector(22), UnitsOfMeasure.NoUnits)
//      assert(sc1.exists(k2))
//      assert(sc1(k2) == Vector(22))
//
//      sc1 = sc1.set(k2, Vector(33), UnitsOfMeasure.NoUnits)
//      assert(sc1.exists(k2))
//      assert(sc1(k2) == Vector(33))
//    }
//
//    it ("should update for the same key with add") {
//      var sc1 = SetupConfig(ck1)
//      sc1 = sc1.add(k2.set(Vector(22), UnitsOfMeasure.NoUnits))
//      assert(sc1.exists(k2))
//      assert(sc1(k2) == Vector(22))
//
//      sc1 = sc1.add(k2.set(Vector(33), UnitsOfMeasure.NoUnits))
//      assert(sc1.exists(k2))
//      assert(sc1(k2) == Vector(33))
//    }
//
//  }
//
//  it ("should update for the same key with set") {
//    val k1 = IntKey("encoder")
//    val k2 = StringKey("windspeed")
//
//    var sc1 = SetupConfig(ck1)
//    sc1 = sc1.set(k1, Vector(22), UnitsOfMeasure.NoUnits)
//    assert(sc1.exists(k1))
//    assert(sc1(k1) == Vector(22))
//
//    sc1 = sc1.set(k2, Vector("bob"), UnitsOfMeasure.NoUnits)
//    assert(sc1.exists(k2))
//    assert(sc1(k2) == Vector("bob"))
//
//    sc1.items.foreach {
//      case _: IntItem => info("IntItem")
//      case _: StringItem => info("StringItem")
//    }
//  }
//
//
//  describe("testing new idea") {
//
//    val t1 = IntKey("test1")
//    it("should allow setting a single value") {
//      val i1 = t1.set(1)
//      assert(i1.value == Vector(1))
//      assert(i1.units == NoUnits)
//      assert(i1(0) == 1)
//    }
//    it("should allow setting several") {
//      val i1 = t1.set(1, 3, 5, 7)
//      assert(i1.value == Vector(1, 3, 5, 7))
//      assert(i1.units == NoUnits)
//      assert(i1(1) == 3)
//
//      val i2 = t1.set(10, 30, 50, 70).withUnits(UnitsOfMeasure.Deg)
//      assert(i2.value == Vector(10, 30, 50, 70))
//      assert(i2.units == UnitsOfMeasure.Deg)
//      assert(i2(1) == 30)
//    }
//    it("should also allow setting with sequence") {
//      val s1 = Vector(2, 4, 6, 8)
//      val i1 = t1.set(s1, Meters)
//      assert(i1.value == s1)
//      assert(i1.value.size == s1.size)
//      assert(i1.units == Meters)
//      assert(i1(2) == 6)
//    }
//  }
//}
