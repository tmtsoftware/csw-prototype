package csw.services.events

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.util.config.Events.StatusEvent
import csw.util.config.{DoubleKey, IntKey, StringKey}
import org.scalatest.FunSuiteLike

import scala.concurrent.duration._

object BlockingTelemetryServiceTests {

  // Define keys for testing
  val infoValue = IntKey("infoValue")

  val infoStr = StringKey("infoStr")

  val exposureTime = DoubleKey("exposureTime")

}

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
//@DoNotDiscover
class BlockingTelemetryServiceTests
    extends TestKit(ActorSystem("Test"))
    with ImplicitSender with FunSuiteLike with LazyLogging {

  import BlockingTelemetryServiceTests._

  val settings = EventServiceSettings(system)
  val ts = BlockingTelemetryService(5.seconds, settings)

  test("Test set and get") {
    val event1 = StatusEvent("tcs.telem.test1")
      .add(infoValue.set(1))
      .add(infoStr.set("info 1"))

    val event2 = StatusEvent("tcs.telem.test2")
      .add(infoValue.set(2))
      .add(infoStr.set("info 2"))

    ts.publish(event1)
    ts.get(event1.prefix).get match {
      case event: StatusEvent =>
        assert(event.prefix == event1.prefix)
        assert(event(infoValue).head == 1)
        assert(event(infoStr).head == "info 1")
      case _ => fail("Expected a StatusEvent")
    }

    ts.publish(event2)
    ts.get(event2.prefix).get match {
      case event: StatusEvent =>
        assert(event(infoValue).head == 2)
        assert(event(infoStr).head == "info 2")
      case _ => fail("Expected a StatusEvent")
    }

    ts.delete(event1.prefix)
    ts.delete(event2.prefix)

    assert(ts.get(event1.prefix).isEmpty)
    assert(ts.get(event2.prefix).isEmpty)
  }

  test("Test set, get and getHistory") {
    val prefix = "tcs.telem.testPrefix"
    val event = StatusEvent("tcs.telem.testPrefix").add(exposureTime.set(2.0))
    val n = 3

    ts.publish(event.add(exposureTime.set(3.0)), n)
    ts.publish(event.add(exposureTime.set(4.0)), n)
    ts.publish(event.add(exposureTime.set(5.0)), n)
    ts.publish(event.add(exposureTime.set(6.0)), n)
    ts.publish(event.add(exposureTime.set(7.0)), n)

    ts.get(prefix).get match {
      case event: StatusEvent =>
        assert(event(exposureTime).head == 7.0)
      case _ => fail("Expected a StatusEvent")
    }

    val h = ts.getHistory(prefix, n + 1)
    assert(h.size == n + 1)
    for (i <- 0 to n) {
      logger.debug(s"History: $i: ${h(i)}")
    }

    ts.delete(prefix)
  }

  test("Simple Test") {
    val key = StringKey("testKey")
    val e1 = StatusEvent("test").add(key.set("Test Passed"))
    ts.publish(e1)
    val e2Opt = ts.get("test")
    assert(e2Opt.isDefined)
    assert(e1 == e2Opt.get)
    println(e2Opt.get(key))
  }
}

