package csw.services.kvs

import csw.util.cfg.Events.StatusEvent
import csw.util.cfg.StandardKeys.PERCENT_20
import csw.util.cfg.Configurations.SetupConfig
import csw.util.cfg.StandardKeys.{ cloudCover, position, exposureTime }
import org.scalatest.FunSuite
import com.typesafe.scalalogging.slf4j.LazyLogging

object EventTests {

  val statusEvent = StatusEvent("mobie.red.dat.exposureInfo").set(exposureTime, 220)

  val setupConfig = SetupConfig("wfos.red.filter")
    .set(position, "IR2")
    .set(cloudCover, PERCENT_20)
}

/**
 * Test s
 */
class EventTests extends FunSuite with LazyLogging with Implicits {
  test("Test serializing an Event to a ByteBuffer") {
    import EventTests._
    assert(statusEventKvsFormatter.deserialize(statusEventKvsFormatter.serialize(statusEvent)) == statusEvent)
    assert(setupConfigKvsFormatter.deserialize(setupConfigKvsFormatter.serialize(setupConfig)) == setupConfig)
  }
}

