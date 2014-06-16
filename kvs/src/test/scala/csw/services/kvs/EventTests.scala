package csw.services.kvs

import org.scalatest.FunSuite
import com.typesafe.scalalogging.slf4j.LazyLogging
import redis.ByteStringFormatter
import csw.util.Configuration

object EventTests {
  val testEvent =
    """
      |tmt.mobie.red.dat.exposureInfo {
      |    exposureTime {
      |        value = 220
      |	       units = seconds
      |    }
      |    startTime = "2022-07-14 22:00:01"
      |    endTime = "2022-07-14 22:03:41"
      |}
    """.stripMargin
}

/**
 * Test the Config object
 */
class EventTests extends FunSuite with LazyLogging {

  test("Test constructing an Event from a string") {
    val event = Configuration(EventTests.testEvent)
    assert(event.getInt("tmt.mobie.red.dat.exposureInfo.exposureTime.value") == 220)
    assert(event.getString("tmt.mobie.red.dat.exposureInfo.startTime") == "2022-07-14 22:00:01")
    assert(event.getString("tmt.mobie.red.dat.exposureInfo.endTime") == "2022-07-14 22:03:41")
  }

  test("Test serializing an Event to a ByteBuffer") {
    val event = Configuration(EventTests.testEvent)
    val formatter = implicitly[ByteStringFormatter[Event]]
    assert(formatter.deserialize(formatter.serialize(event)) == event)
  }
}

