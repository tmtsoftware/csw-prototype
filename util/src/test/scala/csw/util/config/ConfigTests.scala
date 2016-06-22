package csw.util.config

import csw.util.config.Configurations._
import csw.util.config.Events.StatusEvent
import csw.util.config.UnitsOfMeasure._
import org.scalatest.FunSpec
import spray.json.DefaultJsonProtocol

object ConfigTests {

  import DefaultJsonProtocol._

  case class MyData(i: Int, f: Float, d: Double, s: String)

  implicit val MyDataFormat = jsonFormat4(MyData.apply)
}

//noinspection ComparingUnrelatedTypes,ScalaUnusedSymbol
class ConfigTests extends FunSpec {

  import ConfigTests._

  private val s1: String = "encoder"
  private val s2: String = "filter"
  private val s3: String = "detectorTemp"

  private val ck = "wfos.blue.filter"
  private val ck1 = "wfos.prog.cloudcover"
  private val ck2 = "wfos.red.filter"
  private val ck3 = "wfos.red.detector"

  describe("Basic key tests") {
    val k1 = IntKey(s1)
    val k2 = StringKey(s2)

    it("Should be constructed properly") {
      assert(k1.keyName eq s1)
    }

    it("Should use set properly") {
      val i = k1.set(22)
      assert(i.keyName eq s1)
      assert(i.values == Vector(22))
      assert(i.value == 22)

      assert(k2.keyName eq s2)
      val j: StringItem = k2.set("Bob").withUnits(UnitsOfMeasure.Meters)
      assert(j.values == Vector("Bob"))

      // Try default
      val k = k2.set("Bob")
    }

    it("Should support equality of keys") {
      val k3 = IntKey(s1)
      assert(k3 == k1)
      assert(k3 != k2)
      assert(k1 != k2)
    }
  }

  describe("Generic key tests") {
    // Note: It is recommended to use the standard keys, such as IntKey, StringKey, DoubleKey, etc.
    val k1 = GenericKey[MyData]("MyData", "atest")
    val d1 = MyData(1, 2.0f, 3.0, "4")
    val d2 = MyData(10, 20.0f, 30.0, "40")

    it("Should allow an Int array") {
      val i1 = k1.set(d1, d2).withUnits(UnitsOfMeasure.NoUnits)
      assert(i1.values == Vector(d1, d2))
      assert(i1(0) == d1)
      assert(i1(1) == d2)
      assert(i1(0).i == 1)
    }
  }

  describe("Checking key updates") {
    val k1: IntKey = new IntKey("atest")

    it("Should allow updates") {
      val i1 = k1.set(22)
      assert(i1.value == 22)
      assert(i1.units == NoUnits)
      val i2 = k1.set(33)
      assert(i2.value == 33)
      assert(i2.units == NoUnits)

      var sc = SetupConfig(ck1).add(i1)
      assert(sc.get(k1).get.value == 22)
      assert(sc.value(k1) == 22)
      assert(sc.values(k1) == Vector(22))
      assert(sc.value(k1, 0) == 22)
      sc = sc.add(i2)
      assert(sc.get(k1).get.value == 33)
    }
  }

  describe("Test Long") {
    it("should allow setting from Long") {
      val tval = 1234L
      val k1 = LongKey(s1)
      val i1 = k1.set(tval)
      assert(i1.values == Vector(tval))
      assert(i1.values(0) == tval)
      assert(i1.value == tval)

      val tval2 = 4567L
      val k2 = LongKey(s1)
      val i2 = k2.set(tval2)
      assert(i2.values == Vector(tval2))
    }
  }

  describe("SC Test") {

    val k1 = IntKey("encoder")
    val k2 = IntKey("windspeed")
    val k3 = IntKey("notUsed")

    it("Should allow adding keys") {
      var sc1 = SetupConfig(ck3).set(k1, 22).set(k2, 44)
      assert(sc1.size == 2)
      assert(sc1.exists(k1))
      assert(sc1.exists(k2))
      assert(sc1.value(k1) == 22)
      assert(sc1.value(k2) == 44)
      assert(sc1.missingKeys(k1, k2, k3) == Set(k3.keyName))
    }

    it("Should allow setting") {
      var sc1 = SetupConfig(ck1)
      sc1 = sc1.set(k1, NoUnits, 22).set(k2, NoUnits, 44)
      assert(sc1.size == 2)
      assert(sc1.exists(k1))
      assert(sc1.exists(k2))
    }

    it("Should allow apply") {
      var sc1 = SetupConfig(ck1)
      sc1 = sc1.set(k1, NoUnits, 22).set(k2, NoUnits, 44)

      val v1 = sc1(k1)
      val v2 = sc1(k2)
      assert(sc1.get(k1).isDefined)
      assert(sc1.get(k2).isDefined)
      assert(v1 == Vector(22))
      assert(v2 == Vector(44))
    }

    it("should update for the same key with set") {
      var sc1 = SetupConfig(ck1)
      sc1 = sc1.set(k2, NoUnits, 22)
      assert(sc1.exists(k2))
      assert(sc1(k2) == Vector(22))

      sc1 = sc1.set(k2, NoUnits, 33)
      assert(sc1.exists(k2))
      assert(sc1(k2) == Vector(33))
    }
  }

  describe("StatusEvent Test") {

    val k1 = IntKey("encoder")
    val k2 = IntKey("windspeed")
    val k3 = IntKey("notUsed")

    it("Should allow adding keys") {
      var sc1 = StatusEvent(ck3).set(k1, 22).set(k2, 44)
      assert(sc1.size == 2)
      assert(sc1.exists(k1))
      assert(sc1.exists(k2))
      assert(sc1.value(k1) == 22)
      assert(sc1.value(k2) == 44)
      assert(sc1.missingKeys(k1, k2, k3) == Set(k3.keyName))
    }

    it("Should allow setting") {
      var sc1 = StatusEvent(ck1)
      sc1 = sc1.set(k1, NoUnits, 22).set(k2, NoUnits, 44)
      assert(sc1.size == 2)
      assert(sc1.exists(k1))
      assert(sc1.exists(k2))
    }

    it("Should allow apply") {
      var sc1 = StatusEvent(ck1)
      sc1 = sc1.set(k1, NoUnits, 22).set(k2, NoUnits, 44)

      val v1 = sc1(k1)
      val v2 = sc1(k2)
      assert(sc1.get(k1).isDefined)
      assert(sc1.get(k2).isDefined)
      assert(v1 == Vector(22))
      assert(v2 == Vector(44))
    }

    it("should update for the same key with set") {
      var sc1 = StatusEvent(ck1)
      sc1 = sc1.set(k2, NoUnits, 22)
      assert(sc1.exists(k2))
      assert(sc1(k2) == Vector(22))

      sc1 = sc1.set(k2, NoUnits, 33)
      assert(sc1.exists(k2))
      assert(sc1(k2) == Vector(33))
    }
  }

  describe("OC Test") {

    val k1 = IntKey("repeat")
    val k2 = IntKey("expTime")
    it("Should allow adding keys") {
      var oc1 = ObserveConfig(ck3).set(k1, 22).set(k2, 44)
      assert(oc1.size == 2)
      assert(oc1.exists(k1))
      assert(oc1.exists(k2))
      assert(oc1.value(k1) == 22)
      assert(oc1.value(k2) == 44)
    }

    it("Should allow setting") {
      var oc1 = ObserveConfig(ck1)
      oc1 = oc1.set(k1, NoUnits, 22).set(k2, NoUnits, 44)
      assert(oc1.size == 2)
      assert(oc1.exists(k1))
      assert(oc1.exists(k2))
    }

    it("Should allow apply") {
      var oc1 = ObserveConfig(ck1)
      oc1 = oc1.set(k1, NoUnits, 22).set(k2, NoUnits, 44)

      val v1 = oc1(k1)
      val v2 = oc1(k2)
      assert(oc1.get(k1).isDefined)
      assert(oc1.get(k2).isDefined)
      assert(v1 == Vector(22))
      assert(v2 == Vector(44))
    }

    it("should update for the same key with set") {
      var oc1 = ObserveConfig(ck1)
      oc1 = oc1.set(k2, NoUnits, 22)
      assert(oc1.exists(k2))
      assert(oc1(k2) == Vector(22))

      oc1 = oc1.set(k2, NoUnits, 33)
      assert(oc1.exists(k2))
      assert(oc1(k2) == Vector(33))
    }

    it("should update for the same key with add") {
      var oc1 = ObserveConfig(ck1)
      oc1 = oc1.add(k2.set(22).withUnits(NoUnits))
      assert(oc1.exists(k2))
      assert(oc1(k2) == Vector(22))

      oc1 = oc1.add(k2.set(33).withUnits(NoUnits))
      assert(oc1.exists(k2))
      assert(oc1(k2) == Vector(33))
    }
  }

  it("should update for the same key with set") {
    val k1 = IntKey("encoder")
    val k2 = StringKey("windspeed")

    var sc1 = SetupConfig(ck1)
    sc1 = sc1.set(k1, NoUnits, 22)
    assert(sc1.exists(k1))
    assert(sc1(k1) == Vector(22))

    sc1 = sc1.set(k2, NoUnits, "bob")
    assert(sc1.exists(k2))
    assert(sc1(k2) == Vector("bob"))

    sc1.items.foreach {
      case _: IntItem    ⇒ info("IntItem")
      case _: StringItem ⇒ info("StringItem")
    }
  }

  describe("test setting multiple values") {

    val t1 = IntKey("test1")
    it("should allow setting a single value") {
      val i1 = t1.set(1)
      assert(i1.values == Vector(1))
      assert(i1.units == NoUnits)
      assert(i1(0) == 1)
    }
    it("should allow setting several") {
      val i1 = t1.set(1, 3, 5, 7)
      assert(i1.values == Vector(1, 3, 5, 7))
      assert(i1.units == NoUnits)
      assert(i1(1) == 3)

      val i2 = t1.set(Vector(10, 30, 50, 70)).withUnits(Deg)
      assert(i2.values == Vector(10, 30, 50, 70))
      assert(i2.units == Deg)
      assert(i2(1) == 30)
      assert(i2(3) == 70)
    }
    it("should also allow setting with sequence") {
      val s1 = Vector(2, 4, 6, 8)
      val i1 = t1.set(s1).withUnits(Meters)
      assert(i1.values == s1)
      assert(i1.values.size == s1.size)
      assert(i1.units == Meters)
      assert(i1(2) == 6)
    }
  }

  describe("test SetupConfigArg") {
    val encoder1 = IntKey("encoder1")
    val encoder2 = IntKey("encoder2")
    val xOffset = IntKey("xOffset")
    val yOffset = IntKey("yOffset")
    val obsId = "Obs001"

    val sc1 = SetupConfig(ck1).set(encoder1, 22).set(encoder2, 33)
    val sc2 = SetupConfig(ck1).set(xOffset, 1).set(yOffset, 2)
    val configArg = SetupConfigArg(obsId, sc1, sc2)
    assert(configArg.info.obsId.obsId == obsId)
    assert(configArg.configs.toList == List(sc1, sc2))
  }

  describe("testing for getting typed items") {
    val t1:IntKey = IntKey("test1")
    val sc1 = SetupConfig(ck1).set(t1, 22)

    val item:Option[IntItem] = sc1.get(t1)
  }

  describe("test ObserveConfigArg") {
    val encoder1 = IntKey("encoder1")
    val encoder2 = IntKey("encoder2")
    val xOffset = IntKey("xOffset")
    val yOffset = IntKey("yOffset")
    val obsId = "Obs001"

    val sc1 = ObserveConfig(ck1).set(encoder1, 22).set(encoder2, 33)
    val sc2 = ObserveConfig(ck1).set(xOffset, 1).set(yOffset, 2)
    assert(sc1.get(xOffset).isEmpty)
    assert(sc1.get(xOffset, 0).isEmpty)
    assert(sc2.get(xOffset).isDefined)
    assert(sc2.get(xOffset, 0).isDefined)
    val configArg = ObserveConfigArg(obsId, sc1, sc2)
    assert(configArg.info.obsId.obsId equals obsId)
    assert(configArg.configs.toList == List(sc1, sc2))
  }
}
