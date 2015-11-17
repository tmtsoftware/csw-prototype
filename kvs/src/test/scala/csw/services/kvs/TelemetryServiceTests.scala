package csw.services.kvs

import akka.testkit.{ ImplicitSender, TestKit }
import akka.actor.ActorSystem
import csw.util.cfg.Events.StatusEvent
import csw.util.cfg.Key
import csw.util.cfg.StandardKeys._
import org.scalatest.{ DoNotDiscover, FunSuiteLike }
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.Await
import scala.concurrent.duration._

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
  val telemetryService = TelemetryService(settings)


  test("Test Set and Get") {
    val prefix = "tcs.test"
    val event1 = StatusEvent(prefix)
      .set(infoValue, 1)
      .set(infoStr, "info 1")

    val event2 = StatusEvent(prefix)
      .set(infoValue, 2)
      .set(infoStr, "info 2")

    for {
      res1 <- telemetryService.set(event1)
      val1 <- telemetryService.get(prefix)
      res2 <- telemetryService.set(event2)
      val2 <- telemetryService.get(prefix)
      _ ← telemetryService.delete(prefix)
      res3 ← telemetryService.get(prefix)
      res4 ← telemetryService.delete(prefix)
    } yield {
      assert(val1.exists(_.prefix == prefix))
      assert(val1.exists(_.get(infoValue).contains(1)))
      assert(val1.exists(_.get(infoStr).contains("info 1")))
      assert(val2.exists(_.get(infoValue).contains(2)))
      assert(val2.exists(_.get(infoStr).contains("info 2")))
      assert(res3.isEmpty)
    }
  }

  test("Test set, get and getHistory") {
    val prefix = "tcs.test2"
    val event = StatusEvent(prefix).set(exposureTime, 2)
    val n = 3

    val f = for {
      _ ← telemetryService.set(event.set(exposureTime, 3), n)
      _ ← telemetryService.set(event.set(exposureTime, 4), n)
      _ ← telemetryService.set(event.set(exposureTime, 5), n)
      _ ← telemetryService.set(event.set(exposureTime, 6), n)
      _ ← telemetryService.set(event.set(exposureTime, 7), n)
      v ← telemetryService.get(prefix)
      h ← telemetryService.getHistory(prefix, n + 1)
      _ ← telemetryService.delete(prefix)
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
}

