package csw.services.events

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

  val statusEvent = StatusEvent("mobie.red.dat.exposureInfo").madd(
    exposureTime.set(220.0),
    cloudCover.set("20%"),
    exposureType.set("flat"),
    exposureClass.set("science")
  )

  val setupConfig = SetupConfig("wfos.red.filter")
    .add(position.set("IR2"))

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

