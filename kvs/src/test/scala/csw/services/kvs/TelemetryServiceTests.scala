package csw.services.kvs

import akka.testkit.{ ImplicitSender, TestKit }
import akka.actor.ActorSystem
import csw.util.cfg.Configurations.SetupConfig
import csw.util.cfg.Key
import csw.util.cfg.StandardKeys._
import org.scalatest.{ DoNotDiscover, FunSuiteLike }
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.duration._

object TelemetryServiceTests {

  // Define keys for testing
  val infoValue = Key.create[Int]("infoValue")

  val infoStr = Key.create[String]("infoStr")

}

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
@DoNotDiscover
class TelemetryServiceTests
    extends TestKit(ActorSystem("Test"))
    with ImplicitSender with FunSuiteLike with LazyLogging with Implicits {

  import TelemetryServiceTests._

  val kvs = TelemetryService(5.seconds)

  test("Test Set and Get") {
    val config1 = SetupConfig("tcs.test")
      .set(infoValue, 1)
      .set(infoStr, "info 1")

    val config2 = SetupConfig("tcs.test")
      .set(infoValue, 2)
      .set(infoStr, "info 2")

    kvs.set("test1", config1)

    val val1 = kvs.get("test1").get
    assert(val1.prefix == "tcs.test")
    assert(val1.get(infoValue).contains(1))
    assert(val1.get(infoStr).contains("info 1"))

    kvs.set("test2", config2)

    val val2 = kvs.get("test2")
    assert(val2.exists(_.get(infoValue).contains(2)))
    assert(val2.exists(_.get(infoStr).contains("info 2")))

    assert(kvs.delete("test1", "test2") == 2)

    assert(kvs.get("test1").isEmpty)
    assert(kvs.get("test2").isEmpty)

    assert(kvs.delete("test1", "test2") == 0)

    assert(kvs.hmset("testx", config1.getStringMap))
    assert(kvs.hmget("testx", infoValue.name).contains("1"))
    assert(kvs.hmget("testx", infoStr.name).contains("info 1"))
  }

  test("Test lset, lget and getHistory") {
    val config = SetupConfig("tcs.testPrefix").set(exposureTime, 2)
    val key = "test"
    val n = 3

    kvs.set(key, config.set(exposureTime, 3), n)
    kvs.set(key, config.set(exposureTime, 4), n)
    kvs.set(key, config.set(exposureTime, 5), n)
    kvs.set(key, config.set(exposureTime, 6), n)
    kvs.set(key, config.set(exposureTime, 7), n)
    val v = kvs.get(key)
    val h = kvs.getHistory(key, n + 1)
    kvs.delete(key)

    assert(v.isDefined)
    assert(v.get.get(exposureTime).get == 7.0)
    assert(h.size == n + 1)

    for (i ← 0 to n) {
      logger.info(s"History: $i: ${h(i)}")
    }
  }
}

