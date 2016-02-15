package csw.services.pkg

import akka.actor._
import akka.remote.testkit._
import akka.testkit.ImplicitSender
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.services.ccs.{CommandStatus, AssemblyClient}
import csw.services.ccs.AssemblyController.Submit
import csw.services.loc.AccessType.AkkaType
import csw.services.loc.ServiceType.HCD
import csw.services.loc.{ServiceRef, ServiceId, LocationService}
import csw.services.pkg.Supervisor.LifecycleStateChanged

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A test that runs two containers, each in a separate JVM (See the sbt-multi-jvm plugin).
 * See http://doc.akka.io/docs/akka/current/dev/multi-node-testing.html#multi-node-testing.
 */
object ContainerConfig extends MultiNodeConfig {

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
        val container = Container.create(ConfigFactory.load("container1.conf"))
        within(30.seconds) {
          container ! Container.GetComponents
          val map = expectMsgType[Container.Components].map
          assert(map.size == 1)
          for((name, assembly1) <- map) {
            assembly1 ! Supervisor.SubscribeToLifecycleStates(onlyRunning = true)
            val stateChange = expectMsgType[LifecycleStateChanged]
            assert(stateChange.state.isRunning)

            // Make sure the HCDs are ready before sending the test config
            val serviceRefs = Set(
              ServiceRef(ServiceId("HCD-2A", HCD), AkkaType),
              ServiceRef(ServiceId("HCD-2B", HCD), AkkaType)
            )
            implicit val timeout: Timeout = 60.seconds
            Await.result(LocationService.resolve(serviceRefs), timeout.duration)

            // Use actor API
            assembly1 ! Submit(TestConfig.testConfigArg)
            expectMsgType[CommandStatus.Accepted]
            expectMsgType[CommandStatus.Completed]

            // Use client wrapper
            val client = AssemblyClient(assembly1)
            assert(Await.result(client.submit(TestConfig.testConfigArg), timeout.duration).isSuccess)
          }
          println("\nContainer1 tests passed\n")
          enterBarrier("done")
          container ! Supervisor.Uninitialize
        }
      }

      runOn(container2) {
        val container = Container.create(ConfigFactory.load("container2.conf"))
        container ! Container.GetComponents
        val map = expectMsgType[Container.Components].map
        assert(map.size == 2)
        for ((name, hcd) <- map) {
          hcd ! Supervisor.SubscribeToLifecycleStates(onlyRunning = true)
          val stateChange = expectMsgType[LifecycleStateChanged]
          assert(stateChange.state.isRunning)
        }

        println("\nContainer2 tests passed\n")

        enterBarrier("deployed")
        enterBarrier("done")
      }

      enterBarrier("finished")
    }
  }
}
