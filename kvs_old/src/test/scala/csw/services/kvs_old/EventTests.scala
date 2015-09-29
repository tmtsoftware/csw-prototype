package csw.services.kvs_old

import csw.util.cfg_old.ConfigValues.ValueData
import csw.util.cfg_old.Events.TelemetryEvent
import org.scalatest.FunSuite
import com.typesafe.scalalogging.slf4j.LazyLogging
import redis.ByteStringFormatter

object EventTests {

  import ValueData._

  val testEvent = TelemetryEvent(
    source = "test",
    "tmt.mobie.red.dat.exposureInfo",
    "exposureTime" -> 220.seconds,
    "startTime" -> "2022-07-14 22:00:01",
    "endTime" -> "2022-07-14 22:03:41")
}

/**
 * Test the Config object
 */
class EventTests extends FunSuite with LazyLogging {

  test("Test serializing an Event to a ByteBuffer") {
    val event = EventTests.testEvent
    val formatter = implicitly[ByteStringFormatter[Event]]
    assert(formatter.deserialize(formatter.serialize(event)) == event)
  }
}

