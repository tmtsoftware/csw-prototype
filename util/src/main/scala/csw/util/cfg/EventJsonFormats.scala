package csw.util.cfg

import csw.util.cfg.Events._
import spray.json._
import spray.httpx.marshalling.MetaMarshallers
import spray.httpx.SprayJsonSupport


/**
 * Support for converting Events to and from JSON (adds toJson and parseJson methods)
 */
trait EventJsonFormats extends DefaultJsonProtocol with SprayJsonSupport with MetaMarshallers {

  // JSON I/O for TelemetryEvent
  implicit object TelemetryEventJsonFormat extends RootJsonFormat[TelemetryEvent] {

    import EventJsonFormats._

    def write(event: TelemetryEvent): JsValue = {
      val items = for (v <- event.values) yield (v.name, ConfigJsonFormats.valueDataToJsValue(v.data))
      JsObject((TELEMETRY, JsObject(
        (EVENT_ID, JsString(event.eventId)),
        (TIMESTAMP, JsNumber(event.timestamp)),
        (SOURCE, JsString(event.source)),
        (event.prefix, JsObject(items.toList)))))
    }

    def read(json: JsValue): TelemetryEvent = json match {
      case JsObject(root) =>
        root(TELEMETRY) match {
          case JsObject(eventFields) =>
            val eventId = eventFields(EVENT_ID).convertTo[String]
            val timestamp = eventFields(TIMESTAMP).convertTo[Long]
            val source = eventFields(SOURCE).convertTo[String]
            val prefix = eventFields.keys.filter(!reserved(_)).head
            TelemetryEvent(eventId, timestamp, source, prefix,
              ConfigJsonFormats.JsValueToValueSet(eventFields(prefix)))
          case x => unexpectedJsValueError(x)
        }
      case x => unexpectedJsValueError(x)
    }
  }

  // JSON I/O for ObserveEvent
  implicit object ObserveEventJsonFormat extends RootJsonFormat[ObserveEvent] {

    import EventJsonFormats._

    def write(event: ObserveEvent): JsValue = {
      JsObject((OBSERVE, JsObject(
        (EVENT_ID, JsString(event.eventId)),
        (TIMESTAMP, JsNumber(event.timestamp)),
        (SOURCE, JsString(event.source)))))
    }

    def read(json: JsValue): ObserveEvent = json match {
      case JsObject(root) =>
        root(OBSERVE) match {
          case JsObject(eventFields) =>
            val eventId = eventFields(EVENT_ID).convertTo[String]
            val timestamp = eventFields(TIMESTAMP).convertTo[Long]
            val source = eventFields(SOURCE).convertTo[String]
            ObserveEvent(eventId, timestamp, source)
          case x => unexpectedJsValueError(x)
        }
      case x => unexpectedJsValueError(x)
    }

  }

  /**
   * JSON I/O for EventType trait
   */
  implicit object EventTypeJsonFormat extends RootJsonFormat[EventType] {

    import EventJsonFormats._

    def write(ct: EventType): JsValue = ct match {
      case te: TelemetryEvent => TelemetryEventJsonFormat.write(te)
      case oe: ObserveEvent => ObserveEventJsonFormat.write(oe)
    }

    def read(json: JsValue): EventType = json match {
      case JsObject(root) =>
        root.keys.head match {
          case TELEMETRY => TelemetryEventJsonFormat.read(json)
          case OBSERVE => ObserveEventJsonFormat.read(json)
          case x => deserializationError(s"Unexpected event type: $x")
        }
      case x => unexpectedJsValueError(x)
    }
  }

}


object EventJsonFormats {
  // roots
  val TELEMETRY = "telemetry"
  val OBSERVE = "observe"

  // Reserved keys
  val EVENT_ID = "eventId"
  val SOURCE = "source"
  val TIMESTAMP = "timestamp"

  val reserved = Set(EVENT_ID, SOURCE, TIMESTAMP)

  def unexpectedJsValueError(x: JsValue) = deserializationError(s"Unexpected JsValue: $x")
}
