package csw.services.kvs

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.util.config.Configurations.SetupConfig
import csw.util.config._
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.Await
import scala.concurrent.duration._

object KeyValueStoreTests {

  // Define keys for testing
  val infoValue = IntKey("infoValue")

  val infoStr = StringKey("infoStr")

  val boolValue = BooleanKey("boolValue")

  val exposureTime = DoubleKey("exposureTime")
}

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
//@DoNotDiscover
class KeyValueStoreTests
    extends TestKit(ActorSystem("Test"))
    with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll with Implicits {

  import KeyValueStoreTests._
  implicit val execContext = system.dispatcher

  val settings = KvsSettings(system)
  val kvs = KeyValueStore[SetupConfig](settings)

  test("Test Set and Get") {
    val prefix1 = "tcs.test1"
    val config1 = SetupConfig(prefix1)
      .add(infoValue.set(1))
      .add(infoStr.set("info 1"))

    val prefix2 = "tcs.test2"
    val config2 = SetupConfig(prefix2)
      .add(infoValue.set(2))
      .add(infoStr.set("info 2"))

    val f = for {
      res1 <- kvs.set(prefix1, config1)
      val1 <- kvs.get(prefix1)
      res2 <- kvs.set(prefix2, config2)
      val2 <- kvs.get(prefix2)
      res3 <- kvs.delete(prefix1, prefix2)
      res4 <- kvs.get(prefix1)
      res5 <- kvs.get(prefix2)
      res6 <- kvs.delete(prefix1, prefix2)
      res7 <- kvs.hmset("tcs.testx", config1.getStringMap)
      res8 <- kvs.hmget("tcs.testx", infoValue.keyName)
      res9 <- kvs.hmget("tcs.testx", infoStr.keyName)
    } yield {
      assert(val1.exists(_.prefix == prefix1))
      assert(val1.exists(_(infoValue).head == 1))
      assert(val1.exists(_(infoStr).head == "info 1"))
      assert(val2.exists(_(infoValue).head == 2))
      assert(val2.exists(_(infoStr).head == "info 2"))
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

  test("Test set, get and getHistory") {
    val prefix = "tcs.test2"
    val config = SetupConfig(prefix).add(exposureTime.set(2.0))
    //    val testKey = "testKey"
    val n = 3

    val f = for {
      _ <- kvs.set(prefix, config.add(exposureTime.set(3.0)), n)
      _ <- kvs.set(prefix, config.add(exposureTime.set(4.0)), n)
      _ <- kvs.set(prefix, config.add(exposureTime.set(5.0)), n)
      _ <- kvs.set(prefix, config.add(exposureTime.set(6.0)), n)
      _ <- kvs.set(prefix, config.add(exposureTime.set(7.0)), n)
      v <- kvs.get(prefix)
      h <- kvs.getHistory(prefix, n + 1)
      _ <- kvs.delete(prefix)
    } yield {
      assert(v.isDefined)
      assert(v.get(exposureTime).head == 7.0)
      assert(h.size == n + 1)
      for (i <- 0 to n) {
        logger.info(s"History: $i: ${h(i)}")
      }
    }
    Await.result(f, 5.seconds)
  }

  test("Test future usage") {
    val prefix = "tcs.test3"
    val config = SetupConfig(prefix)
      .add(infoValue.set(2))
      .add(infoStr.set("info 2"))
      .add(boolValue.set(true))

    kvs.set(prefix, config).onSuccess {
      case _ =>
        kvs.get(prefix).onSuccess {
          case Some(setupConfig) =>
            assert(setupConfig(infoValue).head == 2)
            assert(setupConfig(infoStr).head == "info 2")
            assert(setupConfig(boolValue).head)
            setupConfig
        }
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
  }
}
