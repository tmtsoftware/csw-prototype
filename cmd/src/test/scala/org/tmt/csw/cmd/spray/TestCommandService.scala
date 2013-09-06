package org.tmt.csw.cmd.spray

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import scala.concurrent.duration._
import org.tmt.csw.cmd.core.{TestConfig, Configuration}
import com.typesafe.scalalogging.slf4j.Logging
import org.tmt.csw.cmd.akka._
import spray.client.pipelining._
import spray.util._

/**
 * Tests the Command Service actor
 */
class TestCommandService extends TestKit(ActorSystem("test")) with CommandServiceJsonFormats
    with ImplicitSender with FunSuite with BeforeAndAfterAll with Logging {

  // The Configuration used in the tests below
  val config = Configuration(TestConfig.testConfig)

  val duration : FiniteDuration = 5.seconds
  implicit val dispatcher = system.dispatcher

  val interface = TestCommandServiceSettings(system).interface
  val port = TestCommandServiceSettings(system).port
  implicit val timeout = TestCommandServiceSettings(system).timeout

  startCommandService()


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


  // -- Tests --

  test("Test HTTP REST interface to Command Service") {
    val pipeline = sendReceive ~> unmarshal[RunId]
    val runId = pipeline {
      Post(s"http://$interface:$port/queue/submit", config)
    }.await()
    logger.info(s"Received runId $runId for command request")
  }

  // --

//  override protected def afterAll(): Unit = {
//    logger.info("Shutting down test http server and actor system")
//    IO(Http).ask(Http.CloseAll)(1.second).await
//    TestKit.shutdownActorSystem(system)
//    system.shutdown()
//  }
}

