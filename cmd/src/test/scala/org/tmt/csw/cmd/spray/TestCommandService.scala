package org.tmt.csw.cmd.spray

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import scala.concurrent.duration._
import org.tmt.csw.cmd.core.{TestConfig, Configuration}
import com.typesafe.scalalogging.slf4j.Logging
import org.tmt.csw.cmd.akka._
import spray.client.pipelining._
import scala.concurrent.Future

/**
 * Tests the Command Service HTTP/REST interface in an actor environment.
 */
class TestCommandService extends TestKit(ActorSystem("test")) with CommandServiceJsonFormats
    with ImplicitSender with FunSuite with BeforeAndAfterAll with Logging {

  // The Configuration used in the tests below
  val config = Configuration(TestConfig.testConfig)

  val duration : FiniteDuration = 5.seconds
  implicit val dispatcher = system.dispatcher

  // Settings
  val interface = CommandServiceTestSettings(system).interface
  val port = CommandServiceTestSettings(system).port
  implicit val timeout = CommandServiceTestSettings(system).timeout

  startCommandService()

  // -- Tests --

  test("Test HTTP REST interface to Command Service") {
    for {
      runId <- submit(config)
      commandStatus <- pollCommandStatus(runId)
    } {
      logger.info(s"Received command status $commandStatus for submit command with runId $runId ")
      commandStatus match {
        case CommandStatus.Complete(_) =>
        case _ => fail(s"Expected the command to complete normally, but got $commandStatus")
      }

      // If we don't call this, the call to system.awaitTermination() below will hang
      system.shutdown()
    }

    // Wait for above to complete!
    system.awaitTermination()
  }


  def submit(config: Configuration): Future[RunId] = {
    logger.info(s"Calling submit")
    val pipeline = sendReceive ~> unmarshal[RunId]
    pipeline {
      Post(s"http://$interface:$port/queue/submit", config)
    }
  }

  // Polls the command status for the given runId until the command completes
  def pollCommandStatus(runId: RunId): Future[CommandStatus] = {
    logger.info(s"Calling pollCommandStatus with $runId")
    val f = for (commandStatus <- getCommandStatus(runId)) yield {
      if (commandStatus.done) {
        logger.info(s"CommandStatus done: $commandStatus")
        Future.successful(commandStatus)
      } else {
        getCommandStatus(runId)
      }
    }
    // Flatten the result, which is of type Future[Future[CommandStatus]]
    f.flatMap[CommandStatus] {x => x}
  }


  // Gets the command status once
  def getCommandStatus(runId: RunId): Future[CommandStatus] = {
    logger.info(s"Calling getCommandStatus for runId $runId")
    val pipeline = sendReceive ~> unmarshal[CommandStatus]
    pipeline {
      Get(s"http://$interface:$port/config/$runId/status")
    }
  }


  // Start the command service, passing it a command service actor, set up with two config actors that
  // will implement the commands.
  def startCommandService() : Unit = {
    // Create a config service actor
    val commandServiceActor = system.actorOf(Props[CommandServiceActor], name = s"testCommandServiceActor")

    // Create 2 config actors, tell them to register with the command service actor and wait, before starting the test
    // (If we start sending commands before the registration is complete, they won't get executed).
    // Each config actor is responsible for a different part of the configs (the path passed as an argument).
    val configActor1 = system.actorOf(TestConfigActor.props("config.tmt.tel.base.pos"), name = s"TestConfigActorA")
    val configActor2 = system.actorOf(TestConfigActor.props("config.tmt.tel.ao.pos.one"), name = s"TestConfigActorB")
    within(duration) {
      // Note: this tells configActor1 to register with the command service. It could do this on its own,
      // (by using a known path to find the commandServiceActor) but doing it this way lets us know when
      // the registration is complete, so we can start sending commands
      configActor1 ! ConfigActor.Register(commandServiceActor)
      configActor2 ! ConfigActor.Register(commandServiceActor)
      expectMsgType[ConfigActor.Registered.type]
      expectMsgType[ConfigActor.Registered.type]
    }

    system.actorOf(CommandService.props(commandServiceActor, interface, port, timeout), "commandService")
  }
}

