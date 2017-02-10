package csw.services.events

import csw.util.config.Events.StatusEvent
import org.scalatest.FunSuite
import com.typesafe.scalalogging.LazyLogging
import csw.util.config.{DoubleKey, StringKey}

object EventTests {

  val exposureTime = DoubleKey("exposureTime")
  val exposureType = StringKey("exposureType")
  val exposureClass = StringKey("exposureClass")
  val cloudCover = StringKey("cloudCover")
  val position = StringKey("position")

  val statusEvent = StatusEvent("mobie.red.dat.exposureInfo").madd(
    exposureTime.set(220.0),
    cloudCover.set("20%"),
    exposureType.set("flat"),
    exposureClass.set("science")
  )
}

/**
 * Test serialization and deserialization
 */
class EventTests extends FunSuite with LazyLogging {
  test("Test serializing an Event to a ByteBuffer") {
    import EventTests._
    import EventServiceImpl._
    assert(eventFormatter.deserialize(eventFormatter.serialize(statusEvent)) == statusEvent)
  }
}

