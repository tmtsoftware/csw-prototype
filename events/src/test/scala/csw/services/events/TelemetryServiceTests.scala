package csw.services.events

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import csw.util.config.Events.StatusEvent
import csw.util.config.{DoubleKey, IntKey, StringKey}
import org.scalatest.FunSuiteLike
import com.typesafe.scalalogging.slf4j.LazyLogging

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

object TelemetryServiceTests {

  // Define keys for testing
  val infoValue = IntKey("infoValue")

  val infoStr = StringKey("infoStr")

}

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
//@DoNotDiscover
class TelemetryServiceTests
    extends TestKit(ActorSystem("Test"))
    with ImplicitSender with FunSuiteLike with LazyLogging {

  import TelemetryServiceTests._
  import system.dispatcher

  val settings = EventServiceSettings(system)
  val ts = TelemetryService(settings)
  implicit val timeout = Timeout(5.seconds)
  val bts = BlockingTelemetryService(ts)
  val exposureTime = DoubleKey("exposureTime")

  // --

  test("Test simplified API with blocking set and get") {
    val prefix = "tcs.test"
    val event1 = StatusEvent(prefix)
      .add(infoValue.set(1))
      .add(infoStr.set("info 1"))

    val event2 = StatusEvent(prefix)
      .add(infoValue.set(2))
      .add(infoStr.set("info 2"))

    bts.publish(event1)
    assert(bts.get(prefix).isDefined)
    val val1: StatusEvent = bts.get(prefix).get

    assert(val1.prefix == prefix)
    assert(val1.get(infoValue).isDefined)
    assert(val1(infoValue).head == 1)
    assert(val1(infoStr).head == "info 1")

    bts.publish(event2)
    assert(bts.get(prefix).isDefined)
    val val2: StatusEvent = bts.get(prefix).get
    assert(val2(infoValue).head == 2)
    assert(val2(infoStr).head == "info 2")

    bts.delete(prefix)
    assert(bts.get(prefix).isEmpty)

    bts.delete(prefix)
  }

  test("Test blocking set, get and getHistory") {
    val prefix = "tcs.test2"
    val event = StatusEvent(prefix).add(exposureTime.set(2.0))
    val n = 3

    bts.publish(event.add(exposureTime.set(3.0)), n)
    bts.publish(event.add(exposureTime.set(4.0)), n)
    bts.publish(event.add(exposureTime.set(5.0)), n)
    bts.publish(event.add(exposureTime.set(6.0)), n)
    bts.publish(event.add(exposureTime.set(7.0)), n)
    assert(bts.get(prefix).isDefined)
    val v = bts.get(prefix).get
    val h = bts.getHistory(prefix, n + 1)
    bts.delete(prefix)
    assert(v.get(exposureTime).isDefined)
    assert(v(exposureTime).head == 7.0)
    assert(h.size == n + 1)
    for (i <- 0 to n) {
      logger.debug(s"History: $i: ${h(i)}")
    }
  }

  // --

  test("Test async set and get") {
    val prefix = "tcs.test"
    val event1 = StatusEvent(prefix)
      .add(infoValue.set(1))
      .add(infoStr.set("info 1"))

    val event2 = StatusEvent(prefix)
      .add(infoValue.set(2))
      .add(infoStr.set("info 2"))

    for {
      res1 <- ts.publish(event1)
      val1 <- ts.get(prefix)
      res2 <- ts.publish(event2)
      val2 <- ts.get(prefix)
      _ <- ts.delete(prefix)
      res3 <- ts.get(prefix)
      res4 <- ts.delete(prefix)
    } yield {
      assert(val1.exists(_.prefix == prefix))
      assert(val1.exists(_(infoValue).head == 1))
      assert(val1.exists(_(infoStr).head == "info 1"))
      assert(val2.exists(_(infoValue).head == 2))
      assert(val2.exists(_(infoStr).head == "info 2"))
      assert(res3.isEmpty)
    }
  }

  test("Test async set, get and getHistory") {
    val prefix = "tcs.test2"
    val event = StatusEvent(prefix).add(exposureTime.set(2.0))
    val n = 3

    val f = for {
      _ <- ts.publish(event.add(exposureTime.set(3.0)), n)
      _ <- ts.publish(event.add(exposureTime.set(4.0)), n)
      _ <- ts.publish(event.add(exposureTime.set(5.0)), n)
      _ <- ts.publish(event.add(exposureTime.set(6.0)), n)
      _ <- ts.publish(event.add(exposureTime.set(7.0)), n)
      v <- ts.get(prefix)
      h <- ts.getHistory(prefix, n + 1)
      _ <- ts.delete(prefix)
    } yield {
      assert(v.isDefined)
      assert(v.get(exposureTime).head == 7.0)
      assert(h.size == n + 1)
      for (i <- 0 to n) {
        logger.debug(s"History: $i: ${h(i)}")
      }
    }
    Await.result(f, 5.seconds)
  }

  // --

  test("Test subscribing to telemetry using a subscriber actor to receive status events") {
    val prefix1 = "tcs.test1"
    val prefix2 = "tcs.test2"

    val event1 = StatusEvent(prefix1)
      .add(infoValue.set(1))
      .add(infoStr.set("info 1"))

    val event2 = StatusEvent(prefix2)
      .add(infoValue.set(1))
      .add(infoStr.set("info 2"))

    // See below for actor class
    val mySubscriber = system.actorOf(MySubscriber.props(prefix1, prefix2))

    // This is just to make sure the actor has time to subscribe before we proceed
    Thread.sleep(1000)

    bts.publish(event1)
    bts.publish(event1.add(infoValue.set(2)))

    bts.publish(event2)
    bts.publish(event2.add(infoValue.set(2)))
    bts.publish(event2.add(infoValue.set(3)))

    // Make sure subscriber actor has received all events before proceeding
    Thread.sleep(1000)

    val result = Await.result((mySubscriber ? MySubscriber.GetResults).mapTo[MySubscriber.Results], 5.seconds)
    assert(result.count1 == 2)
    assert(result.count2 == 3)
    system.stop(mySubscriber)
  }
}

// Test subscriber actor for telemetry
object MySubscriber {
  def props(prefix1: String, prefix2: String): Props = Props(classOf[MySubscriber], prefix1, prefix2)

  case object GetResults
  case class Results(count1: Int, count2: Int)
}

class MySubscriber(prefix1: String, prefix2: String) extends TelemetrySubscriber {
  import MySubscriber._
  import TelemetryServiceTests._

  println(s"prefix1=$prefix1, prefix2=$prefix2")
  var count1 = 0
  var count2 = 0

  subscribe(prefix1, prefix2)

  def receive: Receive = {
    case event: StatusEvent if event.prefix == prefix1 =>
      count1 = count1 + 1
      println(s"$prefix1: assert(${event(infoValue).head} == $count1)")
      assert(event(infoValue).head == count1)
      assert(event(infoStr).head == "info 1")

    case event: StatusEvent if event.prefix == prefix2 =>
      count2 = count2 + 1
      println(s"$prefix2: assert(${event(infoValue).head} == $count2)")
      assert(event(infoValue).head == count2)
      assert(event(infoStr).head == "info 2")

    case GetResults =>
      sender() ! Results(count1, count2)
  }
}
