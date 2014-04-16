package org.tmt.csw.kvs

import org.scalatest.FunSuite
import com.typesafe.scalalogging.slf4j.Logging
import com.typesafe.config.ConfigFactory
import java.io.StringReader
import redis.ByteStringFormatter
import akka.util.ByteString

object TestEvent {
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
class TestEvent extends FunSuite with Logging {

  test("Test constructing an Event from a string") {
    val event = Event(TestEvent.testEvent)
    assert(event.getInt("tmt.mobie.red.dat.exposureInfo.exposureTime.value") == 220)
    assert(event.getString("tmt.mobie.red.dat.exposureInfo.startTime") == "2022-07-14 22:00:01")
    assert(event.getString("tmt.mobie.red.dat.exposureInfo.endTime") == "2022-07-14 22:03:41")
  }

  test("Test serializing an Event to a ByteBuffer") {
    val event = Event(TestEvent.testEvent)
    val formatter = implicitly[ByteStringFormatter[Event]]
    assert(formatter.deserialize(formatter.serialize(event)) == event)
  }
}
