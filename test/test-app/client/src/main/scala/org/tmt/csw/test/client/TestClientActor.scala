package org.tmt.csw.test.client

import akka.actor._
import akka.actor.ActorIdentity
import scala.Some
import akka.actor.Identify
import org.tmt.csw.cmd.akka.{OneAtATimeCommandQueueController, CommandServiceActor}
import org.tmt.csw.ls.LocationServiceActor.{ServiceType, ServiceId}
import java.net.URI
import org.tmt.csw.ls.LocationService

// -- Test command service --
object TestCommandServiceActor {
  def props(name: String, configPath: String): Props = Props(classOf[TestCommandServiceActor], name, configPath)
}

class TestCommandServiceActor(name: String, configPath: String) extends CommandServiceActor with OneAtATimeCommandQueueController {
  override val configActor = context.actorOf(TestConfigActor.props(commandStatusActor), name = name)
  override def receive: Receive = receiveCommands

  // Register with the location service (which must be started as a separate process)
  val serviceId = ServiceId(name, ServiceType.HCD)
  LocationService.register(context.system, self, serviceId, Some(configPath))
}

/**
 * Main test client actor
 */
class TestClientActor extends Actor with ActorLogging {

  val settings = Settings(context.system)

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
      context.system.shutdown()
  }

  def start(remoteCommandServiceActor: ActorRef): Unit = {
      context.actorSelection(s"akka.tcp://TestApp@${settings.testCommandServerHost}:2552/user/testActor") ! "Start"
  }
}
