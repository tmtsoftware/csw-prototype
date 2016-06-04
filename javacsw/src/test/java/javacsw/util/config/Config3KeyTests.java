package javacsw.util.config;
//
//import csw.util.config.*;
//import csw.util.config.ConfigItems.*;
//import csw.util.config.Configurations.SetupConfig;
//import org.junit.Assert;
//import org.junit.Test;
//import scala.Option;
//
//import csw.util.config.KeyHelper.*;
//
//import java.util.Optional;
//
//import static csw.util.config.KeyHelper.*;
//import static junit.framework.TestCase.assertEquals;
//import static junit.framework.TestCase.assertFalse;
//import static org.junit.Assert.assertTrue;
//
//class MyArrayKey extends KeyTypeArray<Integer> {
//  private MyArrayKey(Vector<Integer> items) {
//
//    super("value", items, UnitsOfMeasure.Meters$.MODULE$);
//  }
//
//  static MyArrayKey adata(int... values) {
//    Vector<Integer> items = new Vector(Arrays.asList(values));
//
//    return new MyArrayKey(items);
//  }
//}
//class JFilter extends Key<String> {
//  public JFilter() {
//    super("filter");
//  }
//
//  public CItem<String> set(String v, UnitsOfMeasure.Units units) {
//    return new CItem<>("filter", v, UnitsOfMeasure.NoUnits$.MODULE$);
//  }
//
//  public CItem<String> set(String v, UnitsOfMeasure.Units units) {
//    return new CItem<>("filter", v, UnitsOfMeasure.NoUnits$.MODULE$);
//  }
//
//  static CItem<String> filter(String value) {
//    return (new JFilter()).set(value);
//  }
//}
///**
// * TMT Source Code: 5/3/16.
// */
//public class ConfigKeyTests {
//
//  private String ck = "wfos.blue.filter";
//  private String ck1 = "wfos.prog.cloudcover";
//  private String ck2 = "wfos.red.filter";
//  private String ck3 = "wfos.red.detector";
//
//  private String s1 = "encoder";
//  private String s2 = "filter";
//  private String s3 = "detectorTemp";
//
//  private double epsilon = .00001;  // Not sure why this is so big
//  @Test
//  public void testKey() {
//    IntKey k1 = new IntKey(s1);
//    StringKey k2 = new StringKey(s2);
//
//    assert (k1.keyname().equals(s1));
//
//    IntItem i = KeyHelper.set(k1, 1, 2, 3);
//    //IntItem i = k1.set(22);
//    assert (i.keyname().equals(s1));
//    assert (i.units() == UnitsOfMeasure.NoUnits$.MODULE$);
//    //assert (i.value() == new Vector(22);
//
//    assert (k2.keyname().equals(s2));
//    StringItem j = k2.set("Bob");
//    //assert (j.value().equals("Bob"));
//    assert (j.units() == UnitsOfMeasure.Meters$.MODULE$);
//
//    IntKey k3 = new IntKey(s1);
//    assert(k3 == k1);
//
//  }
//
//  @Test
//  public void jKeyTest() {
//    SingleKey k1 = new SingleKey<String>(s2);
//    CItem<String> i = k1.set("blue", UnitsOfMeasure.NoUnits$.MODULE$);
//    assert (k1.keyname().equals(s2));
//    assert (i.value() == "blue");
//    assert (i.units() == UnitsOfMeasure.NoUnits$.MODULE$);
//
//    SingleKey k2 = new SingleKey<Double>(s3);
//    assert (k2.keyname().equals(s3));
//    CItem<Double> j = k2.set(34.34, UnitsOfMeasure.Deg$.MODULE$);
//    assert (j.value() == 34.34);
//    assert (j.units() == UnitsOfMeasure.Deg$.MODULE$);
//  }
//
//  @Test
//  public void testConfig() {
//    IntKey k1 = new IntKey(s1);
//    int tval = 22;
//    IntItem i1 = k1.set(tval, UnitsOfMeasure.NoUnits$.MODULE$);
//
//    StringKey k2 = new StringKey(s2);
//    Item<String> i2 = k2.set(s2, UnitsOfMeasure.Meters$.MODULE$);
//
//    SetupConfig sc = new SetupConfig(ck2);
//    sc = sc.add(i1).add(i2);
//    assertEquals(sc.size(), 2);
//
//    Optional<IntItem> ki1 = sc.jget(k1);
//    assertEquals(ki1.get(), i1);
//    // I think this cast is only needed to make assertEquals happy
//    assertEquals((int)ki1.get().value(), tval);
//    assertEquals(UnitsOfMeasure.NoUnits$.MODULE$, ki1.get().units());
//    Optional<Item<String>> ki2 = sc.jget(k2);
//    assert (ki2.get() == i2);
//    assert (ki2.get().value().equals(s2));
//
//    // Check setting through sc
//    SetupConfig sc2 = new SetupConfig(ck3);
//    sc2 = sc2.add(k1.set(tval, UnitsOfMeasure.NoUnits$.MODULE$));
//    sc2 = sc2.add(k2.set(s2, UnitsOfMeasure.NoUnits$.MODULE$));
//    assertEquals(sc2.size(), 2);
//
//    //Optional<IntItem> ki3 = sc2.jget<Integer>(k1);
//    //assertEquals((int)ki3.get().value(), tval);
//    Optional<Item<String>> ki4 = sc2.jget(k2);
//    assertEquals(s2, ki4.get().value());
//
//
//    assertEquals(sc2.get(k1).get(), i1);
//    assertEquals((Integer)tval, sc2.jget(k1).get().value());
//    assertEquals(ki2.get(), i2);
//    assertEquals(s2, ki2.get().value());
//
//  }
//
//
//  @Test
//  public void testKey2() {
//    java.lang.Long tval = new java.lang.Long(1234);
//    LongKey k1 = new LongKey("bobby");
//    //JLongKey2 k2 = new JLongKey2("bobby");
//
//    LongItem i1 = k1.set(tval.longValue(), UnitsOfMeasure.NoUnits$.MODULE$);
//
//    //CItem<scala.Float> i2 = k2.set(tval, UnitsOfMeasure.NoUnits$.MODULE$).;
//  }
//
//
//  @Test
//  public void customJavaKey() {
//    // Create an instance
//    JFilter filter = new JFilter();
//    assert(filter.keyname().equals(s2));
//
//    CItem<String> i = filter.set("blue", UnitsOfMeasure.NoUnits$.MODULE$);
//    assert (i.value().equals("blue"));
//    assert (i.units() == UnitsOfMeasure.NoUnits$.MODULE$);
//
//    // First use add
//    SetupConfig sc = new SetupConfig(ck1).add(i);
//    assertEquals(sc.size(), 1);
//    assertTrue(sc.exists(filter));
//    assertEquals("blue", sc.jgetValue(filter));
//
//    // Then use set on config
//    sc = new SetupConfig(ck1).set(filter, "red", UnitsOfMeasure.NoUnits$.MODULE$);
//    assertTrue(sc.exists(filter));
//    assertEquals("red", sc.jgetValue(filter));
//
//    // Then use add with static method on filter - notice two adds of same one and last one overrides!
//    sc = new SetupConfig(ck1).add(JFilter.filter("green")).add(JFilter.filter("blue"));
//    assertTrue(sc.exists(filter));
//    assertEquals("blue", sc.jgetValue(filter));
//  }
//
//  @Test
//  public void testRemove() {
//    IntKey k1 = new IntKey(s1);
//    StringKey k2 = new StringKey(s2);
//    BooleanKey k3 = new BooleanKey(s3);
//
//    IntItem i1 = k1.set(22, UnitsOfMeasure.NoUnits$.MODULE$);
//    Item<String> i2 = k2.set(s2, UnitsOfMeasure.Meters$.MODULE$);
//    BooleanItem i3 = k3.set(true);
//
//    SetupConfig sc = new SetupConfig(ck2).add(i1).add(i2).add(i3);
//    assertEquals(sc.size(), 3);
//
//    sc = sc.remove(k1);
//    assertEquals(sc.size(), 2);
//    assertFalse(sc.exists(k1));
//    assertTrue(sc.exists(k2));
//    assertTrue(sc.exists(k3));
//    sc = sc.remove(k2);
//    assertEquals(sc.size(), 1);
//    assertFalse(sc.exists(k1));
//    assertFalse(sc.exists(k2));
//    assertTrue(sc.exists(k3));
//    sc = sc.remove(k3);
//    assertEquals(sc.size(), 0);
//    assertFalse(sc.exists(k1));
//    assertFalse(sc.exists(k2));
//    assertFalse(sc.exists(k3));
//  }
//
//  @Test
//  public void testKeyHelper() {
//    // This is supposed to test java with scala type conversions - which seem to "just work"
//    ShortKey sk = shortKey("test");
//    short tt1 = 2;
//    ShortItem si = set(sk, tt1, UnitsOfMeasure.Meters$.MODULE$);
//    short sout = si.value();
//    assertEquals(sout, tt1);
//    Short sout2 = si.value();
//    assertEquals(sout2.shortValue(), tt1);
//    ShortItem si2 = sk.set(tt1, UnitsOfMeasure.Meters$.MODULE$);
//    short sout3 = si2.value();
//    assertEquals(sout3, tt1);
//    Short sout4 = si2.value();
//    assertEquals(sout4.shortValue(), tt1);
//
//    int tt2 = 256;
//    IntKey ik = intKey("test2");
//    IntItem ii = set(ik, tt2, UnitsOfMeasure.Meters$.MODULE$);
//    int iout = ii.value();
//    assertEquals(iout, tt2);
//    Integer iout2 = ii.value();
//    assertEquals(iout2.intValue(), tt2);
//    IntItem ii2 = ik.set(tt2, UnitsOfMeasure.Meters$.MODULE$);
//    int iout3 = ii2.value();
//    assertEquals(iout3, tt2);
//    Integer iout4 = ii2.value();
//    assertEquals(iout4.intValue(), tt2);
//
//    long tt3 = 123456L;
//    LongKey lk = longKey("test3");
//    LongItem li = set(lk, tt3, UnitsOfMeasure.Meters$.MODULE$);
//    long lout = li.value();
//    assertEquals(lout, tt3);
//    Long lout2 = li.value();
//    assertEquals(lout2.longValue(), tt3);
//    LongItem li2 = lk.set(tt3, UnitsOfMeasure.Meters$.MODULE$);
//    long lout3 = li2.value();
//    assertEquals(lout3, tt3);
//    Long lout4 = li2.value();
//    assertEquals(lout4.longValue(), tt3);
//
//    float tt4 = 123.456f;
//    FloatKey fk = floatKey("test4");
//    FloatItem fi = set(fk, tt4, UnitsOfMeasure.Meters$.MODULE$);
//    float fout = fi.value();
//    assertEquals(fout, tt4);
//    Float fout2 = fi.value();
//    assertEquals(fout2.floatValue(), tt4);
//    FloatItem fi2 = fk.set(tt4, UnitsOfMeasure.Meters$.MODULE$);
//    float fout3 = fi2.value();
//    assertEquals(fout3, tt4);
//    Float fout4 = fi2.value();
//    assertEquals(fout4.floatValue(), tt4);
//
//    double tt5 = 123.456D;
//    DoubleKey dk = doubleKey("test4");
//    DoubleItem di = set(dk, tt4, UnitsOfMeasure.Meters$.MODULE$);
//    double dout = di.value();
//    assertEquals(dout, tt5, epsilon);
//    Double dout2 = di.value();
//    assertEquals(dout2.doubleValue(), tt5, epsilon);
//    DoubleItem di2 = dk.set(tt4, UnitsOfMeasure.Meters$.MODULE$);
//    double dout3 = di2.value();
//    assertEquals(dout3, tt5, epsilon);
//    Double dout4 = di2.value();
//    assertEquals(dout4.doubleValue(), tt5, epsilon);
//
//    boolean tt6 = false;
//    BooleanKey bk = booleanKey("test5");
//    BooleanItem bi = set(bk, tt6);
//    boolean bout = bi.value();
//    assertEquals(bout, tt6);
//    Boolean bout2 = bi.value();
//    assertEquals(bout2.booleanValue(), tt6);
//    BooleanItem bi2 = bk.set(tt6);
//    boolean bout3 = bi2.value();
//    assertEquals(bout3, tt6);
//    Boolean bout4 = bi2.value();
//    assertEquals(bout4.booleanValue(), tt6);
//  }
//}
