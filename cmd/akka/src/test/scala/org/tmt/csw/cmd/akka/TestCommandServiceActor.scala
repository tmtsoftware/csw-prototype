package org.tmt.csw.cmd.akka

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import org.tmt.csw.cmd.core.Configuration
import com.typesafe.scalalogging.slf4j.Logging

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
class TestCommandServiceActor extends TestKit(ActorSystem("testsys"))
  with ImplicitSender with FunSuite with BeforeAndAfterAll with Logging {

  val duration = 5.seconds
  implicit val timeout = Timeout(5.seconds)
  implicit val dispatcher = system.dispatcher

  test("Test the CommandServiceActor") {

    val configActorProps = Props {new TestConfigActor(100)}
    val commandServiceActor = system.actorOf(Props {new CommandServiceActor(configActorProps, "test")}, name = "commandService")
    val config = Configuration(TestConfig.testConfig)

    // Queue a command

    val f = commandServiceActor ? CommandServiceActor.QueueSubmit(config)
    f onSuccess {
      case runId: RunId =>
        logger.info(s"got runId: $runId")
        Thread.sleep(1000)
        commandServiceActor ! CommandServiceActor.QueuePause()
        commandServiceActor ? CommandServiceActor.QueueSubmit(config.withObsId("TMT-2021A-C-2-2"))
        commandServiceActor ? CommandServiceActor.QueueSubmit(config.withObsId("TMT-2021A-C-2-3"))
        commandServiceActor ? CommandServiceActor.QueueSubmit(config.withObsId("TMT-2021A-C-2-4"))
        Thread.sleep(3000)
        commandServiceActor ! CommandServiceActor.QueueStart()
        Thread.sleep(3000)
        commandServiceActor ! CommandServiceActor.QueueStop()
        Thread.sleep(1000)
        system.shutdown()
    }
    f onFailure {
      case e: Exception =>
        logger.error("Command failed: ", e)
        system.shutdown()
    }

     // Wait for above to complete!
     system.awaitTermination()
  }

}
