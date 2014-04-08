package org.tmt.csw.kvs

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}
import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.Await
import scala.concurrent.duration._

class TestRedisKeyValueStore
  extends TestKit(ActorSystem("Test"))
  with ImplicitSender with FunSuiteLike with Logging with BeforeAndAfterAll {

  val kvs: KeyValueStore = RedisKeyValueStore()

  test("Test Set and Get") {
    implicit val execContext = system.dispatcher
    val event1 = Event().withValue("test.eventId", 1)
    val event2 = Event().withValue("test.eventId", 2)

    val f = for {
      res1 <- kvs.set("test1", event1)
      val1 <- kvs.get("test1")
      res2 <- kvs.set("test2", event2)
      val2 <- kvs.get("test2")
      res3 <- kvs.delete("test1", "test2")
      res4 <- kvs.get("test1")
      res5 <- kvs.get("test2")
    } yield (res1, val1, res2, val2, res3, res4, res5)

    val (res1, val1, res2, val2, res3, res4, res5) = Await.result(f, 3.seconds)
    assert(res1)
    assert(val1 == Some(event1))
    assert(res2)
    assert(val2 == Some(event2))
    assert(res3 == 2)
    assert(res4.isEmpty)
    assert(res5.isEmpty)
    logger.info("All tests passed")
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}
