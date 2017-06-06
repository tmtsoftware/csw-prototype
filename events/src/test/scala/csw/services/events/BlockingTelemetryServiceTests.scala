package csw.services.events

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import csw.services.loc.LocationService
import csw.util.param.Events.StatusEvent
import csw.util.param.{DoubleKey, IntKey, StringKey}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.Await
import scala.concurrent.duration._

object BlockingTelemetryServiceTests {
  LocationService.initInterface()
  val system = ActorSystem("BlockingTelemetryServiceTests")

  // Define keys for testing
  val infoValue = IntKey("infoValue")

  val infoStr = StringKey("infoStr")

  val exposureTime = DoubleKey("exposureTime")

}

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
//@DoNotDiscover
class BlockingTelemetryServiceTests
    extends TestKit(BlockingTelemetryServiceTests.system)
    with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll {

  import BlockingTelemetryServiceTests._
  import system.dispatcher

  implicit val timeout = Timeout(10.seconds)

  val ts = Await.result(TelemetryService(), timeout.duration)
  val tsAdmin = TelemetryServiceAdmin(ts)

  override protected def afterAll(): Unit = {
    system.terminate()
  }

  test("Test set and get") {
    val bts = BlockingTelemetryService(ts, 5.seconds)
    val event1 = StatusEvent("tcs.telem.test1")
      .add(infoValue.set(1))
      .add(infoStr.set("info 1"))

    val event2 = StatusEvent("tcs.telem.test2")
      .add(infoValue.set(2))
      .add(infoStr.set("info 2"))

    bts.publish(event1)
    bts.get(event1.prefixStr).get match {
      case event: StatusEvent =>
        assert(event.prefixStr == event1.prefixStr)
        assert(event(infoValue).head == 1)
        assert(event(infoStr).head == "info 1")
      case _ => fail("Expected a StatusEvent")
    }

    bts.publish(event2)
    bts.get(event2.prefixStr).get match {
      case event: StatusEvent =>
        assert(event(infoValue).head == 2)
        assert(event(infoStr).head == "info 2")
      case _ => fail("Expected a StatusEvent")
    }

    bts.delete(event1.prefixStr)
    bts.delete(event2.prefixStr)

    assert(bts.get(event1.prefixStr).isEmpty)
    assert(bts.get(event2.prefixStr).isEmpty)
  }

  test("Test set, get and getHistory") {
    val bts = BlockingTelemetryService(ts, 5.seconds)
    val prefix = "tcs.telem.testPrefix"
    val event = StatusEvent("tcs.telem.testPrefix").add(exposureTime.set(2.0))
    val n = 3

    bts.publish(event.add(exposureTime.set(3.0)), n)
    bts.publish(event.add(exposureTime.set(4.0)), n)
    bts.publish(event.add(exposureTime.set(5.0)), n)
    bts.publish(event.add(exposureTime.set(6.0)), n)
    bts.publish(event.add(exposureTime.set(7.0)), n)

    bts.get(prefix).get match {
      case event: StatusEvent =>
        assert(event(exposureTime).head == 7.0)
      case _ => fail("Expected a StatusEvent")
    }

    val h = bts.getHistory(prefix, n + 1)
    assert(h.size == n + 1)
    for (i <- 0 to n) {
      logger.debug(s"History: $i: ${h(i)}")
    }

    bts.delete(prefix)
  }

  test("Simple Test") {
    val bts = BlockingTelemetryService(ts, 5.seconds)
    val key = StringKey("testKey")
    val e1 = StatusEvent("test").add(key.set("Test Passed"))
    bts.publish(e1)
    val e2Opt = bts.get("test")
    assert(e2Opt.isDefined)
    assert(e1 == e2Opt.get)
    println(e2Opt.get(key))
  }
}

