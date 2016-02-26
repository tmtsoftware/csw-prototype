package csw.services.kvs

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import akka.util.Timeout
import csw.util.cfg.Events.StatusEvent
import csw.util.cfg.Key
import csw.util.cfg.StandardKeys._
import org.scalatest.{DoNotDiscover, FunSuiteLike}
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

object TelemetryServiceTests {

  // Define keys for testing
  val infoValue = Key.create[Int]("infoValue")

  val infoStr = Key.create[String]("infoStr")

}

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
//@DoNotDiscover
class TelemetryServiceTests
    extends TestKit(ActorSystem("Test"))
    with ImplicitSender with FunSuiteLike with LazyLogging with Implicits {

  import TelemetryServiceTests._
  import system.dispatcher

  val settings = KvsSettings(system)
  val ts = TelemetryService(settings)
  implicit val timeout = Timeout(5.seconds)
  val bts = BlockingTelemetryService(ts)

  // --

  test("Test simplified API with blocking set and get") {
    val prefix = "tcs.test"
    val event1 = StatusEvent(prefix)
      .set(infoValue, 1)
      .set(infoStr, "info 1")

    val event2 = StatusEvent(prefix)
      .set(infoValue, 2)
      .set(infoStr, "info 2")

    bts.set(event1)
    assert(bts.get(prefix).isDefined)
    val val1 = bts.get(prefix).get
    assert(val1.prefix == prefix)
    assert(val1.get(infoValue).isDefined)
    assert(val1.get(infoValue).get == 1)
    assert(val1.get(infoStr).get == "info 1")

    bts.set(event2)
    assert(bts.get(prefix).isDefined)
    val val2 = bts.get(prefix).get
    assert(val2.get(infoValue).get == 2)
    assert(val2.get(infoStr).get == "info 2")

    bts.delete(prefix)
    assert(bts.get(prefix).isEmpty)

    bts.delete(prefix)
  }

  test("Test blocking set, get and getHistory") {
    val prefix = "tcs.test2"
    val event = StatusEvent(prefix).set(exposureTime, 2)
    val n = 3

    bts.set(event.set(exposureTime, 3), n)
    bts.set(event.set(exposureTime, 4), n)
    bts.set(event.set(exposureTime, 5), n)
    bts.set(event.set(exposureTime, 6), n)
    bts.set(event.set(exposureTime, 7), n)
    assert(bts.get(prefix).isDefined)
    val v = bts.get(prefix).get
    val h = bts.getHistory(prefix, n + 1)
    bts.delete(prefix)
    assert(v.get(exposureTime).isDefined)
    assert(v.get(exposureTime).get == 7.0)
    assert(h.size == n + 1)
    for (i ← 0 to n) {
      logger.info(s"History: $i: ${h(i)}")
    }
  }

  // --

  test("Test async set and get") {
    val prefix = "tcs.test"
    val event1 = StatusEvent(prefix)
      .set(infoValue, 1)
      .set(infoStr, "info 1")

    val event2 = StatusEvent(prefix)
      .set(infoValue, 2)
      .set(infoStr, "info 2")

    for {
      res1 ← ts.set(event1)
      val1 ← ts.get(prefix)
      res2 ← ts.set(event2)
      val2 ← ts.get(prefix)
      _ ← ts.delete(prefix)
      res3 ← ts.get(prefix)
      res4 ← ts.delete(prefix)
    } yield {
      assert(val1.exists(_.prefix == prefix))
      assert(val1.exists(_.get(infoValue).contains(1)))
      assert(val1.exists(_.get(infoStr).contains("info 1")))
      assert(val2.exists(_.get(infoValue).contains(2)))
      assert(val2.exists(_.get(infoStr).contains("info 2")))
      assert(res3.isEmpty)
    }
  }

  test("Test async set, get and getHistory") {
    val prefix = "tcs.test2"
    val event = StatusEvent(prefix).set(exposureTime, 2)
    val n = 3

    val f = for {
      _ ← ts.set(event.set(exposureTime, 3), n)
      _ ← ts.set(event.set(exposureTime, 4), n)
      _ ← ts.set(event.set(exposureTime, 5), n)
      _ ← ts.set(event.set(exposureTime, 6), n)
      _ ← ts.set(event.set(exposureTime, 7), n)
      v ← ts.get(prefix)
      h ← ts.getHistory(prefix, n + 1)
      _ ← ts.delete(prefix)
    } yield {
      assert(v.isDefined)
      assert(v.get.get(exposureTime).get == 7.0)
      assert(h.size == n + 1)
      for (i ← 0 to n) {
        logger.info(s"History: $i: ${h(i)}")
      }
    }
    Await.result(f, 5.seconds)
  }

  // --

  test("Test subscribing to telemetry using a subscriber actor to receive status events") {
    val prefix1 = "tcs.test1"
    val prefix2 = "tcs.test2"

    val event1 = StatusEvent(prefix1)
      .set(infoValue, 1)
      .set(infoStr, "info 1")

    val event2 = StatusEvent(prefix2)
      .set(infoValue, 1)
      .set(infoStr, "info 2")

    // See below for actor class
    val mySubscriber = system.actorOf(MySubscriber.props(prefix1, prefix2))

    // This is just to make sure the actor has time to subscribe before we proceed
    Thread.sleep(1000)

    bts.set(event1)
    bts.set(event1.set(infoValue, 2))

    bts.set(event2)
    bts.set(event2.set(infoValue, 2))
    bts.set(event2.set(infoValue, 3))

    // Make sure subscriber actor has received all events before proceeding
    Thread.sleep(1000)

    val result = Await.result((mySubscriber ? MySubscriber.GetResults).mapTo[MySubscriber.Results], 5.seconds)
    assert(result.count1 == 2)
    assert(result.count2 == 3)
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

  var count1 = 0
  var count2 = 0

  subscribe(prefix1, prefix2)

  def receive: Receive = {
    case event: StatusEvent if event.prefix == prefix1 ⇒
      count1 = count1 + 1
      assert(event.get(infoValue).get == count1)
      assert(event.get(infoStr).get == "info 1")

    case event: StatusEvent if event.prefix == prefix2 ⇒
      count2 = count2 + 1
      assert(event.get(infoValue).get == count2)
      assert(event.get(infoStr).get == "info 2")

    case GetResults ⇒
      sender() ! Results(count1, count2)
  }
}
