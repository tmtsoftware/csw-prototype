package csw.util.config

import java.nio.ByteBuffer
import java.util.Date

import csw.util.config.ConfigKeys._
import csw.util.config.Events.{TelemetryEvent, ObserveEvent, EventType}
import csw.util.config.StandardKeys._
import org.scalatest.FunSuite

/**
 * Tests the event classes
 */
class EventTests extends FunSuite {

  def timestamp(): Long = new Date().getTime

  val source = "test"

  test("Event pickling") {
    import scala.pickling.Defaults._
    import scala.pickling.binary._

    val obsEvent = ObserveEvent("event1", timestamp(), source,
      ConfigData()
        .set(exposureType)(FLAT)
        .set(exposureClass)(ACQUISITION)
        .set(repeats)(3))

    val telEvent = TelemetryEvent("event2", timestamp(), source, "my.prefix",
      ConfigData()
        .set(cloudCover)(PERCENT_90)
        .set(position)("12:34:56, 03:22:11"))


    val o = obsEvent.pickle.value
    val t = telEvent.pickle.value

    val obsEvent2 = o.unpickle[ObserveEvent]
    val telEvent2 = t.unpickle[TelemetryEvent]

    assert(obsEvent == obsEvent2)
    assert(telEvent == telEvent2)

  }
}
