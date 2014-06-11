package csw.services.cmd.akka

import akka.actor.{Props, ActorRef}
import akka.testkit.{TestKit, ImplicitSender}
import csw.services.ls.LocationServiceActor
import LocationServiceActor._
import java.net.URI
import csw.services.cmd.akka.CommandServiceActor.{StatusRequest, CommandServiceStatus}

// Test HCD
object TestHcdCommandServiceActor {
  /**
   * Props to create the test HCD actor
   * @param configPath the config keys that this HCD is interested in
   * @param numberOfSecondsToRun number of seconds for processing the config
   * @param name name of the ConfigActor to create
   */
  def props(configPath: String, numberOfSecondsToRun: Int, name: String): Props =
    Props(classOf[TestHcdCommandServiceActor], configPath, numberOfSecondsToRun, name)
}

class TestHcdCommandServiceActor(configPath: String, numberOfSecondsToRun: Int, name: String)
  extends CommandServiceActor with OneAtATimeCommandQueueController {
  override val configActor = context.actorOf(TestConfigActor.props(commandStatusActor, numberOfSecondsToRun), name)

  override def receive: Receive = receiveCommands
}

// Test assembly
object TestAssemblyCommandServiceActor {
  // Note: for testing we pass in the list of HCDs. Normally we would request them from the location service.
  def props(hcds: List[LocationServiceInfo]): Props = Props(classOf[TestAssemblyCommandServiceActor], hcds)
}

class TestAssemblyCommandServiceActor(hcds: List[LocationServiceInfo]) extends AssemblyCommandServiceActor with OneAtATimeCommandQueueController {
  override def receive: Receive = receiveCommands

  configDistributorActor ! ServicesReady(hcds)
}

/**
 * Helper class for starting a test master command service actor (assembly) with two slave command service actors (HCDs)
 */
trait TestHelper extends ImplicitSender {
  this: TestKit ⇒

  /**
   * Creates and returns a new CommandServiceActor
   * @param numberOfSecondsToRun number of seconds the worker actors should run
   * @return the actor ref of the assembly command server
   */
  def getCommandServiceActor(numberOfSecondsToRun: Int = 2): ActorRef = {
    val hcdA = system.actorOf(
      TestHcdCommandServiceActor.props("config.tmt.tel.base.pos", numberOfSecondsToRun - 1, s"TestConfigActorA"),
      name = s"HCD-A")
    val hcdB = system.actorOf(
      TestHcdCommandServiceActor.props("config.tmt.tel.ao.pos.one", numberOfSecondsToRun, s"TestConfigActorB"),
      name = s"HCD-B")

    // Normally this information would come from the location service, but for testing it is hard coded here
    val hcds = List(
      LocationServiceInfo(
        ServiceId(s"HCD-A", ServiceType.HCD), List(new URI(hcdA.path.toString)),
        Some("config.tmt.tel.base.pos"), Some(hcdA)),
      LocationServiceInfo(
        ServiceId(s"HCD-B", ServiceType.HCD), List(new URI(hcdB.path.toString)),
        Some("config.tmt.tel.ao.pos.one"), Some(hcdB))
    )

    val commandServiceActor = system.actorOf(TestAssemblyCommandServiceActor.props(hcds), name = s"Assembly")
    waitForReady(commandServiceActor)
  }

  // Wait for the command service to be ready before returning (should only be necessary when testing)
  def waitForReady(commandServiceActor: ActorRef): ActorRef = {
    commandServiceActor ! StatusRequest
    val status = expectMsgType[CommandServiceStatus]
    if (status.ready) {
      commandServiceActor
    } else {
      waitForReady(commandServiceActor)
    }
  }
}
