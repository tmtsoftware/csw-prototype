package scala.org.tmt.csw.pkg

import akka.testkit.ImplicitSender
import akka.actor._
import org.tmt.csw.pkg.{RemoteLookup, Assembly, Container}
import akka.pattern.ask
import org.tmt.csw.cmd.akka.{CommandStatus, CommandServiceMessage, ConfigActor}
import org.tmt.csw.cmd.core.Configuration
import akka.util.Timeout
import scala.concurrent.duration._
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import scala.concurrent.Future

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

  val duration: FiniteDuration = 2.seconds
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

        //    val container = Container.create("Container-1")
        val container = system.actorOf(Props[Container], "Container-1")

        log.info(s"XXX container = $container")
        val assembly1Props = TestAssembly.props("Assembly-1")

        val hcd2aPath = node(container2) / "user" / "Container-2" / "HCD-2A"
        val hcd2bPath = node(container2) / "user" / "Container-2" / "HCD-2B"

        log.info(s"XXX In Node 1: hcd2aPath = ${hcd2aPath.toString}")

        for {
          assembly1 <- (container ? Container.CreateComponent(assembly1Props, "Assembly-1")).mapTo[ActorRef]
        } {
          log.info(s"XXX In Node 1: assembly1 = $assembly1")
          assembly1 ! Assembly.AddComponentByPath(hcd2aPath)
          expectMsgType[ConfigActor.Registered]
          assembly1 ! Assembly.AddComponentByPath(hcd2bPath)
          expectMsgType[ConfigActor.Registered]

          within(duration) {
            assembly1 ! CommandServiceMessage.Submit(config)
            val s1 = expectMsgType[CommandStatus.Queued]
            val s2 = expectMsgType[CommandStatus.Busy]
            val s3 = expectMsgType[CommandStatus.Complete]
            assert(s1.runId == s2.runId)
            assert(s3.runId == s2.runId)
          }
        }
      }

      runOn(container2) {
        log.info("XXX In Node 2: 1")
        //    val container = Container.create("Container-2")
        val container = system.actorOf(Props[Container], "Container-2")

        log.info("XXX In Node 2: 2")
        val hcd2aProps = TestHcd.props("HCD-2A", Set("config.tmt.tel.base.pos"))
        val hcd2bProps = TestHcd.props("HCD-2B", Set("config.tmt.tel.ao.pos.one"))
        log.info("XXX In Node 2: 3")

        container ! Container.CreateComponent(hcd2aProps, "HCD-2A")
        log.info(s"XXX hcd2a = pending....")
        val hcd2a = expectMsgType[ActorRef]
        log.info(s"XXX hcd2a = $hcd2a")
        container ! Container.CreateComponent(hcd2bProps, "HCD-2B")
        val hcd2b = expectMsgType[ActorRef]
        enterBarrier("deployed")
        Thread.sleep(1000) // XXX how to stop from exiting too soon here?
      }
      enterBarrier("finished")
    }
  }
}
