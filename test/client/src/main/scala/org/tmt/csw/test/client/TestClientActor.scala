package org.tmt.csw.test.client

import akka.actor._
import akka.actor.ActorIdentity
import scala.Some
import akka.actor.Identify
import org.tmt.csw.cmd.akka.{OneAtATimeCommandQueueController, CommandServiceActor}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import org.tmt.csw.cmd.akka.ConfigRegistrationActor.RegisterRequest

/**
 * Main test client actor
 */
class TestClientActor extends Actor with ActorLogging {

  val settings = Settings(context.system)

  // -- Test command service --
  object TestCommandServiceActor {
    def props(name: String, configPath: String): Props = Props(classOf[TestCommandServiceActor], name, configPath)
  }

  class TestCommandServiceActor(name: String, configPath: String) extends CommandServiceActor with OneAtATimeCommandQueueController {
    override val configActor = context.actorOf(TestConfigActor.props(configPath, commandStatusActor), name = name)
    override def receive: Receive = receiveCommands
  }

  // Create two test command service actors
  val commandServiceActor1 = context.actorOf(TestCommandServiceActor.props("TestConfigActorA", "config.tmt.tel.base.pos"))
  val commandServiceActor2 = context.actorOf(TestCommandServiceActor.props("TestConfigActorB", "config.tmt.tel.ao.pos.one"))

  // -- Register the two test command service actors with the remote assembly command service

  val identifyId = 1
  val remoteCommandServiceActorSelection = context.actorSelection(
    s"akka.tcp://TestApp@${settings.testCommandServerHost}:2552/user/testActor/testAppCommandServiceActor")
  remoteCommandServiceActorSelection ! Identify(identifyId)

  def receive: Receive = {
    case ActorIdentity(`identifyId`, Some(commandServiceActorRef)) =>
      log.debug(s"Received identity $commandServiceActorRef")
      context.watch(commandServiceActorRef)
      context.become(active(commandServiceActorRef))
      start(commandServiceActorRef)

    case ActorIdentity(`identifyId`, None) => context.stop(self)
  }

  def active(commandServiceActorRef: ActorRef): Actor.Receive = {
    case Terminated(`commandServiceActorRef`) =>
      // Just quit (could also retry and wait for another commandServiceActor to start...?)
//      context.stop(self)
      context.system.shutdown()
  }

  def start(remoteCommandServiceActor: ActorRef): Unit = {
    // Start two config actors and tell them to register to receive work from the command service,
    // then send a message to the test app to start sending a command
    implicit val timeout = Timeout(5.seconds)
    implicit val dispatcher = context.dispatcher
    for {
        ack1 <- commandServiceActor1 ? RegisterRequest(remoteCommandServiceActor)
        ack2 <- commandServiceActor2 ? RegisterRequest(remoteCommandServiceActor)
    } {
      context.actorSelection(s"akka.tcp://TestApp@${settings.testCommandServerHost}:2552/user/testActor") ! "Start"
    }
  }
}
