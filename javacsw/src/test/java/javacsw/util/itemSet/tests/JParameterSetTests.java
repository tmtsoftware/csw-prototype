package javacsw.util.itemSet.tests;

import csw.util.param.*;
import csw.util.param.Parameters.*;
import javacsw.util.itemSet.JItems;
import javacsw.util.itemSet.JUnitsOfMeasure;
import org.junit.Test;

import java.util.*;

import static javacsw.util.itemSet.JItems.*;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

/**
 * Tests the Java API to the config classes
 */
@SuppressWarnings({"OptionalGetWithoutIsPresent", "unused", "ConstantConditions"})
public class JParameterSetTests {
  private static final String s1 = "encoder";
  private static final String s2 = "filter";
  private static final String s3 = "detectorTemp";

  private static final String ck = "wfos.blue.filter";
  private static final String ck1 = "wfos.prog.cloudcover";
  private static final String ck2 = "wfos.red.filter";
  private static final String ck3 = "wfos.red.detector";

  private static final Parameters.CommandInfo info = new CommandInfo("Obs001");

  // @SuppressWarnings("EqualsBetweenInconvertibleTypes")

  @Test
  public void testJMAdd() {
    Setup sc1 = JItems.Setup(info, ck3);
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
    Setup sc1 = JItems.Setup(info, ck3);
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
    Setup sc1 = JItems.Setup(info, ck3);
    IntKey k1 = IntKey("encoder");
    IntKey k2 = IntKey("windspeed");
    StringKey k3 = StringKey("notpresent");

    IntParameter i1 = jset(k1, 22).withUnits(JUnitsOfMeasure.degrees);
    IntParameter i2 = jset(k2, 44);
    StringParameter i3 = jset(k3, "Really Not Present");

    sc1 = jadd(sc1, i1, i2);
    assertTrue(sc1.size() == 2);

    assertTrue(jitem(sc1, k1).equals(i1));
    assertTrue(jitem(sc1, k2).equals(i2));

    // Test for exception thrown for non present lookup
    boolean exFlag = false;
    try {
      StringParameter si = jitem(sc1, k3);
    } catch (NoSuchElementException ex) {
      exFlag = true;
    }

    assertTrue(exFlag);
  }

  @Test
  public void testJGetFunction() {
    Setup sc1 = JItems.Setup(info, ck3);
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
      Setup sc1 = JItems.Setup(info,ck3);
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
      Setup sc1 = new Setup(info, ck1).add(jset(k1, 22)).add(jset(k2, 44));
      assertTrue(sc1.size() == 2);
      assertTrue(sc1.exists(k1));
      assertTrue(sc1.exists(k2));
    }

    // Should allow getting values
    {
      Setup sc1 = new Setup(info, ck1);
      sc1 = jadd(sc1, jset(k1, 22));
      sc1 = jadd(sc1, jset(k2, 44));
      List<Integer> v1 = jvalues(jitem(sc1, k1));
      List<Integer> v2 = jvalues(jitem(sc1, k2));
      System.out.println("V1: " + v1);
      System.out.println("V2: " + v2);
      assertTrue(v1.equals(Collections.singletonList(22)));
      assertTrue(v2.equals(Collections.singletonList(44)));
    }
  }


  @Test
  public void testStaticSCMethods() {
    IntKey encoder = new IntKey("encoder");
    StringKey filter = new StringKey("filter");

    Setup sc1 = new Setup(info, ck1);
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
    IntParameter iitem = jset(encoder, Arrays.asList(1, 2, 3));
    StringParameter sitem = jset(filter, Arrays.asList("A", "B", "C"));

    Setup sc1 = JItems.Setup(info, ck1);
    sc1 = jadd(sc1, iitem, sitem);
    assertTrue(sc1.size() == 2);
    assertTrue(sc1.exists(encoder));
    assertTrue(sc1.exists(filter));

    Setup sc2 = new Setup(info, ck2);
    sc2 = jadd(sc2, jset(encoder, 1), jset(filter, "blue"));
    System.out.println("SC2: " + sc2);
  }

  @Test
  public void testConfigJGet() {
    IntKey encoder = new IntKey("encoder");
    StringKey filter = new StringKey("filter");
    IntKey notpresent = new IntKey("notpresent");

    IntParameter iitem = JItems.jset(encoder, Arrays.asList(1, 2, 3));
    StringParameter sitem = JItems.jset(filter, Arrays.asList("A", "B", "C"));

    // Add two items, but not the third
    Setup sc1 = new Setup(info, ck1);
    sc1 = sc1.add(iitem).add(sitem);
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
    Setup sc1 = new Setup(info, ck1);

    StringParameter siIn = jset(filter, "green");

    sc1 = sc1.add(jset(encoder, Arrays.asList(1, 2, 3), JUnitsOfMeasure.degrees)).add(siIn);
    assertTrue(sc1.size() == 2);

    sc1 = sc1.remove(encoder);
    System.out.println("Remove: " + sc1);
    assertTrue(sc1.size() == 1);

    sc1 = JItems.jremove(sc1, filter);
    System.out.println("Remove2: " + sc1);
    assertTrue(sc1.size() == 0);
  }
}

