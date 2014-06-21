package csw.util.cfg

import csw.util.cfg.Events._
import csw.util.cfg.UnitsOfMeasure.{NoUnits, Meters, Deg}
import org.scalatest.FunSuite
import ConfigValues.ValueData._
import spray.json._

class EventJsonFormatsTests extends FunSuite with EventJsonFormats {

  val telemetryEvent = TelemetryEvent(
    eventId = "event1",
    timestamp = System.currentTimeMillis,
    source = "test1",
    "tmt.mobie.blue.filter",
    "name" -> "GG495",
    "nameList" -> List("xxx", "yyy", "zzz"),
    "nameTuple" ->("aaa", "bbb", "ccc"),
    "intList" -> List(1, 2, 3).deg,
    "intVal" -> 22.meters,
    "doubleVal" -> 3.14
  )

  val obsEvent = ObserveEvent(
    eventId = "event2",
    timestamp = System.currentTimeMillis,
    source = "test2")

  test("Test converting a TelemetryEvent to JSON and back again") {
    val e = testJson(telemetryEvent).asInstanceOf[TelemetryEvent]
    assert(e.prefix == telemetryEvent.prefix)
    assert(e.names == telemetryEvent.names)
    assert(e("name").elems.head == "GG495")
    assert(e("nameList").elems == List("xxx", "yyy", "zzz"))
    assert(e("nameTuple").elems == List("aaa", "bbb", "ccc"))
    assert(e("intList").elems == List(1, 2, 3))
    assert(e("intList").units == Deg)
    assert(e("intVal").elems.head == 22)
    assert(e("intVal").units == Meters)
    assert(e("doubleVal").elems.head == 3.14)
    assert(e("doubleVal").units == NoUnits)
  }

  test("Test converting an ObserveEvent to JSON and back again") {
    val e = testJson(obsEvent)
  }

  def testJson(event: EventType): EventType = {
    val json = event.toJson
    val s = json.prettyPrint
    val js = s.parseJson
    assert(json == js)
    val e = js.convertTo[EventType]
    val json2 = e.toJson
    assert(json == json2)
    val s2 = json2.prettyPrint
    assert(s == s2)
    assert(event.eventId == e.eventId)
    assert(event.timestamp == e.timestamp)
    assert(event.source == e.source)
    e
  }
}

