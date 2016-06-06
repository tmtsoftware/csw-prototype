package csw.services.kvs

import csw.util.config.Events.StatusEvent
import csw.util.config.Configurations.SetupConfig
import org.scalatest.FunSuite
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.util.config.{DoubleKey, StringKey}

object EventTests {

  val exposureTime = DoubleKey("exposureTime")
  val exposureType = StringKey("exposureType")
  val exposureClass = StringKey("exposureClass")
  val cloudCover = StringKey("cloudCover")
  val position = StringKey("position")

  val statusEvent = StatusEvent("mobie.red.dat.exposureInfo")
    .set(exposureTime, 220.0)
    .set(cloudCover, "20%")
    .set(exposureType, "flat")
    .set(exposureClass, "science")

  val setupConfig = SetupConfig("wfos.red.filter")
    .set(position, "IR2")

}

/**
 * Test serialization and deserialization
 */
class EventTests extends FunSuite with LazyLogging with Implicits {
  test("Test serializing an Event to a ByteBuffer") {
    import EventTests._
    assert(statusEventKvsFormatter.deserialize(statusEventKvsFormatter.serialize(statusEvent)) == statusEvent)
    assert(setupConfigKvsFormatter.deserialize(setupConfigKvsFormatter.serialize(setupConfig)) == setupConfig)
  }
}

