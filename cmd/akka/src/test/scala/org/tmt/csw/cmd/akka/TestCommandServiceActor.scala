package org.tmt.csw.cmd.akka

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import org.tmt.csw.cmd.core.Configuration
import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.Await

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

// XXX TODO: Add more tests, deal with shutting down Akka at the end instead of in each test...

/**
 * Tests the Command Service actor
 */
class TestCommandServiceActor extends TestKit(ActorSystem("testsys"))
with ImplicitSender with FunSuite with BeforeAndAfterAll with Logging {

  implicit val timeout = Timeout(5.seconds)
  implicit val dispatcher = system.dispatcher

  test("Test basic CommandServiceActor queue request") {
    val configActorProps = TestConfigActor.props(3)
    val commandServiceActor = system.actorOf(CommandServiceActor.props(configActorProps, "test"), name = "commandServiceActor")
    val config = Configuration(TestConfig.testConfig)

    // Request a command without being queued
    val status =  Await.result(commandServiceActor ? CommandServiceActor.QueueBypassRequest(config, timeout),
      5.seconds).asInstanceOf[CommandStatus.Complete]
    logger.info(s"Received command status: $status")
  }

  test("Test basic CommandServiceActor queue submit") {
    val configActorProps = TestConfigActor.props(3)
    val commandServiceActor = system.actorOf(CommandServiceActor.props(configActorProps, "test"), name = "commandServiceActor")
    val config = Configuration(TestConfig.testConfig)

    // Queue a command
    val f = commandServiceActor ? CommandServiceActor.QueueSubmit(config, self)
    val runId = Await.result(f, 5.seconds).asInstanceOf[RunId]
    logger.info(s"Received runId for command: $runId")
    expectMsgType[CommandStatus.Queued](5.seconds)
    expectMsgType[CommandStatus.Busy](5.seconds)
    expectMsgType[CommandStatus.Complete](5.seconds)
  }

  test("Test basic CommandServiceActor queue submit  with config abort") {
    val configActorProps = TestConfigActor.props(3)
    val commandServiceActor = system.actorOf(CommandServiceActor.props(configActorProps, "test"), name = "commandServiceActor")
    val config = Configuration(TestConfig.testConfig)

    // Queue a command
    val f = commandServiceActor ? CommandServiceActor.QueueSubmit(config, self)
    val runId = Await.result(f, 5.seconds).asInstanceOf[RunId]
    logger.info(s"Received runId for command: $runId")
    commandServiceActor ! CommandServiceActor.ConfigAbort(runId)
    expectMsgType[CommandStatus.Queued](5.seconds)
    expectMsgType[CommandStatus.Busy](5.seconds)
    expectMsgType[CommandStatus.Aborted](5.seconds)
  }

  //        commandServiceActor ! CommandServiceActor.QueuePause
  //        commandServiceActor ! CommandServiceActor.QueueSubmit(config.withObsId("TMT-2021A-C-2-2"))
  //        commandServiceActor ! CommandServiceActor.QueueSubmit(config.withObsId("TMT-2021A-C-2-3"))
  //        commandServiceActor ! CommandServiceActor.QueueSubmit(config.withObsId("TMT-2021A-C-2-4"))
  //        Thread.sleep(3000)
  //        commandServiceActor ! CommandServiceActor.QueueStart
  //        Thread.sleep(3000)
  //        commandServiceActor ! CommandServiceActor.QueueStop
  //        Thread.sleep(1000)


  override protected def afterAll() {
    logger.info("Shutting down test actor system")
    TestKit.shutdownActorSystem(system)
  }
}
