package org.tmt.csw.cmd.spray

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorRef, Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import org.tmt.csw.cmd.core.Configuration
import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.Await
import org.tmt.csw.cmd.akka._

object TestConfig {
  val testConfig =
    """
      |      config {
      |        info {
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

/**
 * Tests the Command Service actor
 */
class TestCommandService extends TestKit(ActorSystem("test"))
  with ImplicitSender with FunSuite with BeforeAndAfterAll with Logging {

  // The Configuration used in the tests below
  val config = Configuration(TestConfig.testConfig)

  val duration : FiniteDuration = 5.seconds
  implicit val timeout = Timeout(duration)
  implicit val dispatcher = system.dispatcher

  // Called at the start of each test to get a new, unique command service and config actor
  // (Note that all tests run at the same time, so each test needs a unique command service)
  def getCommandService(n: Int) : ActorRef = {
    // Create a config service actor
    val commandServiceActor = system.actorOf(Props[CommandServiceActor], name = s"testCommandServiceActor$n")

    // Create 2 config actors, tell them to register with the command service actor and wait, before starting the test
    // (If we start sending commands before the registration is complete, they won't get executed).
    // Each config actor is responsible for a different part of the configs (the path passed as an argument).
    val configActor1 = system.actorOf(TestConfigActor.props("config.tmt.tel.base.pos"), name = s"TestConfigActor${n}A")
    val configActor2 = system.actorOf(TestConfigActor.props("config.tmt.tel.ao.pos.one"), name = s"TestConfigActor${n}B")
    within(duration) {
      // Note: this tells configActor1 to register with the command service. It could do this on its own,
      // (by using a known path to find the commandServiceActor) but doing it this way lets us know when
      // the registration is complete, so we can start sending commands
      configActor1 ! ConfigActor.Register(commandServiceActor)
      configActor2 ! ConfigActor.Register(commandServiceActor)
      expectMsgType[ConfigActor.Registered.type]
      expectMsgType[ConfigActor.Registered.type]
    }

    commandServiceActor
  }


  // -- Tests --


  test("Test simple queue submit") {
    val commandServiceActor = getCommandService(2)
    within(duration) {
      commandServiceActor ! CommandServiceMessage.Submit(config)
      val s1 = expectMsgType[CommandStatus.Queued]
      val s2 = expectMsgType[CommandStatus.Busy]
      val s3 = expectMsgType[CommandStatus.Complete]
      assert(s1.runId == s2.runId)
      assert(s3.runId == s2.runId)
    }
  }

}


