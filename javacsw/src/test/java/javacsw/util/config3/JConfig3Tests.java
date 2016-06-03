package javacsw.util.config3;

import csw.util.config3.Configurations.SetupConfig;
import csw.util.config3.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static javacsw.util.config3.JUnitsOfMeasure.NoUnits;

/**
 * Tests the Java API to the config classes
 */
@SuppressWarnings({"OptionalGetWithoutIsPresent", "unused"})
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
        assert (i.jvalue() == 22);
        assert (i.jvalue(0) == 22);
        assert (i.jget(0).get() == 22);
        assert (i.units() == NoUnits);

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
    public void CheckingKeyUpdates() {
        IntKey k1 = new IntKey("atest");

        // Should allow updates
        IntItem i1 = k1.jset(22);
        assert (i1.jvalue() == 22);
        assert (i1.jvalue(0) == 22);
        assert (i1.units() == NoUnits);
        IntItem i2 = k1.jset(33);
        assert (i2.jvalue() == 33);
        assert (i2.units() == NoUnits);

        SetupConfig sc = new SetupConfig(ck1).add(i1);
        assert (sc.jvalue(k1, 0) == 22);
        sc = sc.add(i2);
        assert (sc.jvalue(k1, 0) == 33);

        SetupConfig sc2 = new SetupConfig(ck1).jset(k1, 22);
        assert(sc2.jvalue(k1) == 22);
        assert(sc2.jvalues(k1).equals(Collections.singletonList(22)));
    }

    @Test
    public void TestLong() {
        // should allow setting from Long
        long tval = 1234L;
        LongKey k1 = new LongKey(s1);
        LongItem i1 = k1.jset(tval);
        assert (i1.jvalues().equals(Collections.singletonList(tval)));
        assert (i1.jvalue() == tval);
        assert (i1.jget(0).get() == tval);

        long tval2 = 4567L;
        LongKey k2 = new LongKey(s1);
        LongItem i2 = k2.jset(tval2);
        assert (i2.jvalue().equals(tval2));
        assert (i2.jvalues().equals(Collections.singletonList(tval2)));
    }

    @Test
    public void scTest() {
        IntKey k1 = new IntKey("encoder");
        IntKey k2 = new IntKey("windspeed");

        // Should allow adding keys
        {
            SetupConfig sc1 = new SetupConfig(ck3).jset(k1, 22).jset(k2, 44);
            assert (sc1.size() == 2);
            assert (sc1.exists(k1));
            assert (sc1.exists(k2));
            assert (sc1.jvalue(k1) == 22);
            assert (sc1.jvalue(k2) == 44);
        }

        // Should allow setting
        {
            SetupConfig sc1 = new SetupConfig(ck1);
            sc1 = sc1.jset(k1, NoUnits, 22).jset(k2, NoUnits, 44);
            assert (sc1.size() == 2);
            assert (sc1.exists(k1));
            assert (sc1.exists(k2));
        }

        // Should allow getting values
        {
            SetupConfig sc1 = new SetupConfig(ck1);
            sc1 = sc1.jset(k1, NoUnits, 22).jset(k2, NoUnits, 44);
            List<Integer> v1 = sc1.jvalues(k1);
            List<Integer> v2 = sc1.jvalues(k2);
            assert (sc1.jget(k1).isPresent());
            assert (sc1.jget(k2).isPresent());
            assert (v1.equals(Collections.singletonList(22)));
            assert (v2.equals(Collections.singletonList(44)));
        }
    }


//    it("should update for the same key with set") {
//      var sc1 = SetupConfig(ck1)
//      sc1 = sc1.set(k2, NoUnits, 22)
//      assert(sc1.exists(k2))
//      assert(sc1(k2) == Vector(22))
//
//      sc1 = sc1.set(k2, NoUnits, 33)
//      assert(sc1.exists(k2))
//      assert(sc1(k2) == Vector(33))
//    }
//
//    it("should update for the same key with add") {
//      var sc1 = SetupConfig(ck1)
//      sc1 = sc1.add(k2.set(22).withUnits(NoUnits))
//      assert(sc1.exists(k2))
//      assert(sc1(k2) == Vector(22))
//
//      sc1 = sc1.add(k2.set(33).withUnits(NoUnits))
//      assert(sc1.exists(k2))
//      assert(sc1(k2) == Vector(33))
//    }
//
//  }
//
//  it("should update for the same key with set") {
//    val k1 = IntKey("encoder")
//    val k2 = StringKey("windspeed")
//
//    var sc1 = SetupConfig(ck1)
//    sc1 = sc1.set(k1, NoUnits, 22)
//    assert(sc1.exists(k1))
//    assert(sc1(k1) == Vector(22))
//
//    sc1 = sc1.set(k2, NoUnits, "bob")
//    assert(sc1.exists(k2))
//    assert(sc1(k2) == Vector("bob"))
//
//    sc1.items.foreach {
//      case _: IntItem    ⇒ info("IntItem")
//      case _: StringItem ⇒ info("StringItem")
//    }
//  }
//
//  describe("testing new idea") {
//
//    val t1 = IntKey("test1")
//    it("should allow setting a single value") {
//      val i1 = t1.set(1)
//      assert(i1.values == Vector(1))
//      assert(i1.units == NoUnits)
//      assert(i1(0) == 1)
//    }
//    it("should allow setting several") {
//      val i1 = t1.set(1, 3, 5, 7)
//      assert(i1.values == Vector(1, 3, 5, 7))
//      assert(i1.units == NoUnits)
//      assert(i1(1) == 3)
//
//      val i2 = t1.set(Vector(10, 30, 50, 70)).withUnits(Deg)
//      assert(i2.values == Vector(10, 30, 50, 70))
//      assert(i2.units == Deg)
//      assert(i2(1) == 30)
//      assert(i2(3) == 70)
//    }
//    it("should also allow setting with sequence") {
//      val s1 = Vector(2, 4, 6, 8)
//      val i1 = t1.set(s1).withUnits(Meters)
//      assert(i1.values == s1)
//      assert(i1.values.size == s1.size)
//      assert(i1.units == Meters)
//      assert(i1(2) == 6)
//    }
//  }


}

