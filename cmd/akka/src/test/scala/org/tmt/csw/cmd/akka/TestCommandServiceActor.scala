package org.tmt.csw.cmd.akka

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import org.tmt.csw.cmd.core.Configuration

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

class TestConfigActor extends ConfigActor {
  def getName() = "Test Component"

  /**
   * Submits the given configuration
   * @param runId identifies the configuration
   * @param config the configuration to execute
   */
  def configSubmit(runId: RunId, config: Configuration) {
    println("XXX TestComponent: matchConfig: " + config.toString())
    for (a <- 1 to 3) {
      Thread.sleep(1000)
      println("XXX Sleeping")
    }
  }

  def configAbort(runId: RunId) {}

  def configCancel(runId: RunId) {}

  def configPause(runId: RunId) {}

  def configResume(runId: RunId) {}
}

/**
 * Tests the Command Service actor
 */
class TestCommandServiceActor extends TestKit(ActorSystem("mySystem")) with ImplicitSender with FunSuite with BeforeAndAfterAll {
  val duration = 5.seconds
  implicit val timeout = Timeout(5.seconds)
  implicit val dispatcher = system.dispatcher

  test("Test the CommandServiceActor") {

    // Create the actor
    val configActor = system.actorOf(Props(new TestConfigActor()), name = "testActor")
    val commandServiceActor = system.actorOf(Props(new CommandServiceActor(configActor, "testActor")), name = "commandService")

    // Queue a command
    val config = Configuration(TestConfig.testConfig)
    val f = commandServiceActor ? CommandServiceActor.QueueSubmit(config)
    f onSuccess {
      case runId: RunId =>
        println("XXX got runId: " + runId)
        Thread.sleep(3000)
        commandServiceActor ! CommandServiceActor.QueueStop
        system.shutdown()
    }
    f onFailure {
      case e: Exception =>
        e.printStackTrace()
        system.shutdown()
    }

     // Wait for above to complete!
     system.awaitTermination()
  }

}
