package org.tmt.csw.pkg

import akka.testkit.ImplicitSender
import akka.actor._
import org.tmt.csw.cmd.akka.CommandStatus
import org.tmt.csw.util.TestConfig
import akka.util.Timeout
import scala.concurrent.duration._
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import org.tmt.csw.cmd.akka.CommandServiceActor.{CommandServiceStatus, StatusRequest, Submit}
import com.typesafe.config.ConfigFactory
import org.tmt.csw.ls.LocationServiceActor
import org.tmt.csw.util.Configuration

/**
 * A test that runs each of the classes below and the location service
 * in a separate JVM (See the sbt-multi-jvm plugin).
 * See http://doc.akka.io/docs/akka/current/dev/multi-node-testing.html#multi-node-testing.
 */
object ContainerConfig extends MultiNodeConfig {
  val container1 = role("container1")

  val container2 = role("container2")

  val locationService = role("locationService")

  // We need to configure the location service to run on a known port
  nodeConfig(locationService)(ConfigFactory.load("testLocationService.conf"))
}

class TestMultiJvmContainer1 extends ContainerSpec
class TestMultiJvmContainer2 extends ContainerSpec
class TestMultiJvmLocationService extends ContainerSpec


class ContainerSpec extends MultiNodeSpec(ContainerConfig) with STMultiNodeSpec with ImplicitSender {

  import ContainerConfig._

  override def initialParticipants: Int = roles.size

  "A container" must {

    "wait for all nodes to enter a barrier" in {
      enterBarrier("startup")
    }

    "be able to create a local Assembly and add two remote Hcds" in {
      runOn(container1) {
        enterBarrier("locationServiceStarted")
        enterBarrier("deployed")
        val config = Configuration(TestConfig.testConfig)
        val container = Container.create("Container-1")
        val assembly1Props = TestAssembly.props("Assembly-1")
        within(10 seconds) {
          container ! Container.CreateComponent(assembly1Props, "Assembly-1")
          val assembly1 = expectMsgType[ActorRef]
          waitForReady(assembly1)
          assembly1 ! Submit(config)
          val s1 = expectMsgType[CommandStatus.Queued]
          val s2 = expectMsgType[CommandStatus.Busy]
          val s3a = expectMsgType[CommandStatus.PartiallyCompleted]
          val s3 = expectMsgType[CommandStatus.Completed]
          assert(s1.runId == s2.runId)
          assert(s3.runId == s2.runId)
          assert(s3a.runId == s3.runId)
          enterBarrier("done")
        }
      }

      runOn(container2) {
        enterBarrier("locationServiceStarted")
        val container = Container.create("Container-2")
        val hcd2aProps = TestHcd.props("HCD-2A", "config.tmt.tel.base.pos")
        val hcd2bProps = TestHcd.props("HCD-2B", "config.tmt.tel.ao.pos.one")
        container ! Container.CreateComponent(hcd2aProps, "HCD-2A")
        expectMsgType[ActorRef]
        container ! Container.CreateComponent(hcd2bProps, "HCD-2B")
        expectMsgType[ActorRef]
        enterBarrier("deployed")
        enterBarrier("done")
      }

      runOn(locationService) {
        val ls = system.actorOf(Props[LocationServiceActor], LocationServiceActor.locationServiceName)
        enterBarrier("locationServiceStarted")
        enterBarrier("deployed")
        enterBarrier("done")
      }

      enterBarrier("finished")
    }
  }

  // Wait for the command service to be ready before returning (should only be necessary when testing)
  def waitForReady(commandServiceActor: ActorRef): ActorRef = {
    commandServiceActor ! StatusRequest
    val status = expectMsgType[CommandServiceStatus]
    if (status.ready) {
      commandServiceActor
    } else {
      Thread.sleep(200)
      waitForReady(commandServiceActor)
    }
  }
}
