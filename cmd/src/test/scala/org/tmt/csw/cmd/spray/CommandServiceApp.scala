package org.tmt.csw.cmd.spray

import akka.actor._
import org.tmt.csw.cmd.akka.{ConfigActor, TestConfigActor, CommandServiceActor}

/**
 * Standalone command service application
 */
object CommandServiceApp extends App {
  /**
   * Construct the ActorSystem we will use in our application
   */
  implicit lazy val system = ActorSystem("commandServiceApp")
  implicit val dispatcher = system.dispatcher

  /**
   * Ensure that the constructed ActorSystem is shut down when the JVM shuts down
   */
  sys.addShutdownHook(system.shutdown())

  val interface = CommandServiceSettings(system).interface
  val port = CommandServiceSettings(system).port
  val timeout = CommandServiceSettings(system).timeout
  system.actorOf(Props[AppActor])

  class AppActor extends Actor with ActorLogging {
    system.actorOf(CommandService.props(getCommandServiceActor, interface, port, timeout), "commandService")

    override def receive: Receive = {
      case ConfigActor.Registered =>
        log.debug("Received registered ack")
    }

    // Called at the start of each test to get a new, unique command service and config actor
    // (Note that all tests run at the same time, so each test needs a unique command service)
    def getCommandServiceActor: ActorRef = {
      // Create a config service actor
      val commandServiceActor = system.actorOf(Props[CommandServiceActor], name = "testCommandServiceActor")

      // Create 2 config actors, tell them to register with the command service actor and wait, before starting the test
      // (If we start sending commands before the registration is complete, they won't get executed).
      // Each config actor is responsible for a different part of the configs (the path passed as an argument).
      val configActor1 = system.actorOf(TestConfigActor.props("config.tmt.tel.base.pos"), name = "TestConfigActorA")
      val configActor2 = system.actorOf(TestConfigActor.props("config.tmt.tel.ao.pos.one"), name = "TestConfigActorB")
      configActor1 ! ConfigActor.Register(commandServiceActor)
      configActor2 ! ConfigActor.Register(commandServiceActor)
      commandServiceActor
    }
  }
}
