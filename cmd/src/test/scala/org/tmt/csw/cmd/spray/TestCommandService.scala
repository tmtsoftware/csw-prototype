package org.tmt.csw.cmd.spray

import akka.actor.{ActorRef, ActorSystem, Props}
import org.tmt.csw.cmd.core.Configuration
import org.tmt.csw.cmd.akka._
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import org.specs2.time.NoTimeConversions
import scala.concurrent.duration._

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
class TestCommandService extends Specification with Specs2RouteTest with CommandServiceRoute with NoTimeConversions {

  // The Configuration used in the tests below
  val config = Configuration(TestConfig.testConfig)

  // Required by the traits used
  def actorRefFactory: ActorSystem = system

  var actorMap = Map[RunId, ActorRef]()

  // Create a config service actor
  val commandServiceActor = system.actorOf(Props[CommandServiceActor], name = "testCommandServiceActor")

  // Create 2 config actors, tell them to register with the command service actor and wait, before starting the test
  // (If we start sending commands before the registration is complete, they won't get executed).
  // Each config actor is responsible for a different part of the configs (the path passed as an argument).
  val configActor1 = system.actorOf(TestConfigActor.props("config.tmt.tel.base.pos"), name = "TestConfigActorA")
  val configActor2 = system.actorOf(TestConfigActor.props("config.tmt.tel.ao.pos.one"), name = "TestConfigActorB")
  configActor1 ! ConfigActor.Register(commandServiceActor)
  configActor2 ! ConfigActor.Register(commandServiceActor)
  // may need to wait here (Since this class is not an actor we can't wait for the reply)
  Thread.sleep(500)

  val timeout = CommandServiceSettings(system).timeout

  // creates a new CommandServiceMonitor actor to listen for status messages for the given runId
  override def newMonitorFor(runId: RunId): ActorRef = {
    val actorRef = system.actorOf(CommandServiceMonitor.props(timeout), monitorName(runId))
    actorMap += (runId -> actorRef)
    actorRef
  }

  // Gets an existing CommandServiceMonitor actor for the given runId
  override def getMonitorFor(runId: RunId): Option[ActorRef] = {
    actorMap.get(runId)
  }

  // Gets the name of the CommandServiceMonitor actor for the given runId
  private def monitorName(runId: RunId): String = {
    s"CommandServiceMonitor-${runId.id}"
  }


  // -- Tests --
  implicit val routeTestTimeout = RouteTestTimeout(5 seconds)

  "The command service" should {
    "return a runId for a POST /submit and return the status for GET /status/$runId" in {
      val runId = Post("/submit", config) ~> route ~> check {
        entityAs[RunId]
      }

      val status = getStatus(runId)
      assert(status.isInstanceOf[CommandStatus.Complete])
    }
  }

  // Polls the command status for the given runId until the command completes
  def getStatus(runId: RunId): CommandStatus = {
    Get(s"/status/$runId") ~> route ~> check {
      val status = entityAs[CommandStatus]
      println(s"Command status is $status")
      if (status.done) {
        status
      } else {
        getStatus(runId)
      }
    }
  }
}


