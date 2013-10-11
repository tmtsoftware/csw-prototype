package org.tmt.csw.pkg

import akka.testkit.ImplicitSender
import akka.actor._
import akka.pattern.ask
import org.tmt.csw.cmd.akka.{CommandStatus, CommandServiceMessage, ConfigActor}
import org.tmt.csw.cmd.core.Configuration
import akka.util.Timeout
import scala.concurrent.duration._
import akka.remote.testkit.MultiNodeSpec

/**
 * A test that runs each of the classes below in a separate JVM (See the sbt-multi-jvm plugin)
 */
object ContainerTest {
  val testConfig =
    """
      |      config {
      |        info {
      |          configId = 1000233
      |          obsId = TMT-2021A-C-2-1
      |        }
      |        tmt.tel.base.pos {
      |          posName = NGC738B
      |          c1 = "22:35:58.530"
      |          c2 = "33:57:55.40"
      |          equinox = J2000
      |        }
      |        tmt.tel.ao.pos.one {
      |          c1 = "22:356:01.066"
      |          c2 = "33:58:21.69"
      |          equinox = J2000
      |        }
      |      }
      |
    """.stripMargin
}


class TestMultiJvmContainer1 extends ContainerSpec

class TestMultiJvmContainer2 extends ContainerSpec

class ContainerSpec extends MultiNodeSpec(ContainerConfig) with STMultiNodeSpec with ImplicitSender {

  import ContainerConfig._

  val duration: FiniteDuration = 5.seconds
  implicit val timeout = Timeout(duration)

  override def initialParticipants: Int = roles.size

  "A container" must {

    "wait for all nodes to enter a barrier" in {
      enterBarrier("startup")
    }

    "be able to create a local Assembly and add two remote Hcds" in {
      runOn(container1) {
        enterBarrier("deployed")
        val config = Configuration(ContainerTest.testConfig)
        implicit val dispatcher = system.dispatcher

        val container = Container.create("Container-1")

        val assembly1Props = TestAssembly.props("Assembly-1")

        val hcd2aPath = ActorPath.fromString(TestSettings(system).hcd2a)
        val hcd2bPath = ActorPath.fromString(TestSettings(system).hcd2b)

        for {
          assembly1 <- (container ? Container.CreateComponent(assembly1Props, "Assembly-1")).mapTo[ActorRef]
          ack2a <- assembly1 ? Assembly.AddComponentByPath(hcd2aPath)
          ack2b <- assembly1 ? Assembly.AddComponentByPath(hcd2bPath)
        } {
          assembly1 ! CommandServiceMessage.Submit(config)
          val s1 = expectMsgType[CommandStatus.Queued]
          val s2 = expectMsgType[CommandStatus.Busy]
          val s3 = expectMsgType[CommandStatus.Complete]
          assert(s1.runId == s2.runId)
          assert(s3.runId == s2.runId)
          log.info(s"Command status: $s3")
        }
      }

      runOn(container2) {
        val container = Container.create("Container-2")

        val hcd2aProps = TestHcd.props("HCD-2A", Set("config.tmt.tel.base.pos"))
        val hcd2bProps = TestHcd.props("HCD-2B", Set("config.tmt.tel.ao.pos.one"))

        container ! Container.CreateComponent(hcd2aProps, "HCD-2A")
        val hcd2a = expectMsgType[ActorRef]
        container ! Container.CreateComponent(hcd2bProps, "HCD-2B")
        val hcd2b = expectMsgType[ActorRef]
        enterBarrier("deployed")
        Thread.sleep(3000) // XXX how to stop from exiting too soon here?
      }

      enterBarrier("finished")
    }
  }
}
