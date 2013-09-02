package org.tmt.csw.cmd.spray

import akka.actor.{ActorRefFactory, Props}
import org.tmt.csw.cmd.core.Configuration
import org.tmt.csw.cmd.akka._
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService

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
 * Tests the Command Service
 */
class TestCommandService extends Specification with Specs2RouteTest with HttpService {

  // The Configuration used in the tests below
  val config = Configuration(TestConfig.testConfig)

  implicit val dispatcher = system.dispatcher
  def actorRefFactory: ActorRefFactory = system

  // Create a config service actor
  val commandServiceActor = system.actorOf(Props[CommandServiceActor], name = "testCommandServiceActor")

  // Create 2 config actors, tell them to register with the command service actor and wait, before starting the test
  // (If we start sending commands before the registration is complete, they won't get executed).
  // Each config actor is responsible for a different part of the configs (the path passed as an argument).
  val configActor1 = system.actorOf(TestConfigActor.props("config.tmt.tel.base.pos"), name = "TestConfigActorA")
  val configActor2 = system.actorOf(TestConfigActor.props("config.tmt.tel.ao.pos.one"), name = "TestConfigActorB")
  // XXX may need to wait here

  val interface = CommandServiceSettings(system).interface
  val port = CommandServiceSettings(system).port
  val timeout = CommandServiceSettings(system).timeout
  val commandService = system.actorOf(CommandService.props(commandServiceActor, interface, port, timeout), "commandService")

  // XXX shouldn't normally do this...
  val route = new CommandService(commandServiceActor, interface, port, timeout).route


  // -- Tests --

  "The service" should {

    "return a greeting for GET requests to the root path" in {
      // XXX how to do a POST here?
      Post("submit") ~> route ~> check {
        entityAs[String] must contain("Say hello")
      }
    }
  }

}


