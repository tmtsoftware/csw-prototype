package org.tmt.csw.kvs

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import org.scalatest.{DoNotDiscover, BeforeAndAfterAll, FunSuiteLike}
import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.Await
import scala.concurrent.duration._
import org.tmt.csw.util.Configuration

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
@DoNotDiscover
class TestRedisKeyValueStore
  extends TestKit(ActorSystem("Test"))
  with ImplicitSender with FunSuiteLike with Logging with BeforeAndAfterAll {

  implicit val execContext = system.dispatcher
  val kvs: KeyValueStore = RedisKeyValueStore()

  test("Test Set and Get") {
    val event1 = Configuration().withValue("test.eventId", 1)
    val event2 = Configuration().withValue("test.eventId", 2)

    val f = for {
      res1 <- kvs.set("test1", event1)
      val1 <- kvs.get("test1")
      res2 <- kvs.set("test2", event2)
      val2 <- kvs.get("test2")
      res3 <- kvs.delete("test1", "test2")
      res4 <- kvs.get("test1")
      res5 <- kvs.get("test2")
      res6 <- kvs.delete("test1", "test2")
    } yield {
      assert(res1)
      assert(val1 == Some(event1))
      assert(res2)
      assert(val2 == Some(event2))
      assert(res3 == 2)
      assert(res4.isEmpty)
      assert(res5.isEmpty)
      assert(res6 == 0)
    }
    Await.result(f, 5.seconds)
  }

  test("Test lset, lget and getHistory") {
    val event = Configuration()
    val key = "test"
    val idKey = "test.eventId"
    val n = 3

    val f = for {
      _ <- kvs.lset(key, event.withValue(idKey, "test1"), n)
      _ <- kvs.lset(key, event.withValue(idKey, "test2"), n)
      _ <- kvs.lset(key, event.withValue(idKey, "test3"), n)
      _ <- kvs.lset(key, event.withValue(idKey, "test4"), n)
      _ <- kvs.lset(key, event.withValue(idKey, "test5"), n)
      v <- kvs.lget(key)
      h <- kvs.getHistory(key, n+1)
      _ <- kvs.delete(key)
    } yield {
      assert(v.isDefined)
      assert(v.get.getString(idKey) == "test5")
      assert(h.size == n+1)
      for(i <- 0 to n) {
        logger.info(s"History: $i: ${h(i)}")
        assert(h(i).getString(idKey) == s"test${n+2-i}")
      }
    }
    Await.result(f, 5.seconds)
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}
