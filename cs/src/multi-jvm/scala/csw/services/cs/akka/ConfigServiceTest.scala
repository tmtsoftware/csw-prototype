package csw.services.cs.akka

import java.io.{File, FileNotFoundException, IOException}

import akka.actor._
import akka.remote.testkit._
import akka.testkit.ImplicitSender
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer
import csw.services.cs.akka.ConfigServiceActor._
import csw.services.cs.core.ConfigData
import csw.services.ls.LocationServiceActor

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A test that runs the config service, annex http server, location service
 * and a client to test the config service, each in a separate JVM (See the sbt-multi-jvm plugin).
 * See http://doc.akka.io/docs/akka/current/dev/multi-node-testing.html#multi-node-testing.
 */
object TestConfig extends MultiNodeConfig {
  val configServiceAnnex = role("configServiceAnnex")

  val configService = role("configService")

  val configServiceClient = role("configServiceClient")

  val locationService = role("locationService")

  // We need to configure the location service to run on a known port
  nodeConfig(locationService)(ConfigFactory.load("testLocationService.conf"))
}

class TestMultiJvmConfigServiceAnnex extends TestSpec

class TestMultiJvmConfigService extends TestSpec

class TestMultiJvmConfigServiceClient extends TestSpec

class TestMultiJvmLocationService extends TestSpec


class TestSpec extends MultiNodeSpec(TestConfig) with STMultiNodeSpec with ImplicitSender {

  import csw.services.cs.akka.TestConfig._

  override def initialParticipants: Int = roles.size

  "The test" must {

    "wait for all nodes to enter a barrier" in {
      enterBarrier("startup")
    }

    "be able to start the config service, annex, and client to manage files" in {
      runOn(configServiceAnnex) {
        enterBarrier("locationServiceStarted")
        ConfigServiceAnnexServer()
        enterBarrier("deployed")
        enterBarrier("done")
      }

      runOn(configService) {
        enterBarrier("locationServiceStarted")
        val manager = TestRepo.getConfigManager(ConfigServiceSettings(system))
        val configServiceActor = system.actorOf(ConfigServiceActor.props(manager), name = "configService")
        configServiceActor ! RegisterWithLocationService
        Thread.sleep(1000) // XXX FIXME: need reply from location service?
        enterBarrier("deployed")
        enterBarrier("done")
      }

      runOn(configServiceClient) {
        enterBarrier("locationServiceStarted")
        enterBarrier("deployed")
        val cs = Await.result(ConfigServiceActor.locateConfigService(), 5.seconds)
        println(s"Got a config service: $cs")
        runTests(cs, oversize = true)
        enterBarrier("done")
      }

      runOn(locationService) {
        system.actorOf(Props[LocationServiceActor], LocationServiceActor.locationServiceName)
        enterBarrier("locationServiceStarted")
        enterBarrier("deployed")
        enterBarrier("done")
      }

      enterBarrier("finished")
    }
  }

  val path1 = new File("some/test1/TestConfig1")
  val path2 = new File("some/test2/TestConfig2")

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  def runTests(configServiceActor: ActorRef, oversize: Boolean = false): Unit = {
    import system.dispatcher
    implicit val timeout: Timeout = 30.seconds

    val csClient = ConfigServiceClient(configServiceActor)

    // Sequential, non-blocking for-comprehension
    val result = for {
    // Try to update a file that does not exist (should fail)
      updateIdNull ← csClient.update(path1, ConfigData(contents2), comment2) recover {
        case e: FileNotFoundException ⇒ null
      }

      // Add, then update the file twice
      createId1 ← csClient.create(path1, ConfigData(contents1), oversize, comment1)
      createId2 ← csClient.create(path2, ConfigData(contents1), oversize, comment1)
      updateId1 ← csClient.update(path1, ConfigData(contents2), comment2)
      updateId2 ← csClient.update(path1, ConfigData(contents3), comment3)

      // Check that we can access each version
      result1 ← csClient.get(path1).flatMap(_.get.toFutureString)
      result2 ← csClient.get(path1, Some(createId1)).flatMap(_.get.toFutureString)
      result3 ← csClient.get(path1, Some(updateId1)).flatMap(_.get.toFutureString)
      result4 ← csClient.get(path1, Some(updateId2)).flatMap(_.get.toFutureString)
      result5 ← csClient.get(path2).flatMap(_.get.toFutureString)
      result6 ← csClient.get(path2, Some(createId2)).flatMap(_.get.toFutureString)

      // test history()
      historyList1 ← csClient.history(path1)
      historyList2 ← csClient.history(path2)

      // test list()
      list ← csClient.list()

      // Should throw exception if we try to create a file that already exists
      createIdNull ← csClient.create(path1, ConfigData(contents2), oversize, comment2) recover {
        case e: IOException ⇒ null
      }
    } yield {
      // At this point all of the above Futures have completed,so we can do some tests
      assert(updateIdNull == null)
      assert(result1 == contents3)
      assert(result2 == contents1)
      assert(result3 == contents2)
      assert(result4 == contents3)
      assert(result5 == contents1)
      assert(result6 == contents1)
      assert(createIdNull == null)

      assert(historyList1.size == 3)
      assert(historyList2.size == 1)
      assert(historyList1(0).comment == comment3)
      assert(historyList2(0).comment == comment1)
      assert(historyList1(1).comment == comment2)
      assert(historyList1(2).comment == comment1)

      assert(list.size == 2 + 1) // +1 for README file added when creating the bare rep
      for (info ← list) {
        info.path match {
          case this.path1 ⇒ assert(info.comment == comment3)
          case this.path2 ⇒ assert(info.comment == comment1)
          case x ⇒ if (x.getName != "README") sys.error("Test failed for " + info)
        }
      }

      println("\nAll config service client tests passed\n(Pay no attention to those annoying dead letter warnings...)\n")
    }

    Await.result(result, 30.seconds)

  }
}
