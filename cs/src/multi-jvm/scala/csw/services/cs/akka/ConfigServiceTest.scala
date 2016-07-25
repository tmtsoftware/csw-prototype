package csw.services.cs.akka

import akka.actor._
import akka.remote.testkit._
import akka.testkit.ImplicitSender
import akka.util.Timeout
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer
import csw.services.cs.akka.ConfigServiceActor._
import csw.services.cs.core.ConfigManagerTestHelper
import csw.services.loc.LocationService

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A test that runs the config service, annex http server, location service
 * and a client to test the config service, each in a separate JVM (See the sbt-multi-jvm plugin).
 * See http://doc.akka.io/docs/akka/current/dev/multi-node-testing.html#multi-node-testing.
 */
object TestConfig extends MultiNodeConfig {
  LocationService.initInterface()

  val configServiceAnnex = role("configServiceAnnex")

  val configService = role("configService")

  val configServiceClient = role("configServiceClient")

  // Note: The "multinode.host" system property needs to be set to empty so that the MultiNodeSpec
  // base class below will use the actual host name.
  // (By default it ends up with "localhost", which breaks the test, since the LocationService
  // registers the config service with the actual host name.)
  System.setProperty("multinode.host", "")
}

class TestMultiJvmConfigServiceAnnex extends TestSpec

class TestMultiJvmConfigService extends TestSpec

class TestMultiJvmConfigServiceClient extends TestSpec

class TestSpec extends MultiNodeSpec(TestConfig) with STMultiNodeSpec with ImplicitSender {

  import csw.services.cs.akka.TestConfig._

  override def initialParticipants: Int = roles.size

  "The test" must {

    "wait for all nodes to enter a barrier" in {
      enterBarrier("startup")
    }

    "be able to start the config service, annex, and client to manage files" in {
      runOn(configServiceAnnex) {
        val server = ConfigServiceAnnexServer()
        enterBarrier("deployed")
        enterBarrier("done")
        server.shutdown()
      }

      runOn(configService) {
        val manager = TestRepo.getTestRepoConfigManager()
        val configServiceActor = system.actorOf(ConfigServiceActor.props(manager, registerWithLocationService = true), name = "configService")
        enterBarrier("deployed")
        enterBarrier("done")
      }

      runOn(configServiceClient) {
        enterBarrier("deployed")
        val cs = Await.result(ConfigServiceActor.locateConfigService(ConfigServiceSettings(system).name), 20.seconds)
        println(s"Got a config service: $cs")
        runTests(cs, oversize = true)
        enterBarrier("done")
      }

      enterBarrier("finished")
    }
  }

  def runTests(configServiceActor: ActorRef, oversize: Boolean = false): Unit = {
    implicit val timeout: Timeout = 30.seconds

    val csClient = ConfigServiceClient(configServiceActor)

    ConfigManagerTestHelper.runTests(csClient, oversize)
  }
}
