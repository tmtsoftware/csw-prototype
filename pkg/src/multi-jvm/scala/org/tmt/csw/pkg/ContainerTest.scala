package org.tmt.csw.pkg

import akka.testkit.ImplicitSender
import akka.actor._
import akka.pattern.ask
import org.tmt.csw.cmd.akka.CommandStatus
import org.tmt.csw.cmd.core.Configuration
import akka.util.Timeout
import scala.concurrent.duration._
import akka.remote.testkit.{MultiNodeSpecCallbacks, MultiNodeConfig, MultiNodeSpec}
import org.tmt.csw.cmd.akka.CommandServiceActor.Submit
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers

/**
 * A test that runs each of the classes below in a separate JVM (See the sbt-multi-jvm plugin)
 * See http://doc.akka.io/docs/akka/current/dev/multi-node-testing.html#multi-node-testing.
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

object ContainerConfig extends MultiNodeConfig {
  val container1 = role("container1")
  val container2 = role("container2")
  commonConfig(ConfigFactory.load())
}

class TestMultiJvmContainer1 extends ContainerSpec
class TestMultiJvmContainer2 extends ContainerSpec

trait STMultiNodeSpec extends MultiNodeSpecCallbacks with WordSpec with MustMatchers with BeforeAndAfterAll {
  override def beforeAll(): Unit = multiNodeSpecBeforeAll()
  override def afterAll(): Unit = multiNodeSpecAfterAll()
}

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
        val config = Configuration(ContainerTest.testConfig)
        implicit val dispatcher = system.dispatcher

        val container = Container.create("Container-1")

        val assembly1Props = TestAssembly.props("Assembly-1")
        implicit val timeout = Timeout(5.seconds)

        for {
          assembly1 <- (container ? Container.CreateComponent(assembly1Props, "Assembly-1")).mapTo[ActorRef]
        } {
          assembly1 ! Submit(config)
          within(5.seconds) {
            val s1 = expectMsgType[CommandStatus.Queued]
            val s2 = expectMsgType[CommandStatus.Busy]
            val s3a = expectMsgType[CommandStatus.PartiallyCompleted]
            val s3 = expectMsgType[CommandStatus.Completed]
            assert(s1.runId == s2.runId)
            assert(s3.runId == s2.runId)
            assert(s3a.runId == s3.runId)
            log.info(s"Command status: $s3")
          }
        }
      }

      runOn(container2) {
        val container = Container.create("Container-2")

        val hcd2aProps = TestHcd.props("HCD-2A", "config.tmt.tel.base.pos")
        val hcd2bProps = TestHcd.props("HCD-2B", "config.tmt.tel.ao.pos.one")

        container ! Container.CreateComponent(hcd2aProps, "HCD-2A")
        expectMsgType[ActorRef]
        container ! Container.CreateComponent(hcd2bProps, "HCD-2B")
        expectMsgType[ActorRef]
        enterBarrier("deployed")
        Thread.sleep(5000) // XXX how to stop from exiting too soon here?
      }

      enterBarrier("finished")
    }
  }
}
