package org.tmt.csw.cmd.akka

import akka.actor.{Props, ActorRef}
import akka.testkit.{TestKit, ImplicitSender}
import scala.concurrent.duration._

class CommandServiceActorClass extends CommandServiceActor {
  override def receive: Receive = receiveCommands
}

/**
 * Helper class for starting a test command service actor
 */
trait TestHelper extends ImplicitSender { this: TestKit ⇒

  /**
   * Creates and returns a new CommandServiceActor
   * @param n a unique number (needed if multiple command servers are running at once)
   * @param numberOfSecondsToRun number of seconds the worker actors should run
   * @return the actor ref
   */
  def getCommandServiceActor(n: Int = 1, numberOfSecondsToRun: Int = 2): ActorRef = {
    // Create a config service actor
    val commandServiceActor = system.actorOf(Props[CommandServiceActorClass], name = s"testCommandServiceActor$n")
    val duration = (numberOfSecondsToRun * 3).seconds

    // Create 2 config actors, tell them to register with the command service actor and wait, before starting the test
    // (If we start sending commands before the registration is complete, they won't get executed).
    // Each config actor is responsible for a different part of the configs (the path passed as an argument).
    val configActor1 = system.actorOf(TestConfigActor.props("config.tmt.tel.base.pos", numberOfSecondsToRun), name = s"TestConfigActor${n}A")
    val configActor2 = system.actorOf(TestConfigActor.props("config.tmt.tel.ao.pos.one", numberOfSecondsToRun), name = s"TestConfigActor${n}B")
    within(duration) {
      // Note: this tells configActor1 to register with the command service. It could do this on its own,
      // (by using a known path to find the commandServiceActor) but doing it this way lets us know when
      // the registration is complete, so we can start sending commands
      configActor1 ! ConfigActor.Register(commandServiceActor)
      expectMsg(ConfigActor.Registered(configActor1))
      configActor2 ! ConfigActor.Register(commandServiceActor)
      expectMsg(ConfigActor.Registered(configActor2))
    }

    commandServiceActor
  }

}
