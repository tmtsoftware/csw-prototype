package org.tmt.csw.kvs

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}
import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.Await
import scala.concurrent.duration._

class TestRedisKeyValueStore
  extends TestKit(ActorSystem("TestRedisKeyValueStore"))
  with ImplicitSender with FunSuiteLike with Logging with BeforeAndAfterAll {

  val kvs: KeyValueStore = RedisKeyValueStore()

  test("Test Set and Get") {
    implicit val execContext = system.dispatcher
    val f = for {
      res1 <- kvs.set("test1", "value1")
      val1 <- kvs.get("test1")
      res2 <- kvs.set("test2", "value2")
      val2 <- kvs.get("test2")
    } yield (res1, val1, res2, val2)

    val (res1, val1, res2, val2) = Await.result(f, 2.seconds)
    assert(res1)
    assert(val1 == Some("value1"))
    assert(res2)
    assert(val2 == Some("value2"))
    logger.info("All tests passed")
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}
