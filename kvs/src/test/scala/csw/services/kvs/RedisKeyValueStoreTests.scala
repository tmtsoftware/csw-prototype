package csw.services.kvs

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import csw.util.config.ConfigKeys.{StringValued, IntValued}
import csw.util.config.Events.TelemetryEvent
import csw.util.config.Key
import csw.util.config.StandardKeys.exposureTime
import org.scalatest.{DoNotDiscover, BeforeAndAfterAll, FunSuiteLike}
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.Await
import scala.concurrent.duration._

object RedisKeyValueStoreTests {

  // Define keys for testing
  case object eventId extends Key("eventId") with IntValued

  case object infoStr extends Key("infoStr") with StringValued

}

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
@DoNotDiscover
class RedisKeyValueStoreTests
  extends TestKit(ActorSystem("Test"))
  with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll with Implicits {

  import RedisKeyValueStoreTests._

  implicit val execContext = system.dispatcher
  val kvs: KeyValueStore[TelemetryEvent] = RedisKeyValueStore[TelemetryEvent]

  test("Test Set and Get") {
    val event1 = TelemetryEvent(source = "test", prefix = "test")
      .set(eventId)(1)
      .set(infoStr)("info 1")
    val event2 = TelemetryEvent(source = "test", prefix = "test")
      .set(eventId)(2)
      .set(infoStr)("info 2")

    val f = for {
      res1 ← kvs.set("test1", event1)
      val1 ← kvs.get("test1")
      res2 ← kvs.set("test2", event2)
      val2 ← kvs.get("test2")
      res3 ← kvs.delete("test1", "test2")
      res4 ← kvs.get("test1")
      res5 ← kvs.get("test2")
      res6 ← kvs.delete("test1", "test2")
      res7 <- kvs.hmset("testx", event1)
      res8 <- kvs.hmget("testx", eventId.name)
      res9 <- kvs.hmget("testx", infoStr.name)
    } yield {
        assert(res1)
        assert(val1.exists(_.source == "test"))
        assert(val1.exists(_.prefix == "test"))
        assert(val1.exists(_.get(eventId).contains(1)))
        assert(val1.exists(_.get(infoStr).contains("info 1")))
        assert(res2)
        assert(val2.exists(_.get(eventId).contains(2)))
        assert(val2.exists(_.get(infoStr).contains("info 2")))
        assert(res3 == 2)
        assert(res4.isEmpty)
        assert(res5.isEmpty)
        assert(res6 == 0)
        assert(res7)
        assert(res8.contains("1"))
        assert(res9.contains("info 1"))
      }
    Await.result(f, 5.seconds)
  }

  test("Test lset, lget and getHistory") {
    val event = TelemetryEvent(source = "testSource", "testPrefix").set(exposureTime)(2)
    val key = "test"
    //    val testKey = "testKey"
    val n = 3

    val f = for {
      _ ← kvs.lset(key, event.set(exposureTime)(3), n)
      _ ← kvs.lset(key, event.set(exposureTime)(4), n)
      _ ← kvs.lset(key, event.set(exposureTime)(5), n)
      _ ← kvs.lset(key, event.set(exposureTime)(6), n)
      _ ← kvs.lset(key, event.set(exposureTime)(7), n)
      v ← kvs.lget(key)
      h ← kvs.getHistory(key, n + 1)
      _ ← kvs.delete(key)
    } yield {
        assert(v.isDefined)
        assert(v.get.get(exposureTime).get == 7.0)
        assert(h.size == n + 1)
        for (i ← 0 to n) {
          logger.info(s"History: $i: ${h(i)}")
          //        assert(h(i).asInstanceOf[TelemetryEvent](testKey).elems.head == s"test${n + 2 - i}")
        }
      }
    Await.result(f, 5.seconds)
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}
