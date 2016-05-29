package csw.util.config3

import csw.util.config3.ConfigItems._
import csw.util.config3.Configurations.SetupConfig
import csw.util.config3.UnitsOfMeasure.{Meters, NoUnits}
import org.scalatest.FunSpec

/**
 * TMT Source Code: 5/8/16.
 */
//noinspection ComparingUnrelatedTypes,ScalaUnusedSymbol
class Config3Tests extends FunSpec {
  private val s1: String = "encoder"
  private val s2: String = "filter"
  private val s3: String = "detectorTemp"

  private val ck = "wfos.blue.filter"
  private val ck1 = "wfos.prog.cloudcover"
  private val ck2 = "wfos.red.filter"
  private val ck3 = "wfos.red.detector"

  describe("Basic key tests") {
    val k1 = IntegerKey(s1)
    val k2 = StringKey(s2)

    it("Should be constructed properly") {
      assert(k1.keyName eq s1)
    }

    it("Should use set properly") {
      val i = k1.set(Vector(22), UnitsOfMeasure.NoUnits)
      assert(i.keyName eq s1)
      assert(i.value == Vector(22))

      assert(k2.keyName eq s2)
      val j: StringItem = k2.set(Vector("Bob"), UnitsOfMeasure.Meters)
      assert(j.value == Vector("Bob"))

      // Try default
      val k = k2.set("Bob")
    }

    it("Should support equality of keys") {
      val k3 = IntegerKey(s1)
      assert(k3 == k1)
      assert(k3 != k2)
      assert(k1 != k2)
    }
  }

  describe("Basic array tests") {
    val k1 = SingleKey[Int]("atest")

    it("Should allow an Int array") {
      val i1 = k1.set(Vector(1, 2, 3), UnitsOfMeasure.NoUnits)
      assert(i1.value == Vector(1, 2, 3))
      val i2 = k1.set(1, 2, 3)
      assert(i2.value == Vector(1, 2, 3))
      assert(i2.units == UnitsOfMeasure.NoUnits)
    }

    it("Should use key equals") {
      val k2: SingleKey[Int] = SingleKey[Int]("atest1")
      val k3: SingleKey[Int] = SingleKey[Int]("atest")
      val k4: SingleKey[Float] = SingleKey[Float]("atest")

      assert(k1 == k1)
      assert(k1 != k2)
      assert(k2 != k3)
      // Checking for types
      //      assert(k3 != k4)
    }
  }

  describe("Checking key updates") {
    val k1: IntegerKey = new IntegerKey("atest")

    it("Should allow updates") {
      val i1 = k1.set(22)
      assert(i1.value == Vector(22))
      assert(i1.units == UnitsOfMeasure.NoUnits)
      val i2 = k1.set(33)
      assert(i2.value == Vector(33))
      assert(i2.units == UnitsOfMeasure.NoUnits)

      var sc = SetupConfig(ck1).add(i1)
      assert(sc.get(k1).get.value == Vector(22))
      sc = sc.add(i2)
      assert(sc.get(k1).get.value == Vector(33))
    }
  }

  describe("Test for conversions from Java") {
    it("should allow setting from Java objects") {
      val tval = new java.lang.Long(1234)
      val k1 = LongKey(s1)
      val i1 = k1.set(tval)
      assert(i1.value == Vector(1234L))

      val tval2 = 4567L
      val k2 = LongKey(s1)
      val i2 = k2.set(tval2)
      assert(i2.value == Vector(4567L))
    }
  }

  describe("SC Test") {

    val k1 = IntegerKey("encoder")
    val k2 = IntegerKey("windspeed")
    it("Should allow adding") {
      var sc1 = SetupConfig(ck3)
      val i1 = k1.set(Vector(22), UnitsOfMeasure.NoUnits)
      val i2 = k2.set(Vector(44), UnitsOfMeasure.NoUnits)
      sc1 = sc1.add(i1).add(i2)
      assert(sc1.size == 2)
      assert(sc1.exists(k1))
      assert(sc1.exists(k2))
    }

    it("Should allow setting") {
      var sc1 = SetupConfig(ck1)
      sc1 = sc1.set(k1, Vector(22), UnitsOfMeasure.NoUnits).set(k2, Vector(44), UnitsOfMeasure.NoUnits)
      assert(sc1.size == 2)
      assert(sc1.exists(k1))
      assert(sc1.exists(k2))
    }

    it("Should allow apply") {
      var sc1 = SetupConfig(ck1)
      sc1 = sc1.set(k1, Vector(22), UnitsOfMeasure.NoUnits).set(k2, Vector(44), UnitsOfMeasure.NoUnits)

      val v1 = sc1(k1)
      val v2 = sc1(k2)
      assert(sc1.get(k1).isDefined)
      assert(sc1.get(k2).isDefined)
      assert(v1 == Vector(22))
      assert(v2 == Vector(44))
    }

    it("should update for the same key with set") {
      var sc1 = SetupConfig(ck1)
      sc1 = sc1.set(k2, Vector(22), UnitsOfMeasure.NoUnits)
      assert(sc1.exists(k2))
      assert(sc1(k2) == Vector(22))

      sc1 = sc1.set(k2, Vector(33), UnitsOfMeasure.NoUnits)
      assert(sc1.exists(k2))
      assert(sc1(k2) == Vector(33))
    }

    it("should update for the same key with add") {
      var sc1 = SetupConfig(ck1)
      sc1 = sc1.add(k2.set(Vector(22), UnitsOfMeasure.NoUnits))
      assert(sc1.exists(k2))
      assert(sc1(k2) == Vector(22))

      sc1 = sc1.add(k2.set(Vector(33), UnitsOfMeasure.NoUnits))
      assert(sc1.exists(k2))
      assert(sc1(k2) == Vector(33))
    }

  }

  it("should update for the same key with set") {
    val k1 = IntegerKey("encoder")
    val k2 = StringKey("windspeed")

    var sc1 = SetupConfig(ck1)
    sc1 = sc1.set(k1, Vector(22), UnitsOfMeasure.NoUnits)
    assert(sc1.exists(k1))
    assert(sc1(k1) == Vector(22))

    sc1 = sc1.set(k2, Vector("bob"), UnitsOfMeasure.NoUnits)
    assert(sc1.exists(k2))
    assert(sc1(k2) == Vector("bob"))

    sc1.items.foreach {
      case _: IntegerItem    ⇒ info("IntegerItem")
      case _: StringItem ⇒ info("StringItem")
    }
  }

  describe("testing new idea") {

    val t1 = IntegerKey("test1")
    it("should allow setting a single value") {
      val i1 = t1.set(1)
      assert(i1.value == Vector(1))
      assert(i1.units == NoUnits)
      assert(i1(0) == 1)
    }
    it("should allow setting several") {
      val i1 = t1.set(1, 3, 5, 7)
      assert(i1.value == Vector(1, 3, 5, 7))
      assert(i1.units == NoUnits)
      assert(i1(1) == 3)

      val i2 = t1.set(10, 30, 50, 70).withUnits(UnitsOfMeasure.Deg)
      assert(i2.value == Vector(10, 30, 50, 70))
      assert(i2.units == UnitsOfMeasure.Deg)
      assert(i2(1) == 30)
    }
    it("should also allow setting with sequence") {
      val s1 = Vector(2, 4, 6, 8)
      val i1 = t1.set(s1, Meters)
      assert(i1.value == s1)
      assert(i1.value.size == s1.size)
      assert(i1.units == Meters)
      assert(i1(2) == 6)
    }
  }
}
