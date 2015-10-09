package csw.services.pkg

import akka.actor._
import akka.remote.testkit._
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import csw.services.pkg.LifecycleManager.LifecycleStateChanged
import csw.shared.cmd_old.CommandStatus
import csw.util.config.TestConfig

import scala.concurrent.duration._

/**
 * A test that runs two containers, each in a separate JVM (See the sbt-multi-jvm plugin).
 * See http://doc.akka.io/docs/akka/current/dev/multi-node-testing.html#multi-node-testing.
 */
object ContainerConfig extends MultiNodeConfig {
  val container1 = role("container1")

  val container2 = role("container2")
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
        within(10.seconds) {
          container ! Container.GetComponents
          val map = expectMsgType[Container.Components].map
          assert(map.size == 1)
          for((name, assembly1) <- map) {
            assembly1 ! LifecycleManager.SubscribeToLifecycleStates(onlyRunningAndConnected = true)
            val stateChange = expectMsgType[LifecycleStateChanged]
            assert(stateChange.connected && stateChange.state.isRunning)
            assembly1 ! TestConfig.testConfigArg
            val status = expectMsgType[CommandStatus.Completed]
          }
          println("\nContainer1 tests passed\n")
          enterBarrier("done")
          container ! LifecycleManager.Uninitialize
        }
      }

      runOn(container2) {
        val container = Container.create(ConfigFactory.load("container2.conf"))
        container ! Container.GetComponents
        val componentInfo = expectMsgType[Container.Components]
        for ((name, hcd) <- componentInfo.map) {
          hcd ! LifecycleManager.SubscribeToLifecycleStates(onlyRunningAndConnected = true)
          val stateChange = expectMsgType[LifecycleStateChanged]
          assert(stateChange.connected && stateChange.state.isRunning)
        }

        println("\nContainer2 tests passed\n")

        enterBarrier("deployed")
        enterBarrier("done")
      }

      enterBarrier("finished")
    }
  }
}
