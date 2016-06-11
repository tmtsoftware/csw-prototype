package csw.util.config

import csw.util.config.Configurations.{ObserveConfig, SetupConfig, WaitConfig}
import org.scalatest.FunSpec
import spray.json._
import ConfigJSON._
import csw.util.config.Events.{ObserveEvent, StatusEvent, SystemEvent}
import csw.util.config.StateVariable.{CurrentState, DemandState}

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
          GenericItem[MyData2]("MyData2", keyName, value, units)
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
      val s: Short = -1
      val i1 = k1.set(s).withUnits(UnitsOfMeasure.NoUnits)

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
      //      info("j1: " + j1)
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

    it("Should encode/decode a SetupConfig") {
      val c1 = SetupConfig(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7)
      assert(c1.size == 7)
      val c1out = ConfigJSON.writeConfig(c1)
      val c1in = ConfigJSON.readConfig[SetupConfig](c1out)
      assert(c1in.value(k3) == 1234L)
      assert(c1in == c1)
    }

    it("Should encode/decode an ObserveConfig") {
      val c1 = ObserveConfig(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7)
      assert(c1.size == 7)
      val c1out = ConfigJSON.writeConfig(c1)
      val c1in = ConfigJSON.readConfig[ObserveConfig](c1out)
      assert(c1in.value(k3) == 1234L)
      assert(c1in == c1)
    }

    it("Should encode/decode an StatusEvent") {
      val e1 = StatusEvent(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7)
      assert(e1.size == 7)
      val e1out = ConfigJSON.writeEvent(e1)
      val e1in = ConfigJSON.readEvent[StatusEvent](e1out)
      assert(e1in.value(k3) == 1234L)
      assert(e1in == e1)
    }

    it("Should encode/decode an ObserveEvent") {
      val e1 = ObserveEvent(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7)
      assert(e1.size == 7)
      val e1out = ConfigJSON.writeEvent(e1)
      val e1in = ConfigJSON.readEvent[ObserveEvent](e1out)
      assert(e1in.value(k3) == 1234L)
      assert(e1in == e1)
    }

    it("Should encode/decode an SystemEvent") {
      val e1 = SystemEvent(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7)
      assert(e1.size == 7)
      val e1out = ConfigJSON.writeEvent(e1)
      val e1in = ConfigJSON.readEvent[SystemEvent](e1out)
      assert(e1in.value(k3) == 1234L)
      assert(e1in == e1)
    }

    it("Should encode/decode an CurrentState") {
      val c1 = CurrentState(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7)
      assert(c1.size == 7)
      val c1out = ConfigJSON.writeConfig(c1)
      val c1in = ConfigJSON.readConfig[CurrentState](c1out)
      assert(c1in.value(k3) == 1234L)
      assert(c1in == c1)
    }

    it("Should encode/decode an DemandState") {
      val c1 = DemandState(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7)
      assert(c1.size == 7)
      val c1out = ConfigJSON.writeConfig(c1)
      val c1in = ConfigJSON.readConfig[DemandState](c1out)
      assert(c1in.value(k3) == 1234L)
      assert(c1in == c1)
    }

    it("Should encode/decode an WaitConfig") {
      val c1 = WaitConfig(ck).add(i1).add(i2).add(i3).add(i4).add(i5).add(i6).add(i7)
      assert(c1.size == 7)
      val c1out = ConfigJSON.writeConfig(c1)
      val c1in = ConfigJSON.readConfig[WaitConfig](c1out)
      assert(c1in.value(k3) == 1234L)
      assert(c1in == c1)
    }
  }

  describe("Test GenericItem") {
    it("Should allow a GenericItem with a custom type") {
      val k1 = GenericKey[MyData2]("MyData2", "testData")
      val d1 = MyData2(1, 2.0f, 3.0, "4")
      val d2 = MyData2(10, 20.0f, 30.0, "40")
      val i1 = k1.set(d1, d2).withUnits(UnitsOfMeasure.Meters)
      val sc1 = SetupConfig(ck).add(i1)
      assert(sc1.get(k1).get.values.size == 2)
      assert(sc1.get(k1).get.values(0) == d1)
      assert(sc1.get(k1).get.values(1) == d2)
      assert(sc1.get(k1).get.units == UnitsOfMeasure.Meters)

      val sc1out = ConfigJSON.writeConfig(sc1)
      //      info("2: sc1out: " + sc1out.prettyPrint)

      val sc1in = ConfigJSON.readConfig[SetupConfig](sc1out)
      assert(sc1.equals(sc1in))
      assert(sc1in.get(k1).get.values.size == 2)
      assert(sc1in.get(k1).get.values(0) == d1)
      assert(sc1in.get(k1).get.values(1) == d2)
      assert(sc1in.get(k1).get.units == UnitsOfMeasure.Meters)

      val sc2 = SetupConfig(ck).set(k1, UnitsOfMeasure.Meters, d1, d2)
      assert(sc2 == sc1)
    }
  }

  describe("Test Custom RaDecItem") {
    it("Should allow cutom RaDecItem") {
      val k1 = GenericKey[RaDec]("RaDec", "coords")
      val c1 = RaDec(7.3, 12.1)
      val c2 = RaDec(9.1, 2.9)
      val i1 = k1.set(c1, c2)
      val sc1 = SetupConfig(ck).add(i1)
      assert(sc1.get(k1).get.values.size == 2)
      assert(sc1.get(k1).get.values(0) == c1)
      assert(sc1.get(k1).get.values(1) == c2)

      val sc1out = ConfigJSON.writeConfig(sc1)
      //        info("sc1out: " + sc1out.prettyPrint)

      val sc1in = ConfigJSON.readConfig[SetupConfig](sc1out)
      assert(sc1.equals(sc1in))
      assert(sc1in.get(k1).get.values.size == 2)
      assert(sc1in.get(k1).get.values(0) == c1)
      assert(sc1in.get(k1).get.values(1) == c2)

      val sc2 = SetupConfig(ck).set(k1, UnitsOfMeasure.NoUnits, c1, c2)
      assert(sc2 == sc1)
    }
  }

  describe("Test Double Matrix items") {
    it("Should allow double matrix values") {
      val k1 = DoubleMatrixKey("myMatrix")
      val m1 = DoubleMatrix(Vector(
        Vector(1.0, 2.0, 3.0),
        Vector(4.1, 5.1, 6.1),
        Vector(7.2, 8.2, 9.2)
      ))
      val sc1 = SetupConfig(ck).set(k1, m1)
      assert(sc1.value(k1) == m1)

      val sc1out = ConfigJSON.writeConfig(sc1)
      //      info("sc1out: " + sc1out.prettyPrint)

      val sc1in = ConfigJSON.readConfig[SetupConfig](sc1out)
      assert(sc1.equals(sc1in))
      assert(sc1in.value(k1) == m1)

      val sc2 = SetupConfig(ck).set(k1, m1)
      assert(sc2 == sc1)
    }
  }

  describe("Test Double Vector items") {
    it("Should allow double vector values") {
      val k1 = DoubleVectorKey("myVector")
      val m1 = DoubleVector(Vector(1.0, 2.0, 3.0))
      val i1 = k1.set(m1)
      val sc1 = SetupConfig(ck).add(i1)
      assert(sc1.value(k1) == m1)

      val sc1out = ConfigJSON.writeConfig(sc1)
      //      info("sc1out: " + sc1out.prettyPrint)

      val sc1in = ConfigJSON.readConfig[SetupConfig](sc1out)
      assert(sc1.equals(sc1in))
      assert(sc1in.value(k1) == m1)

      val sc2 = SetupConfig(ck).set(k1, m1)
      assert(sc2 == sc1)
    }
  }

  describe("Test Int Matrix items") {
    it("Should allow int matrix values") {
      val k1 = IntMatrixKey("myMatrix")
      val m1 = IntMatrix(Vector(Vector(1, 2, 3), Vector(4, 5, 6), Vector(7, 8, 9)))
      val i1 = k1.set(m1)
      val sc1 = SetupConfig(ck).add(i1)
      assert(sc1.value(k1) == m1)

      val sc1out = ConfigJSON.writeConfig(sc1)
      //      info("sc1out: " + sc1out.prettyPrint)

      val sc1in = ConfigJSON.readConfig[SetupConfig](sc1out)
      assert(sc1.equals(sc1in))
      assert(sc1in.value(k1) == m1)

      val sc2 = SetupConfig(ck).set(k1, m1)
      assert(sc2 == sc1)
    }
  }

  describe("Test Int Vector items") {
    it("Should allow int vector values") {
      val k1 = IntVectorKey("myVector")
      val m1 = IntVector(Vector(1, 2, 3))
      val i1 = k1.set(m1)
      val sc1 = SetupConfig(ck).add(i1)
      assert(sc1.value(k1) == m1)

      val sc1out = ConfigJSON.writeConfig(sc1)
      //      info("sc1out: " + sc1out.prettyPrint)

      val sc1in = ConfigJSON.readConfig[SetupConfig](sc1out)
      assert(sc1.equals(sc1in))
      assert(sc1in.value(k1) == m1)

      val sc2 = SetupConfig(ck).set(k1, m1)
      assert(sc2 == sc1)
    }
  }

  describe("Test Byte Matrix items") {
    it("Should allow byte matrix values") {
      val k1 = ByteMatrixKey("myMatrix")
      val m1 = ByteMatrix(Vector(Vector(1, 2, 3), Vector(4, 5, 6), Vector(7, 8, 9)))
      val i1 = k1.set(m1)
      val sc1 = SetupConfig(ck).add(i1)
      assert(sc1.value(k1) == m1)

      val sc1out = ConfigJSON.writeConfig(sc1)

      val sc1in = ConfigJSON.readConfig[SetupConfig](sc1out)
      assert(sc1.equals(sc1in))
      assert(sc1in.value(k1) == m1)

      val sc2 = SetupConfig(ck).set(k1, m1)
      assert(sc2 == sc1)
    }
  }

  describe("Test Byte Vector items") {
    it("Should allow byte vector values") {
      val k1 = ByteVectorKey("myVector")
      val m1 = ByteVector(Vector(1, 2, 3))
      val i1 = k1.set(m1)
      val sc1 = SetupConfig(ck).add(i1)
      assert(sc1.value(k1) == m1)

      val sc1out = ConfigJSON.writeConfig(sc1)
      //      info("sc1out: " + sc1out.prettyPrint)

      val sc1in = ConfigJSON.readConfig[SetupConfig](sc1out)
      assert(sc1.equals(sc1in))
      assert(sc1in.value(k1) == m1)

      val sc2 = SetupConfig(ck).set(k1, m1)
      assert(sc2 == sc1)
    }
  }
}

