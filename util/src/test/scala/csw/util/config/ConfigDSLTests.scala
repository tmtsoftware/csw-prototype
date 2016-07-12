package csw.util.config

import csw.util.config.ConfigDSL._
import csw.util.config.Configurations.SetupConfig
import csw.util.config.UnitsOfMeasure.{Deg, NoUnits}
import org.scalatest.{FunSpec, ShouldMatchers}

/**
 * TMT Source Code: 7/9/16.
 */
class ConfigDSLTests extends FunSpec with ShouldMatchers {
  private val s1: String = "encoder"
  private val s2: String = "filter"
  private val s3: String = "detectorTemp"
  private val ck: String = "wfos.blue.filter"
  private val ck1: String = "wfos.prog.cloudcover"
  private val ck2: String = "wfos.red.filter"
  private val ck3: String = "wfos.red.detector"

  describe("creating items") {
    val k1 = IntKey(s1)
    val detectorTemp = DoubleKey(s3)

    it("should work to set single items") {
      val i1 = set(k1, 2)
      i1 shouldBe an[IntItem]
      i1.size should equal(1)
      i1.units should be(NoUnits)
    }

    it("should work to set multiple items") {
      val i1 = set(k1, 1, 2, 3, 4, 5)
      i1.size should equal(5)
    }

    it("should work with units too") {
      val i1 = set(detectorTemp, 100.0).withUnits(Deg)
      i1 shouldBe an[DoubleItem]
      i1.size shouldBe 1
      i1.units should be(Deg)
    }
  }

  describe("checking simple values") {
    val k1 = IntKey(s1)

    it("should have value access") {
      val i1 = set(k1, 1, 2, 3, 4, 5)
      i1.size should equal(5)

      i1.values should equal(Vector(1, 2, 3, 4, 5))
      i1.head should equal(1)
      i1.value(0) should equal(1)
      i1.value(1) should equal(2)
      i1.value(2) should equal(3)
      i1.value(3) should equal(4)
      i1.value(4) should equal(5)

      intercept[IndexOutOfBoundsException] {
        i1.value(5)
      }
    }
  }

  describe("work with an array type") {
    val k1 = LongArrayKey(s2)

    it("should allow setting") {
      val m1: Array[Long] = Array(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

      val i1 = k1.set(m1)
      i1.size should be(1)
      i1.head should equal(LongArray(m1))
      i1.head.value should equal(m1)
      i1.values should equal(Vector(LongArray(m1)))
      i1.values(0) should equal(LongArray(m1))
    }
  }

  describe("work with a matrix type") {
    val k1 = LongMatrixKey(s2)

    it("should allow setting") {
      val m1: Array[Array[Long]] = Array(Array(1, 2, 4), Array(2, 4, 8), Array(4, 8, 16))

      val i1 = k1.set(m1)
      i1.size should be(1)
      i1.head should equal(LongMatrix(m1))
      i1.head.value should equal(m1)
      i1.values should equal(Vector(LongMatrix(m1)))
      i1.values(0) should equal(LongMatrix(m1))
    }
  }

  describe("checking optional get values") {
    val k1 = IntKey(s1)

    it("should have value access") {
      val i1 = set(k1, 1, 2, 3, 4, 5)
      i1.size should equal(5)
      i1.values should equal(Vector(1, 2, 3, 4, 5))

      i1.get(0) should equal(Option(1))
      i1.get(1) should equal(Option(2))
      // Out of range gives None
      i1.get(9) should equal(None)
    }
  }

  describe("adding items to sc") {
    val k1 = IntKey(s1)
    val k2 = StringKey(s2)
    val k3 = DoubleKey(s3)

    it("should allow adding single items") {
      // Note sc1 is a var
      var sc1 = SetupConfig(ck1)
      sc1 = add(sc1, set(k1, 1000))
      sc1.size should be(1)
    }

    it("shoudl allow adding several at once") {
      var sc2 = SetupConfig(ck2)
      sc2 = madd(sc2, set(k1, 1000), set(k2, "1000"), set(k3, 1000.0))

      sc2.size should be(3)
      sc2.exists(k1) shouldBe true
      sc2.exists(k2) shouldBe true
      sc2.exists(k3) shouldBe true
    }
  }

  describe("accessing items in an sc") {
    val k1 = IntKey(s1)
    val k2 = StringKey(s2)
    val k3 = DoubleKey(s3)

    val i1 = set(k1, 1000)
    val i2 = set(k2, "1000")
    val i3 = set(k3, 1000.0)

    it("should allow accessing existing items") {
      var sc1 = SetupConfig(ck2)
      sc1 = madd(sc1, i1, i2, i3)
      sc1.size should be(3)

      item(sc1, k1) should equal(i1)
      item(sc1, k2) should equal(i2)
      item(sc1, k3) should equal(i3)
    }

    it("should throw NoSuchElementException if not present") {
      var sc1 = SetupConfig(ck2)
      sc1 = madd(sc1, i1, i2, i3)

      val k4 = FloatKey("not present")

      sc1.exists(k1) shouldBe true
      sc1.exists(k2) shouldBe true
      sc1.exists(k3) shouldBe true
      sc1.exists(k4) shouldBe false

      intercept[NoSuchElementException] {
        val i1 = item(sc1, k4)
      }
    }
  }

  describe("accessing items in an sc as option") {
    val k1 = IntKey(s1)
    val k2 = StringKey(s2)
    val k3 = DoubleKey(s3)

    val i1 = set(k1, 1000)
    val i2 = set(k2, "1000")
    val i3 = set(k3, 1000.0)

    it("should allow accessing existing items") {
      var sc1 = SetupConfig(ck2)
      sc1 = madd(sc1, i1, i2, i3)
      sc1.size should be(3)

      get(sc1, k1) should equal(Option(i1))
      get(sc1, k2) should equal(Option(i2))
      get(sc1, k3) should equal(Option(i3))
    }

    it("should be None if not present") {
      var sc1 = SetupConfig(ck2)
      sc1 = madd(sc1, i1, i2, i3)

      val k4 = FloatKey("not present")
      get(sc1, k1) should equal(Option(i1))
      get(sc1, k2) should equal(Option(i2))
      get(sc1, k3) should equal(Option(i3))
      get(sc1, k4) should equal(None)
    }
  }

  describe("should allow option get") {
    val k1 = IntKey(s1)
    val k2 = StringKey(s2)
    val k3 = DoubleKey(s3)
    val k4 = StringKey("Not Present")

    val i1 = set(k1, 1000, 2000)
    val i2 = set(k2, "1000", "2000")
    val i3 = set(k3, 1000.0, 2000.0)

    it("should allow accessing existing items") {
      var sc1 = SetupConfig(ck2)
      sc1 = madd(sc1, i1, i2, i3)
      sc1.size should be(3)

      get(sc1, k1, 0) should be(Some(1000))
      get(sc1, k2, 1) should be(Some("2000"))
      // Out of range
      get(sc1, k3, 3) should be(None)
      // Non existent item
      get(sc1, k4, 0) should be(None)
    }
  }

  describe("removing items from a configuration by keyname") {
    val k1 = IntKey("itest")
    val k2 = DoubleKey("dtest")
    val k3 = StringKey("stest")
    val k4 = LongArrayKey("lartest")

    val i1 = set(k1, 1, 2, 3).withUnits(UnitsOfMeasure.Deg)
    val i2 = set(k2, 1.0, 2.0, 3.0).withUnits(UnitsOfMeasure.Meters)
    val i3 = set(k3, "A", "B", "C")
    val i4 = set(k4, LongArray(Array.fill[Long](100)(10)), LongArray(Array.fill[Long](100)(100)))

    it("Should allow removing one at a time") {
      var sc1 = madd(SetupConfig(ck1), i1, i2, i3, i4)
      sc1.size should be(4)
      get(sc1, k1).isDefined should be(true)
      get(sc1, k2).isDefined should be(true)
      get(sc1, k3).isDefined should be(true)
      get(sc1, k4).isDefined should be(true)

      sc1 = remove(sc1, k1)
      sc1.size should be(3)
      get(sc1, k1).isDefined should be(false)
      get(sc1, k2).isDefined should be(true)
      get(sc1, k3).isDefined should be(true)
      get(sc1, k4).isDefined should be(true)

      sc1 = remove(sc1, k2)
      sc1.size should be(2)
      get(sc1, k1).isDefined should be(false)
      get(sc1, k2).isDefined should be(false)
      get(sc1, k3).isDefined should be(true)
      get(sc1, k4).isDefined should be(true)

      sc1 = remove(sc1, k3)
      sc1.size should be(1)
      get(sc1, k1).isDefined should be(false)
      get(sc1, k2).isDefined should be(false)
      get(sc1, k3).isDefined should be(false)
      get(sc1, k4).isDefined should be(true)

      // Should allow removing non-existent
      sc1 = remove(sc1, k3)
      sc1.size should be(1)
      get(sc1, k1).isDefined should be(false)
      get(sc1, k2).isDefined should be(false)
      get(sc1, k3).isDefined should be(false)
      get(sc1, k4).isDefined should be(true)

      sc1 = remove(sc1, k4)
      sc1.size should be(0)
      get(sc1, k1).isDefined should be(false)
      get(sc1, k2).isDefined should be(false)
      get(sc1, k3).isDefined should be(false)
      get(sc1, k4).isDefined should be(false)

      // Add allows re-adding
      sc1 = add(sc1, i4)
      sc1.size should be(1)
      get(sc1, k1).isDefined should be(false)
      get(sc1, k2).isDefined should be(false)
      get(sc1, k3).isDefined should be(false)
      get(sc1, k4).isDefined should be(true)
    }
  }

  describe("removing items from a configuration as items") {
    val k1 = IntKey("itest")
    val k2 = DoubleKey("dtest")
    val k3 = StringKey("stest")
    val k4 = LongArrayKey("lartest")

    val i1 = set(k1, 1, 2, 3).withUnits(UnitsOfMeasure.Deg)
    val i2 = set(k2, 1.0, 2.0, 3.0).withUnits(UnitsOfMeasure.Meters)
    val i3 = set(k3, "A", "B", "C")
    val i4 = set(k4, LongArray(Array.fill[Long](100)(10)), LongArray(Array.fill[Long](100)(100)))

    it("Should allow removing one at a time") {
      var sc1 = madd(SetupConfig(ck1), i1, i2, i3, i4)
      sc1.size should be(4)
      get(sc1, k1).isDefined should be(true)
      get(sc1, k2).isDefined should be(true)
      get(sc1, k3).isDefined should be(true)
      get(sc1, k4).isDefined should be(true)

      sc1 = remove(sc1, i1)
      sc1.size should be(3)
      get(sc1, k1).isDefined should be(false)
      get(sc1, k2).isDefined should be(true)
      get(sc1, k3).isDefined should be(true)
      get(sc1, k4).isDefined should be(true)

      sc1 = remove(sc1, i2)
      sc1.size should be(2)
      get(sc1, k1).isDefined should be(false)
      get(sc1, k2).isDefined should be(false)
      get(sc1, k3).isDefined should be(true)
      get(sc1, k4).isDefined should be(true)

      sc1 = remove(sc1, i3)
      sc1.size should be(1)
      get(sc1, k1).isDefined should be(false)
      get(sc1, k2).isDefined should be(false)
      get(sc1, k3).isDefined should be(false)
      get(sc1, k4).isDefined should be(true)

      // Should allow removing non-existent
      sc1 = remove(sc1, i3)
      sc1.size should be(1)
      get(sc1, k1).isDefined should be(false)
      get(sc1, k2).isDefined should be(false)
      get(sc1, k3).isDefined should be(false)
      get(sc1, k4).isDefined should be(true)

      sc1 = remove(sc1, i4)
      sc1.size should be(0)
      get(sc1, k1).isDefined should be(false)
      get(sc1, k2).isDefined should be(false)
      get(sc1, k3).isDefined should be(false)
      get(sc1, k4).isDefined should be(false)

      // Add allows re-adding
      sc1 = add(sc1, i4)
      sc1.size should be(1)
      get(sc1, k1).isDefined should be(false)
      get(sc1, k2).isDefined should be(false)
      get(sc1, k3).isDefined should be(false)
      get(sc1, k4).isDefined should be(true)
    }
  }

  describe("sc tests") {
    val k1 = IntKey("itest")
    val k2 = DoubleKey("dtest")
    val k3 = StringKey("stest")
    val k4 = LongArrayKey("lartest")

    val i1 = set(k1, 1, 2, 3).withUnits(UnitsOfMeasure.Deg)
    val i2 = set(k2, 1.0, 2.0, 3.0).withUnits(UnitsOfMeasure.Meters)
    val i3 = set(k3, "A", "B", "C")
    val i4 = set(k4, LongArray(Array.fill[Long](100)(10)), LongArray(Array.fill[Long](100)(100)))

    it("should allow creation") {
      val sc1 = sc(ck2, i1, i2)

      sc1.size should be(2)
      info("sc1: " + sc1)

    }
  }

  describe("builder tests") {
    val zeroPoint = IntKey("zeroPoint")
    val filter = StringKey("filter")
    val mode = StringKey("mode")

    val i1 = set(mode, "Fast") // default value
    val i2 = set(filter, "home") // default value
    val i3 = set(zeroPoint, 1000) // Where home is

    it("should create with defaults") {
      val fbuilder = SCBuilder(ck2, i1, i2, i3)

      val setupConfig = fbuilder.set(zeroPoint → 2000)
      val intItem = setupConfig.apply(zeroPoint)
      assert(intItem.head == 2000)
    }

    it("should allow override") {
      val fbuilder = SCBuilder(ck2, i1, i2, i3)

      val fc: SetupConfig = fbuilder.set(
        filter → "green",
        zeroPoint → 2500
      )
      item(fc, filter).head should be("green")
      info("fc: " + fbuilder)
    }

    it("test of new sc") {
      val sc1 = sc(ck2, (zeroPoint := 1000) withUnits Deg)
      info("SC: " + sc1)
    }

  }

}
