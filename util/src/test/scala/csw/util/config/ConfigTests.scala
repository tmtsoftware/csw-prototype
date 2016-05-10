package csw.util.config

import csw.util.config.Configurations.SetupConfig
import org.scalatest.FunSpec

/**
  * Tests the configuration classes
  */
class ConfigTests extends FunSpec {
  private val s1: String = "encoder"
  private val s2: String = "filter"
  private val s3: String = "detectorTemp"

  private val ck = "wfos.blue.filter"
  private val ck1 = "wfos.prog.cloudcover"
  private val ck2 = "wfos.red.filter"
  private val ck3 = "wfos.red.detector"

  describe("Basic key tests") {
    val k1 = IntKey(s1, UnitsOfMeasure.NoUnits)
    val k2 = StringKey(s2, UnitsOfMeasure.Meters)

    it("Should be constructed properly") {
      assert(k1.name eq s1)
      assert(k1.units eq UnitsOfMeasure.NoUnits)
    }

    it("Should use set properly") {
      val i: Item[Integer] = k1.set(22)
      assert(i.key.name eq s1)
      assert(i.key.units eq UnitsOfMeasure.NoUnits)
      assert(i.value == new Integer(22))

      assert(k2.name eq s2)
      assert(k2.units eq UnitsOfMeasure.Meters)
      val j: Item[String] = k2.set("Bob")
      assert(j.value == "Bob")
    }

    it("Should support equality of keys") {
      val k3 = IntKey(s1, UnitsOfMeasure.NoUnits)
      assert(k3 == k1)
      //noinspection ComparingUnrelatedTypes
      assert(k3 != k2)
      //noinspection ComparingUnrelatedTypes
      assert(k1 != k2)
    }
  }

  describe("Basic array tests") {
    val k1: ArrayKey[Int] = ArrayKey[Int]("atest", UnitsOfMeasure.NoUnits)

    it("Should allow an Int array") {
      val i1 = k1.set(Seq(1, 2, 3))
      assert(i1.value == Seq(1, 2, 3))
      val i2 = k1.set(1, 2, 3)
      assert(i2.value == Seq(1, 2, 3))
    }

    it("Should use key equals") {
      val k2: ArrayKey[Int] = ArrayKey("atest1", UnitsOfMeasure.NoUnits)
      val k3: ArrayKey[Int] = ArrayKey("atest", UnitsOfMeasure.Deg)

      assert(k1 == k1)
      assert(k1 != k2)
      assert(k1 != k3)
      assert(k2 != k3)

    }
  }

  describe("Java compat array tests") {
    val k1: IntArrayKey = IntArrayKey("atest", UnitsOfMeasure.NoUnits)

    it("Should allow an Int array") {
      val seq = Seq(1, 2, 3).asInstanceOf[Seq[java.lang.Integer]]
      val i1 = k1.set(seq)
      assert(i1.value == seq)
      val i2 = k1.set(1, 2, 3)
      assert(i2.value == seq)
    }

    it("Should use key equals") {
      val k2: IntArrayKey = IntArrayKey("atest1", UnitsOfMeasure.NoUnits)
      val k3: IntArrayKey = IntArrayKey("atest", UnitsOfMeasure.Deg)

      assert(k1 == k1)
      assert(k1 != k2)
      assert(k1 != k3)
      assert(k2 != k3)
    }
  }



  describe("Checking key updates") {
    val k1: JKey1[Int] = JKey1("atest", UnitsOfMeasure.NoUnits)

    it("Should allow updates") {
      val i1 = k1.set(22)
      assert(i1.value == 22)
      val i2 = k1.set(33)
      assert(i2.value == 33)

      val sc = SetupConfig(ck1).add(i1)
      assert(sc.get(k1).get.value == 22)
      val sc2 = sc.add(i2)
      // assert(sc.get(k1).get.value == 33)
    }
  }

  describe("Check set") {
    case class ttt(name: String, value: Int)
    // Trying to understand updates
    it("should allow updates") {
      val t1 = ttt("bob", 22)
      val t2 = ttt("ted", 44)
      val t3 = ttt("bob", 33)
      val t4 = ttt("bob", 22)

      assert(t1 == t4)


      var set1 = Set(t1, t2)
      assert(set1.contains(t1))
      assert(set1.contains(t2))
      val f1 = set1.find(_.name == "bob")
      assert(f1.get.value == 22)

      set1 = set1 + t3
      val f2 = set1.find(_.name == "bob")
      //assert(f2.get.value == 33)
    }

  }

}
