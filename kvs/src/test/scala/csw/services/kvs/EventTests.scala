package csw.services.kvs

import csw.util.config.ConfigKeys.PERCENT_20
import csw.util.config.Configurations.SetupConfig
import csw.util.config.Events.TelemetryEvent
import csw.util.config.StandardKeys.{cloudCover, position, exposureTime}
import org.scalatest.FunSuite
import com.typesafe.scalalogging.slf4j.LazyLogging

object EventTests {

  val telemetryEvent = TelemetryEvent(
    source = "test",
    prefix = "tmt.mobie.red.dat.exposureInfo")
    .set(exposureTime)(220) // XXX make it a duration!
  //    "startTime" -> "2022-07-14 22:00:01",
  //    "endTime" -> "2022-07-14 22:03:41")

  val setupConfig = SetupConfig("wfos.red.filter")
    .set(position)("IR2")
    .set(cloudCover)(PERCENT_20)
}

/**
 * Test s
 */
class EventTests extends FunSuite with LazyLogging with Implicits {
  test("Test serializing an Event to a ByteBuffer") {
    import EventTests._
    assert(telemetryEventKvsFormatter.deserialize(telemetryEventKvsFormatter.serialize(telemetryEvent)) == telemetryEvent)
    assert(setupConfigKvsFormatter.deserialize(setupConfigKvsFormatter.serialize(setupConfig)) == setupConfig)
  }
}

