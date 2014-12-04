package csw.services.pkg

import akka.actor._
import akka.remote.testkit._
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import csw.services.cmd.akka.CommandServiceActor.{CommandServiceStatus, StatusRequest, Submit}
import csw.services.cmd.akka.CommandStatus
import csw.services.ls.LocationService.RegInfo
import csw.services.ls.LocationServiceActor
import csw.services.ls.LocationServiceActor.{ServiceId, ServiceType}
import csw.util.cfg.TestConfig

import scala.concurrent.duration._

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

  import csw.services.pkg.ContainerConfig._

  override def initialParticipants: Int = roles.size

  "A container" must {

    "wait for all nodes to enter a barrier" in {
      enterBarrier("startup")
    }

    "be able to create a local Assembly and add two remote Hcds" in {
      runOn(container1) {
        enterBarrier("locationServiceStarted")
        enterBarrier("deployed")
        val config = TestConfig.testConfig
        val container = Container.create("Container-1")
        val assembly1Props = TestAssembly.props("Assembly-1")
        within(10.seconds) {
          val serviceId = ServiceId("Assembly-1", ServiceType.Assembly)
          val regInfo = RegInfo(serviceId)
          container ! Container.CreateComponent(assembly1Props, regInfo)
          val started = expectMsgType[LifecycleManager.Started]
          assert(started.name == "Assembly-1")
          val assembly1 = started.actorRef
          waitForReady(assembly1)
          assembly1 ! Submit(config)
          val s1 = expectMsgType[CommandStatus.Queued]
          val s2 = expectMsgType[CommandStatus.Busy]
          val s3a = expectMsgType[CommandStatus.PartiallyCompleted]
          val s3 = expectMsgType[CommandStatus.Completed]
          assert(s1.runId == s2.runId)
          assert(s3.runId == s2.runId)
          assert(s3a.runId == s3.runId)

          println("\nContainer1 tests passed\n")

          assembly1 ! LifecycleManager.Stop
          val stopped = expectMsgType[LifecycleManager.Stopped]
          assert(stopped.name == "Assembly-1")

          assembly1 ! LifecycleManager.Start
          val started2 = expectMsgType[LifecycleManager.Started]
          assert(started2.name == "Assembly-1")

//          container ! Container.DeleteComponent("Assembly-1")

          enterBarrier("done")
        }
      }

      runOn(container2) {
        enterBarrier("locationServiceStarted")
        val container = Container.create("Container-2")

        for((name, configPath) <- List(("HCD-2A", "tmt.tel.base.pos"), ("HCD-2B", "tmt.tel.ao.pos.one"))) {
          val serviceId = ServiceId(name, ServiceType.HCD)
          val regInfo = RegInfo(serviceId, Some(configPath))
          val props = TestHcd.props(name, configPath)
          container ! Container.CreateComponent(props, regInfo)
          val started = expectMsgType[LifecycleManager.Started]
          assert(started.name == name)
        }

        println("\nContainer2 tests passed\n")

        enterBarrier("deployed")
        enterBarrier("done")

//        container ! Container.DeleteComponent("HCD-2A")
//        container ! Container.DeleteComponent("HCD-2B")
//        container ! PoisonPill
      }

      runOn(locationService) {
        val ls = system.actorOf(Props[LocationServiceActor], LocationServiceActor.locationServiceName)
        enterBarrier("locationServiceStarted")
        enterBarrier("deployed")
        enterBarrier("done")
//        ls ! PoisonPill
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
