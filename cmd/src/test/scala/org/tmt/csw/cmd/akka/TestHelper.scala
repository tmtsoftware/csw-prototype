package org.tmt.csw.cmd.akka

import akka.actor.{Props, ActorRef}
import akka.testkit.{TestKit, ImplicitSender}
import scala.concurrent.duration._
import org.tmt.csw.cmd.akka.ConfigRegistrationActor._

// Test HCD object
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

// Test HCD class
class TestHcdCommandServiceActor(configPath: String, numberOfSecondsToRun: Int, name: String)
  extends CommandServiceActor with OneAtATimeCommandQueueController {
  override val configActor = context.actorOf(TestConfigActor.props(commandStatusActor, numberOfSecondsToRun), name)
  override val configPaths = Set(configPath)
  override def receive: Receive = receiveCommands
}

// Test assembly
class TestAssemblyCommandServiceActor extends AssemblyCommandServiceActor with OneAtATimeCommandQueueController {
  override def receive: Receive = receiveCommands
}

/**
 * Helper class for starting a test master command service actor (assembly) with two slave command service actors (HCDs)
 */
trait TestHelper extends ImplicitSender {
  this: TestKit ⇒

  /**
   * Creates and returns a new CommandServiceActor
   * @param n a unique number (needed if multiple command servers are running at once)
   * @param numberOfSecondsToRun number of seconds the worker actors should run
   * @return the actor ref of the assembly command server
   */
  def getCommandServiceActor(n: Int = 1, numberOfSecondsToRun: Int = 2): ActorRef = {
    val assembly = system.actorOf(Props[TestAssemblyCommandServiceActor], name = s"Assembly$n")
    val duration = (numberOfSecondsToRun * 3).seconds

    val hcdA = system.actorOf(TestHcdCommandServiceActor.props("config.tmt.tel.base.pos", numberOfSecondsToRun-1, s"TestConfigActorA$n"),
      name = s"HCD-A$n")

    val hcdB = system.actorOf(TestHcdCommandServiceActor.props("config.tmt.tel.ao.pos.one", numberOfSecondsToRun, s"TestConfigActorB$n"),
      name = s"HCD-B$n")

    within(duration) {
      hcdA ! RegisterRequest(assembly)
      expectMsg(Registered(hcdA))
      hcdB ! RegisterRequest(assembly)
      expectMsg(Registered(hcdB))
    }
    assembly
  }

}
