package javacsw.util.config;

import csw.util.config.Configurations.*;
import csw.util.config.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static javacsw.util.config.JUnitsOfMeasure.*;

/**
 * Tests the Java API to the config classes
 */
@SuppressWarnings({"OptionalGetWithoutIsPresent", "unused"})
public class JConfigTests {
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
        System.out.println("basicKeyTests");

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
        assert (sc2.jvalue(k1) == 22);
        assert (sc2.jvalues(k1).equals(Collections.singletonList(22)));
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

        // should update for the same key with set
        {
            SetupConfig sc1 = new SetupConfig(ck1);
            sc1 = sc1.jset(k2, NoUnits, 22);
            assert (sc1.exists(k2));
            assert (sc1.jvalue(k2) == 22);

            sc1 = sc1.jset(k2, NoUnits, 33);
            assert (sc1.exists(k2));
            assert (sc1.jvalue(k2) == 33);
        }

        // should update for the same key with add
        {
            SetupConfig sc1 = new SetupConfig(ck1);
            sc1 = sc1.add(k2.jset(22).withUnits(NoUnits));
            assert (sc1.exists(k2));
            assert (sc1.jvalue(k2) == 22);

            sc1 = sc1.add(k2.jset(33).withUnits(NoUnits));
            assert (sc1.exists(k2));
            assert (sc1.jvalue(k2) == 33);
        }
    }

    @Test
    public void ocTest() {
        IntKey repeat = new IntKey("repeat");
        IntKey expTime = new IntKey("expTime");

        // Should allow adding keys
        {
            ObserveConfig oc1 = new ObserveConfig(ck3).jset(repeat, 22).jset(expTime, 44);
            assert (oc1.size() == 2);
            assert (oc1.exists(repeat));
            assert (oc1.exists(expTime));
            assert (oc1.jvalue(repeat) == 22);
            assert (oc1.jvalue(expTime) == 44);
        }

        // Should allow setting
        {
            ObserveConfig oc1 = new ObserveConfig(ck1);
            oc1 = oc1.jset(repeat, NoUnits, 22).jset(expTime, NoUnits, 44);
            assert (oc1.size() == 2);
            assert (oc1.exists(repeat));
            assert (oc1.exists(expTime));
        }

        // Should allow getting values
        {
            ObserveConfig oc1 = new ObserveConfig(ck1);
            oc1 = oc1.jset(repeat, NoUnits, 22).jset(expTime, NoUnits, 44);
            List<Integer> v1 = oc1.jvalues(repeat);
            List<Integer> v2 = oc1.jvalues(expTime);
            assert (oc1.jget(repeat).isPresent());
            assert (oc1.jget(expTime).isPresent());
            assert (v1.equals(Collections.singletonList(22)));
            assert (v2.equals(Collections.singletonList(44)));
        }

        // should update for the same key with set
        {
            ObserveConfig oc1 = new ObserveConfig(ck1);
            oc1 = oc1.jset(expTime, NoUnits, 22);
            assert (oc1.exists(expTime));
            assert (oc1.jvalue(expTime) == 22);

            oc1 = oc1.jset(expTime, NoUnits, 33);
            assert (oc1.exists(expTime));
            assert (oc1.jvalue(expTime) == 33);
        }

        // should update for the same key with add
        {
            ObserveConfig oc1 = new ObserveConfig(ck1);
            oc1 = oc1.add(expTime.jset(22).withUnits(NoUnits));
            assert (oc1.exists(expTime));
            assert (oc1.jvalue(expTime) == 22);

            oc1 = oc1.add(expTime.jset(33).withUnits(NoUnits));
            assert (oc1.exists(expTime));
            assert (oc1.jvalue(expTime) == 33);
        }
    }

    @Test
    public void scTest2() {
        // should update for the same key with set
        IntKey k1 = new IntKey("encoder");
        StringKey k2 = new StringKey("windspeed");

        SetupConfig sc1 = new SetupConfig(ck1);
        sc1 = sc1.jset(k1, NoUnits, 22);
        assert (sc1.exists(k1));
        assert (sc1.jvalue(k1) == 22);

        sc1 = sc1.jset(k2, NoUnits, "bob");
        assert (sc1.exists(k2));
        assert (Objects.equals(sc1.jvalue(k2), "bob"));
        assert (sc1.size() == 2);
    }

    @Test
    public void testSettingMultipleValues() {
        IntKey t1 = new IntKey("test1");
        // should allow setting a single value
        {
            IntItem i1 = t1.jset(1);
            assert (i1.jvalue() == 1);
            assert (i1.units() == NoUnits);
            assert (i1.jvalue(0) == 1);
        }
        // should allow setting several
        {
            IntItem i1 = t1.jset(1, 3, 5, 7);
            assert (i1.jvalues().equals(Arrays.asList(1, 3, 5, 7)));
            assert (i1.units() == NoUnits);
            assert (i1.jvalue(1) == 3);

            IntItem i2 = t1.jset(Arrays.asList(10, 30, 50, 70)).withUnits(Deg);
            assert (i2.jvalues().equals(Arrays.asList(10, 30, 50, 70)));
            assert (i2.units() == Deg);
            assert (i2.jvalue(1) == 30);
            assert (i2.jvalue(3) == 70);
        }
        // should also allow setting with sequence
        {
            List<Integer> s1 = Arrays.asList(2, 4, 6, 8);
            IntItem i1 = t1.jset(s1).withUnits(Meters);
            assert (i1.jvalues().equals(s1));
            assert (i1.size() == s1.size());
            assert (i1.units() == Meters);
            assert (i1.jvalue(2) == 6);
        }
    }

    @Test
    public void testSetupConfigArgs() {
        IntKey encoder1 = new IntKey("encoder1");
        IntKey encoder2 = new IntKey("encoder2");
        IntKey xOffset = new IntKey("xOffset");
        IntKey yOffset = new IntKey("yOffset");
        String obsId = "Obs001";

        SetupConfig sc1 = new SetupConfig(ck1).jset(encoder1, 22).jset(encoder2, 33);
        SetupConfig sc2 = new SetupConfig(ck1).jset(xOffset, 1).jset(yOffset, 2);
        SetupConfigArg configArg = Configurations.createSetupConfigArg(obsId, sc1, sc2);
        assert (configArg.info().obsId().obsId().equals(obsId));
        assert (configArg.jconfigs().equals(Arrays.asList(sc1, sc2)));
    }

    @Test
    public void testObserveConfigArgs() {
        IntKey encoder1 = new IntKey("encoder1");
        IntKey encoder2 = new IntKey("encoder2");
        IntKey xOffset = new IntKey("xOffset");
        IntKey yOffset = new IntKey("yOffset");
        String obsId = "Obs001";

        ObserveConfig sc1 = new ObserveConfig(ck1).jset(encoder1, 22).jset(encoder2, 33);
        ObserveConfig sc2 = new ObserveConfig(ck1).jset(xOffset, 1).jset(yOffset, 2);
        ObserveConfigArg configArg = Configurations.createObserveConfigArg(obsId, sc1, sc2);
        assert (configArg.info().obsId().obsId().equals(obsId));
        assert (configArg.jconfigs().equals(Arrays.asList(sc1, sc2)));
    }

}

