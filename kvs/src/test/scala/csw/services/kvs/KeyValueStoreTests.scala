package csw.services.kvs

import akka.testkit.{ ImplicitSender, TestKit }
import akka.actor.ActorSystem
import csw.util.cfg.Configurations.SetupConfig
import csw.util.cfg.Key
import csw.util.cfg.StandardKeys._
import org.scalatest.{ DoNotDiscover, BeforeAndAfterAll, FunSuiteLike }
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.Await
import scala.concurrent.duration._

object KeyValueStoreTests {

  // Define keys for testing
  val infoValue = Key.create[Int]("infoValue")

  val infoStr = Key.create[String]("infoStr")

  val boolValue = Key.create[Boolean]("boolValue")
}

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
@DoNotDiscover
class KeyValueStoreTests
    extends TestKit(ActorSystem("Test"))
    with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll with Implicits {

  import KeyValueStoreTests._

  implicit val execContext = system.dispatcher
  val kvs = KeyValueStore[SetupConfig]

  test("Test Set and Get") {
    val prefix1 = "tcs.test1"
    val config1 = SetupConfig(prefix1)
      .set(infoValue, 1)
      .set(infoStr, "info 1")

    val prefix2 = "tcs.test2"
    val config2 = SetupConfig(prefix2)
      .set(infoValue, 2)
      .set(infoStr, "info 2")

    val f = for {
      res1 ← kvs.set(prefix1, config1)
      val1 ← kvs.get(prefix1)
      res2 ← kvs.set(prefix2, config2)
      val2 ← kvs.get(prefix2)
      res3 ← kvs.delete(prefix1, prefix2)
      res4 ← kvs.get(prefix1)
      res5 ← kvs.get(prefix2)
      res6 ← kvs.delete(prefix1, prefix2)
      res7 ← kvs.hmset("tcs.testx", config1.getStringMap)
      res8 ← kvs.hmget("tcs.testx", infoValue.name)
      res9 ← kvs.hmget("tcs.testx", infoStr.name)
    } yield {
      assert(val1.exists(_.prefix == prefix1))
      assert(val1.exists(_.get(infoValue).contains(1)))
      assert(val1.exists(_.get(infoStr).contains("info 1")))
      assert(val2.exists(_.get(infoValue).contains(2)))
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

  test("Test set, get and getHistory") {
    val prefix = "tcs.test2"
    val config = SetupConfig(prefix).set(exposureTime, 2)
    //    val testKey = "testKey"
    val n = 3

    val f = for {
      _ ← kvs.set(prefix, config.set(exposureTime, 3), n)
      _ ← kvs.set(prefix, config.set(exposureTime, 4), n)
      _ ← kvs.set(prefix, config.set(exposureTime, 5), n)
      _ ← kvs.set(prefix, config.set(exposureTime, 6), n)
      _ ← kvs.set(prefix, config.set(exposureTime, 7), n)
      v ← kvs.get(prefix)
      h ← kvs.getHistory(prefix, n + 1)
      _ ← kvs.delete(prefix)
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

  test("Test future usage") {
    val prefix = "tcs.test3"
    val config = SetupConfig(prefix)
      .set(infoValue, 2)
      .set(infoStr, "info 2")
      .set(boolValue, true)

    kvs.set(prefix, config).onSuccess {
      case _ ⇒
        kvs.get(prefix).onSuccess {
          case Some(setupConfig) ⇒
            assert(setupConfig.get(infoValue).get == 2)
            assert(setupConfig.get(infoStr).get == "info 2")
            assert(setupConfig.get(boolValue).get)
            setupConfig
        }
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
  }
}
