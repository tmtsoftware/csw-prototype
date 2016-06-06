package csw.services.kvs

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import csw.util.config.Configurations.SetupConfig
import org.scalatest.{DoNotDiscover, FunSuiteLike}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.util.config.{DoubleKey, IntKey, StringKey}

import scala.concurrent.duration._

object BlockingKeyValueStoreTests {

  // Define keys for testing
  val infoValue = IntKey("infoValue")

  val infoStr = StringKey("infoStr")

  val exposureTime = DoubleKey("exposureTime")

}

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
//@DoNotDiscover
class BlockingKeyValueStoreTests
    extends TestKit(ActorSystem("Test"))
    with ImplicitSender with FunSuiteLike with LazyLogging with Implicits {

  import BlockingKeyValueStoreTests._

  val settings = KvsSettings(system)
  val kvs = BlockingKeyValueStore[SetupConfig](5.seconds, settings)

  test("Test set and get") {
    val config1 = SetupConfig("tcs.test")
      .set(infoValue, 1)
      .set(infoStr, "info 1")

    val config2 = SetupConfig("tcs.test")
      .set(infoValue, 2)
      .set(infoStr, "info 2")

    kvs.set("test1", config1)

    val val1 = kvs.get("test1").get
    assert(val1.prefix == "tcs.test")
    assert(val1.value(infoValue) == 1)
    assert(val1.value(infoStr) == "info 1")

    kvs.set("test2", config2)

    val val2 = kvs.get("test2")
    assert(val2.exists(_.value(infoValue) == 2))
    assert(val2.exists(_.value(infoStr) == "info 2"))

    assert(kvs.delete("test1", "test2") == 2)

    assert(kvs.get("test1").isEmpty)
    assert(kvs.get("test2").isEmpty)

    assert(kvs.delete("test1", "test2") == 0)
  }

  test("Test set, get and getHistory") {
    val config = SetupConfig("tcs.testPrefix").set(exposureTime, 2.0)
    val key = "test"
    val n = 3

    kvs.set(key, config.set(exposureTime, 3.0), n)
    kvs.set(key, config.set(exposureTime, 4.0), n)
    kvs.set(key, config.set(exposureTime, 5.0), n)
    kvs.set(key, config.set(exposureTime, 6.0), n)
    kvs.set(key, config.set(exposureTime, 7.0), n)

    val v = kvs.get(key)
    assert(v.isDefined)
    assert(v.get.value(exposureTime) == 7.0)

    val h = kvs.getHistory(key, n + 1)
    assert(h.size == n + 1)
    for (i ← 0 to n) {
      logger.info(s"History: $i: ${h(i)}")
    }

    kvs.delete(key)

  }
}

