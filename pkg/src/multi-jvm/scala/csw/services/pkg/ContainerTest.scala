package csw.services.pkg

import akka.actor._
import akka.remote.testkit._
import akka.testkit.ImplicitSender
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.services.ccs.{AssemblyClient, BlockingAssemblyClient, CommandStatus}
import csw.services.ccs.AssemblyController.Submit
import csw.services.loc.ComponentType.HCD
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.{ComponentId, Connection, LocationService}
import csw.services.pkg.ContainerComponent.Stop

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A test that runs two containers, each in a separate JVM (See the sbt-multi-jvm plugin).
 * See http://doc.akka.io/docs/akka/current/dev/multi-node-testing.html#multi-node-testing.
 */
object ContainerConfig extends MultiNodeConfig {
  LocationService.initInterface()

  val container1 = role("container1")

  val container2 = role("container2")

  // Note: The "multinode.host" system property needs to be set to empty so that the MultiNodeSpec
  // base class below will use the actual host name.
  // (By default it ends up with "localhost", which breaks the test, since the LocationService
  // registers the config service with the actual host name.)
  System.setProperty("multinode.host", "")
}


class TestMultiJvmContainer1 extends ContainerSpec

class TestMultiJvmContainer2 extends ContainerSpec

class ContainerSpec extends MultiNodeSpec(ContainerConfig) with STMultiNodeSpec with ImplicitSender {

  import ContainerConfig._

  override def initialParticipants: Int = roles.size

  "A container" must {

    "wait for all nodes to enter a barrier" in {
      enterBarrier("startup")
    }

    "be able to create a local Assembly and add two remote Hcds" in {
      runOn(container1) {
        enterBarrier("deployed")
        val container = ContainerComponent.create(ConfigFactory.load("container1.conf")).get
        within(30.seconds) {
          container ! ContainerComponent.GetComponents
          val components = expectMsgType[ContainerComponent.Components].components
          assert(components.size == 1)
          for(comp <- components) {
            val assembly1 = comp.supervisor
            // Make sure the HCDs are ready before sending the test config
            val connections: Set[Connection] = Set(
              AkkaConnection(ComponentId("HCD-2A", HCD)),
              AkkaConnection(ComponentId("HCD-2B", HCD))
            )
            implicit val timeout: Timeout = 60.seconds
            Await.result(LocationService.resolve(connections), timeout.duration)

            // Use actor API
            assembly1 ! Submit(TestConfig.testConfigArg)
            expectMsgType[CommandStatus.Accepted]
            expectMsgType[CommandStatus.Completed]

            // Use client wrapper
            val client = AssemblyClient(assembly1)
            assert(Await.result(client.submit(TestConfig.testConfigArg), timeout.duration).isSuccess)

            // Test dummy request and blocking client
            val blockingClient = BlockingAssemblyClient(client)
            val result = blockingClient.request(TestConfig.testConfig1)
            assert(result.status.isSuccess)
            assert(result.resp.get == TestConfig.testConfig2)
          }
        }
        println("\nContainer1 tests passed\n")
        container ! Stop
        enterBarrier("done")
      }

      runOn(container2) {
        val container = ContainerComponent.create(ConfigFactory.load("container2.conf")).get
        container ! ContainerComponent.GetComponents
        val components = expectMsgType[ContainerComponent.Components].components
        assert(components.size == 2)
        println("\nContainer2 tests passed\n")
        enterBarrier("deployed")
        enterBarrier("done")
      }

      enterBarrier("finished")
    }
  }
}
