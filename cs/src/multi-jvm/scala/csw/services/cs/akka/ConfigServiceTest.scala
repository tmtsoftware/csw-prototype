package csw.services.cs.akka

import akka.actor._
import akka.remote.testkit._
import akka.testkit.ImplicitSender
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer
import csw.services.cs.akka.ConfigServiceActor._
import csw.services.cs.core.ConfigManagerTestHelper
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
        val cs = Await.result(ConfigServiceActor.locateConfigService(ConfigServiceSettings(system).name), 5.seconds)
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

  def runTests(configServiceActor: ActorRef, oversize: Boolean = false): Unit = {
    implicit val timeout: Timeout = 30.seconds

    val csClient = ConfigServiceClient(configServiceActor)

    val result = ConfigManagerTestHelper.runTests(csClient, oversize)
    Await.result(result, 30.seconds)
  }
}
