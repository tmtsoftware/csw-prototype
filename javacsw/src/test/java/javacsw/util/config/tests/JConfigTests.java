package javacsw.util.config.tests;

import csw.util.config.*;
import csw.util.config.Configurations.*;
import javacsw.util.config.JItems;
import javacsw.util.config.JUnitsOfMeasure;
import org.junit.Test;

import java.util.*;

import static javacsw.util.config.JItems.*;
import static javacsw.util.config.JConfigDSL.*;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

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

  // @SuppressWarnings("EqualsBetweenInconvertibleTypes")

  @Test
  public void testJMAdd() {
    SetupConfig sc1 = SetupConfig(ck3);
    IntKey k1 = IntKey("encoder");
    IntKey k2 = IntKey("windspeed");

    sc1 = jadd(sc1, jset(k1, 22), jset(k2, 44));
    assertTrue(sc1.size() == 2);
    assertTrue(sc1.exists(k1));
    assertTrue(sc1.exists(k2));
    assertTrue(jvalue(jitem(sc1, k1)).equals(22));
    assertEquals(jvalues(jitem(sc1, k2)), Collections.singletonList(44));
  }

  @Test
  public void testJGetItem() {
    SetupConfig sc1 = SetupConfig(ck3);
    IntKey k1 = IntKey("encoder");
    IntKey k2 = IntKey("windspeed");
    StringKey k3 = StringKey("notpresent");

    sc1 = jadd(sc1, jset(k1, 22), jset(k2, 44));
    assertEquals(sc1.size(), 2);

    // Check for two present and one not present
    assertTrue(jgetItem(sc1, k1).isPresent());
    assertTrue(jgetItem(sc1, k2).isPresent());
    assertFalse(jgetItem(sc1, k3).isPresent());
  }

  @Test
  public void testJItem() {
    SetupConfig sc1 = SetupConfig(ck3);
    IntKey k1 = IntKey("encoder");
    IntKey k2 = IntKey("windspeed");
    StringKey k3 = StringKey("notpresent");

    IntItem i1 = jset(k1, 22).withUnits(JUnitsOfMeasure.Deg);
    IntItem i2 = jset(k2, 44);
    StringItem i3 = jset(k3, "Really Not Present");

    sc1 = jadd(sc1, i1, i2);
    assertTrue(sc1.size() == 2);

    assertTrue(jitem(sc1, k1).equals(i1));
    assertTrue(jitem(sc1, k2).equals(i2));

    // Test for exception thrown for non present lookup
    boolean exFlag = false;
    try {
      StringItem si = jitem(sc1, k3);
    } catch (NoSuchElementException ex) {
      exFlag = true;
    }

    assertTrue(exFlag);
  }

  @Test
  public void testJGetFunction() {
    SetupConfig sc1 = SetupConfig(ck3);
    IntKey k1 = IntKey("encoder");
    DoubleKey k2 = DoubleKey("windspeed");
    IntKey k3 = IntKey("NotPresent");

    sc1 = jadd(sc1, jset(k1, 22, 44, 66), jset(k2, 1., 2., 3., 4.));
    assertTrue(jget(sc1, k1, 0).isPresent() && jvalue(jitem(sc1, k1), 0).equals(22));
    assertTrue(jget(sc1, k2, 1).isPresent() && jvalue(jitem(sc1, k2), 1).equals(2.));
    assertFalse(jget(sc1, k3, 2).isPresent());
  }

  @Test
  public void scTest() {
    IntKey k1 = IntKey("encoder");
    IntKey k2 = IntKey("windspeed");

    // It "Should allow adding keys")
    {
      SetupConfig sc1 = SetupConfig(ck3);
      sc1 = sc1.add(jset(k1, 22)).add(jset(k2, 44));
      assertTrue(sc1.size() == 2);
      assertTrue(sc1.exists(k1));
      assertTrue(sc1.exists(k2));
      assertTrue(jvalue(jitem(sc1, k1)).equals(22));
      int xx = jvalue(jitem(sc1, k2));
      assertTrue(xx == 44);
    }

    // it("Should allow setting")
    {
      SetupConfig sc1 = new SetupConfig(ck1).add(jset(k1, 22)).add(jset(k2, 44));
      assertTrue(sc1.size() == 2);
      assertTrue(sc1.exists(k1));
      assertTrue(sc1.exists(k2));
    }

    // Should allow getting values
    {
      SetupConfig sc1 = new SetupConfig(ck1);
      sc1 = jadd(sc1, jset(k1, 22));
      sc1 = jadd(sc1, jset(k2, 44));
      List<Integer> v1 = jvalues(jitem(sc1, k1));
      List<Integer> v2 = jvalues(jitem(sc1, k2));
      System.out.println("V1: " + v1);
      System.out.println("V2: " + v2);
      assertTrue(v1.equals(Collections.singletonList(22)));
      assertTrue(v2.equals(Collections.singletonList(44)));
    }
/*
    // should update for the same key with set
    {
      SetupConfig sc1 = new SetupConfig(ck1);
      sc1 = sc1.jset(k2, NoUnits, 22);
      assertTrue(sc1.exists(k2));
      assertTrue(sc1.jvalue(k2) == 22);

      sc1 = sc1.jset(k2, NoUnits, 33);
      assertTrue(sc1.exists(k2));
      assertTrue(sc1.jvalue(k2) == 33);
    }

    // should update for the same key with add
    {
      SetupConfig sc1 = new SetupConfig(ck1);
      sc1 = sc1.add(k2.jset(22).withUnits(NoUnits));
      assertTrue(sc1.exists(k2));
      assertTrue(sc1.jvalue(k2) == 22);

      sc1 = sc1.add(k2.jset(33).withUnits(NoUnits));
      assertTrue(sc1.exists(k2));
      assertTrue(sc1.jvalue(k2) == 33);
    }
    */
  }

  /*
      @Test
      public void CheckingKeyUpdates() {
          IntKey k1 = new IntKey("atest");

          // Should allow updates
          IntItem i1 = k1.jset(22);
          assertTrue(i1.jvalue() == 22);
          assertTrue(i1.jvalue(0) == 22);
          assertTrue(i1.units() == NoUnits);
          IntItem i2 = k1.jset(33);
          assertTrue(i2.jvalue() == 33);
          assertTrue(i2.units() == NoUnits);

          SetupConfig sc = new SetupConfig(ck1).add(i1);
          assertTrue(sc.jvalue(k1, 0) == 22);
          sc = sc.add(i2);
          assertTrue(sc.jvalue(k1, 0) == 33);

          SetupConfig sc2 = new SetupConfig(ck1).jset(k1, 22);
          assertTrue(sc2.jvalue(k1) == 22);
          assertTrue(sc2.jvalues(k1).equals(Collections.singletonList(22)));
      }
  */
/*
    @Test
    public void ocTest() {
        IntKey repeat = new IntKey("repeat");
        IntKey expTime = new IntKey("expTime");

        // Should allow adding keys
        {
            ObserveConfig oc1 = new ObserveConfig(ck3).jset(repeat, 22).jset(expTime, 44);
            assertTrue(oc1.size() == 2);
            assertTrue(oc1.exists(repeat));
            assertTrue(oc1.exists(expTime));
            assertTrue(oc1.jvalue(repeat) == 22);
            assertTrue(oc1.jvalue(expTime) == 44);
        }

        // Should allow setting
        {
            ObserveConfig oc1 = new ObserveConfig(ck1);
            oc1 = oc1.jset(repeat, NoUnits, 22).jset(expTime, NoUnits, 44);
            assertTrue(oc1.size() == 2);
            assertTrue(oc1.exists(repeat));
            assertTrue(oc1.exists(expTime));
        }

        // Should allow getting values
        {
            ObserveConfig oc1 = new ObserveConfig(ck1);
            oc1 = oc1.jset(repeat, NoUnits, 22).jset(expTime, NoUnits, 44);
            List<Integer> v1 = oc1.jvalues(repeat);
            List<Integer> v2 = oc1.jvalues(expTime);
            assertTrue(oc1.jget(repeat).isPresent());
            assertTrue(oc1.jget(expTime).isPresent());
            assertTrue(v1.equals(Collections.singletonList(22)));
            assertTrue(v2.equals(Collections.singletonList(44)));
        }

        // should update for the same key with set
        {
            ObserveConfig oc1 = new ObserveConfig(ck1);
            oc1 = oc1.jset(expTime, NoUnits, 22);
            assertTrue(oc1.exists(expTime));
            assertTrue(oc1.jvalue(expTime) == 22);

            oc1 = oc1.jset(expTime, NoUnits, 33);
            assertTrue(oc1.exists(expTime));
            assertTrue(oc1.jvalue(expTime) == 33);
        }

        // should update for the same key with add
        {
            ObserveConfig oc1 = new ObserveConfig(ck1);
            oc1 = oc1.add(expTime.jset(22).withUnits(NoUnits));
            assertTrue(oc1.exists(expTime));
            assertTrue(oc1.jvalue(expTime) == 22);

            oc1 = oc1.add(expTime.jset(33).withUnits(NoUnits));
            assertTrue(oc1.exists(expTime));
            assertTrue(oc1.jvalue(expTime) == 33);
        }
    }

    @Test
    public void scTest2() {
        // should update for the same key with set
        IntKey k1 = new IntKey("encoder");
        StringKey k2 = new StringKey("windspeed");

        SetupConfig sc1 = new SetupConfig(ck1);
        sc1 = sc1.jset(k1, NoUnits, 22);
        assertTrue(sc1.exists(k1));
        assertTrue(sc1.jvalue(k1) == 22);

        sc1 = sc1.jset(k2, NoUnits, "bob");
        assertTrue(sc1.exists(k2));
        assertTrue(Objects.equals(sc1.jvalue(k2), "bob"));
        assertTrue(sc1.size() == 2);
    }

    @Test
    public void testSettingMultipleValues() {
        IntKey t1 = new IntKey("test1");
        // should allow setting a single value
        {
            IntItem i1 = t1.jset(1);
            assertTrue(i1.jvalue() == 1);
            assertTrue(i1.units() == NoUnits);
            assertTrue(i1.jvalue(0) == 1);
        }
        // should allow setting several
        {
            IntItem i1 = t1.jset(1, 3, 5, 7);
            assertTrue(i1.jvalues().equals(Arrays.asList(1, 3, 5, 7)));
            assertTrue(i1.units() == NoUnits);
            assertTrue(i1.jvalue(1) == 3);

            IntItem i2 = t1.jset(Arrays.asList(10, 30, 50, 70)).withUnits(Deg);
            assertTrue(i2.jvalues().equals(Arrays.asList(10, 30, 50, 70)));
            assertTrue(i2.units() == Deg);
            assertTrue(i2.jvalue(1) == 30);
            assertTrue(i2.jvalue(3) == 70);
        }
        // should also allow setting with sequence
        {
            List<Integer> s1 = Arrays.asList(2, 4, 6, 8);
            IntItem i1 = t1.jset(s1).withUnits(Meters);
            assertTrue(i1.jvalues().equals(s1));
            assertTrue(i1.size() == s1.size());
            assertTrue(i1.units() == Meters);
            assertTrue(i1.jvalue(2) == 6);
        }
    }
*/

  @Test
  public void testSetupConfigArgs() {
    IntKey encoder1 = IntKey("encoder1");
    IntKey encoder2 = IntKey("encoder2");
    IntKey xOffset = IntKey("xOffset");
    IntKey yOffset = IntKey("yOffset");
    String obsId = "Obs001";
    SetupConfig sc1 = jadd(sc(ck1), jset(encoder1, 22), jset(encoder2, 33));
    SetupConfig sc2 = jadd(sc(ck1), jset(xOffset, 1), jset(yOffset, 2));
    SetupConfigArg configArg = Configurations.createSetupConfigArg(obsId, sc1, sc2);
    assertTrue(configArg.info().obsId().obsId().equals(obsId));
    assertTrue(configArg.jconfigs().equals(Arrays.asList(sc1, sc2)));
  }

  @Test
  public void testObserveConfigArgs() {
    IntKey encoder1 = new IntKey("encoder1");
    IntKey encoder2 = new IntKey("encoder2");
    IntKey xOffset = new IntKey("xOffset");
    IntKey yOffset = new IntKey("yOffset");
    String obsId = "Obs001";

    ObserveConfig sc1 = ObserveConfig(ck1);
    sc1 = jadd(sc1, jset(encoder1, 22), jset(encoder2, 33));
    ObserveConfig sc2 = ObserveConfig(ck1);
    sc2 = jadd(sc2, jset(xOffset, 1), jset(yOffset, 2));
    assertTrue(!jgetItem(sc1, xOffset).isPresent());
    assertTrue(!jget(sc1, xOffset, 0).isPresent());
    assertTrue(jgetItem(sc2, xOffset).isPresent());
    assertTrue(jget(sc2, xOffset, 0).isPresent());
/*  Kim READD
        ObserveConfigArg configArg = Configurations.createObserveConfigArg(obsId, sc1, sc2);
        assertTrue(configArg.info().obsId().obsId().equals(obsId));
        assertTrue(configArg.jconfigs().equals(Arrays.asList(sc1, sc2)));
        */
  }


  @Test
  public void testStaticSCMethods() {
    IntKey encoder = new IntKey("encoder");
    StringKey filter = new StringKey("filter");

    SetupConfig sc1 = new SetupConfig(ck1);
    sc1 = JItems.jadd(sc1, jset(encoder, Arrays.asList(100, 200)));

    assertTrue(jvalue(jitem(sc1, encoder)).equals(100));
    System.out.println("ONE: " + jget(sc1, encoder, 0));
    assertTrue(jget(sc1, encoder, 0).equals(Optional.of(100)));
    assertTrue(jget(sc1, encoder, 1).equals(Optional.of(200)));

    sc1 = JItems.jadd(sc1, jset(encoder, 100, 1000, 1000));
    System.out.println("SC1: " + sc1);

    List<Integer> x1 = jvalues(jitem(sc1, encoder));
    System.out.println("X1: " + x1);
  }

  @Test
  public void testMultiItemAdd() {
    IntKey encoder = IntKey("encoder");
    StringKey filter = StringKey("filter");
    IntItem iitem = jset(encoder, Arrays.asList(1, 2, 3));
    StringItem sitem = jset(filter, Arrays.asList("A", "B", "C"));

    SetupConfig sc1 = SetupConfig(ck1);
    sc1 = jadd(sc1, iitem, sitem);
    assertTrue(sc1.size() == 2);
    assertTrue(sc1.exists(encoder));
    assertTrue(sc1.exists(filter));

    SetupConfig sc2 = new SetupConfig(ck2);
    sc2 = jadd(sc2, jset(encoder, 1), jset(filter, "blue"));
    System.out.println("SC2: " + sc2);
  }

  @Test
  public void testConfigJGet() {
    IntKey encoder = new IntKey("encoder");
    StringKey filter = new StringKey("filter");
    IntKey notpresent = new IntKey("notpresent");

    IntItem iitem = JItems.jset(encoder, Arrays.asList(1, 2, 3));
    StringItem sitem = JItems.jset(filter, Arrays.asList("A", "B", "C"));

    // Add two items, but not the third
    SetupConfig sc1 = new SetupConfig(ck1);
    sc1 = sc1.add(iitem).add(sitem);
    //sc1 = sc1.madd(iitem, sitem);
    assertTrue(sc1.size() == 2);

    // Two should be present, but the last is not
    assertTrue(jgetItem(sc1, encoder).get().equals(iitem));
    assertTrue(jgetItem(sc1, filter).get().equals(sitem));
    assertTrue(jgetItem(sc1, notpresent).equals(Optional.empty()));

    assertTrue(jvalue(jitem(sc1, encoder), 1).equals(2));
  }

  @Test
  public void testJavaRemove() {
    IntKey encoder = new IntKey("encoder");
    StringKey filter = new StringKey("filter");
    IntKey notpresent = new IntKey("notpresent");
    SetupConfig sc1 = new SetupConfig(ck1);

    StringItem siIn = jset(filter, "green");

    sc1 = sc1.add(jset(encoder, Arrays.asList(1, 2, 3), JUnitsOfMeasure.Deg)).add(siIn);
    assertTrue(sc1.size() == 2);

    sc1 = sc1.remove(encoder);
    System.out.println("Remove: " + sc1);
    assertTrue(sc1.size() == 1);

    sc1 = JItems.jremove(sc1, filter);
    System.out.println("Remove2: " + sc1);
    assertTrue(sc1.size() == 0);
  }
}

