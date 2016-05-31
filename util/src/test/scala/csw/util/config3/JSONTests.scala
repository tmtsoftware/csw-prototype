package csw.util.config3

import csw.util.config3.Configurations.SetupConfig
import org.scalatest.FunSpec
import spray.json._
import ConfigJSON._

object JSONTests extends DefaultJsonProtocol {

  // Example custom data type for a GenericItem
  case class MyData2(i: Int, f: Float, d: Double, s: String)

  // Since automatic JSON reading doesn't work with generic types, we need to do it manually here
  case object MyData2 {
    // JSON read/write for MyData2
    implicit val myData2Format = jsonFormat4(MyData2.apply)

    // Creates a GenericItem[MyData2] from a JSON value (This didn't work with the jsonFormat3 method)
    def reader(json: JsValue): GenericItem[MyData2] = {
      json.asJsObject.getFields("keyName", "value", "units") match {
        case Seq(JsString(keyName), JsArray(v), u) ⇒
          val units = ConfigJSON.unitsFormat.read(u)
          val value = v.map(MyData2.myData2Format.read)
          GenericItem[MyData2](keyName, value, units)
        case _ ⇒ throw new DeserializationException("Color expected")
      }
    }
    GenericItem.register("MyData2", reader)
  }

}

//noinspection ScalaUnusedSymbol
class JSONTests extends FunSpec {
  import JSONTests._

  private val s1: String = "encoder"
  private val s2: String = "filter"
  private val s3: String = "detectorTemp"

  private val ck = "wfos.blue.filter"
  private val ck1 = "wfos.prog.cloudcover"
  private val ck2 = "wfos.red.filter"
  private val ck3 = "wfos.red.detector"

  describe("Test Subsystem JSON") {
    val wfos: Subsystem = Subsystem.WFOS

    it("should encode and decode properly") {
      val json = wfos.toJson
      //info("wfos: " + json)
      val sub = json.convertTo[Subsystem]
      assert(sub == wfos)
    }
  }

  describe("Test concrete items") {

    it("char item encode/decode") {
      val k1 = CharKey(s3)
      val i1 = k1.set('d').withUnits(UnitsOfMeasure.NoUnits)

      val j1 = i1.toJson
      val in1 = j1.convertTo[CharItem]
      assert(in1 == i1)
    }

    it("short item encode/decode") {
      val k1 = ShortKey(s3)
      val i1 = k1.set(-1).withUnits(UnitsOfMeasure.NoUnits)

      val j1 = i1.toJson
      val in1 = j1.convertTo[ShortItem]
      assert(in1 == i1)
    }

    it("int item encode/decode") {
      val k1 = IntKey(s3)
      val i1 = k1.set(23).withUnits(UnitsOfMeasure.NoUnits)

      val j1 = i1.toJson
      val in1 = j1.convertTo[IntItem]
      assert(in1 == i1)
    }

    it("long item encode/decode") {
      val k1 = LongKey(s1)
      val i1 = k1.set(123456L).withUnits(UnitsOfMeasure.NoUnits)

      val j1 = i1.toJson
      val in1 = j1.convertTo[LongItem]
      assert(in1 == i1)
    }

    it("float item encode/decode") {
      val k1 = FloatKey(s1)
      val i1 = k1.set(123.456f).withUnits(UnitsOfMeasure.NoUnits)

      val j1 = i1.toJson
      val in1 = j1.convertTo[FloatItem]
      assert(in1 == i1)
    }

    it("double item encode/decode") {
      val k1 = DoubleKey(s1)
      val i1 = k1.set(123.456).withUnits(UnitsOfMeasure.NoUnits)

      val j1 = i1.toJson
      val in1 = j1.convertTo[DoubleItem]
      assert(in1 == i1)
    }

    it("boolean item encode/decode") {
      val k1 = BooleanKey(s1)
      val i1 = k1.set(true, false).withUnits(UnitsOfMeasure.NoUnits)

      val j1 = i1.toJson
      info("j1: " + j1)
      val in1 = j1.convertTo[BooleanItem]
      assert(in1 == i1)

      val i2 = k1.set(true)

      val j2 = i2.toJson
      val in2 = j2.convertTo[BooleanItem]
      assert(in2 == i2)
    }

    it("string item encode/decode") {
      val k1 = StringKey(s2)
      val i1 = k1.set("Blue", "Green").withUnits(UnitsOfMeasure.NoUnits)

      val j1 = i1.toJson
      val in1 = j1.convertTo[StringItem]
      assert(in1 == i1)
    }
  }

  describe("Testing Items") {

    val k1 = IntKey(s1)
    val k2 = StringKey(s2)

    val i1 = k1.set(22, 33, 44)
    val i2 = k2.set("a", "b", "c").withUnits(UnitsOfMeasure.Deg)

    it("should encode and decode items list") {
      // Use this to get a list to test
      val sc1 = SetupConfig(ck).add(i1).add(i2)
      val items = sc1.items

      val js3 = ConfigJSON.itemsFormat.write(items)
      val in1 = ConfigJSON.itemsFormat.read(js3)
      assert(in1 == items)
    }
  }

  describe("SetupConfig JSON") {

    val k1 = CharKey("a")
    val k2 = IntKey("b")
    val k3 = LongKey("c")
    val k4 = FloatKey("d")
    val k5 = DoubleKey("e")
    val k6 = BooleanKey("f")
    val k7 = StringKey("g")

    val i1 = k1.set('d').withUnits(UnitsOfMeasure.NoUnits)
    val i2 = k2.set(22).withUnits(UnitsOfMeasure.NoUnits)
    val i3 = k3.set(1234L).withUnits(UnitsOfMeasure.NoUnits)
    val i4 = k4.set(123.45f).withUnits(UnitsOfMeasure.Deg)
    val i5 = k5.set(123.456).withUnits(UnitsOfMeasure.Meters)
    val i6 = k6.set(false)
    val i7 = k7.set("GG495").withUnits(UnitsOfMeasure.Deg)

    it("Should encode/decode a setupconfig") {
      val sc1 = SetupConfig(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7)
      assert(sc1.size == 7)

      val sc1out = ConfigJSON.writeConfig(sc1)
      info("sc1out: " + sc1out.prettyPrint)
      val sc1in = ConfigJSON.readConfig(sc1out)
    }
  }

  describe("Trying to understand GenericItem") {
    it("Should allow a GenericItem") {
      val k1 = GenericKey[MyData2]("MyData2")
      val d1 = MyData2(1, 2.0f, 3.0, "4")
      val d2 = MyData2(10, 20.0f, 30.0, "40")
      val i1 = k1.set(d1, d2).withUnits(UnitsOfMeasure.Meters)
      val sc1 = SetupConfig(ck).add(i1)
      assert(sc1.get(k1).get.value.size == 2)
      assert(sc1.get(k1).get.value(0) == d1)
      assert(sc1.get(k1).get.value(1) == d2)
      assert(sc1.get(k1).get.units == UnitsOfMeasure.Meters)

      val sc1out = ConfigJSON.writeConfig(sc1)
      info("sc1out: " + sc1out.prettyPrint)

      val sc1in = ConfigJSON.readConfig(sc1out)
      assert(sc1.equals(sc1in))
      assert(sc1in.get(k1).get.value.size == 2)
      assert(sc1in.get(k1).get.value(0) == d1)
      assert(sc1in.get(k1).get.value(1) == d2)
      assert(sc1in.get(k1).get.units == UnitsOfMeasure.Meters)
    }
  }

  // XXX TODO FIXME
  //    describe("Trying to understand GenericItem") {
  //      it("Should allow a GenericItem") {
  //        val k1 = GenericKey[String, java.lang.String]("bob")
  //        val i1 = k1.set("1", "2", "3").withUnits(UnitsOfMeasure.NoUnits)
  //        info("j1: " + i1)
  //
  //        val j1 = i1.toJson
  //        info("j1: " + j1.prettyPrint)
  //        val in1 = j1.convertTo[GenericItem[String, java.lang.String]]
  //        info("j1in: " + in1)
  //      }
  //    }
}

