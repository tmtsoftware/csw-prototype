package org.tmt.csw.test.client

import akka.actor._
import akka.actor.ActorIdentity
import scala.Some
import akka.actor.Identify
import org.tmt.csw.cmd.akka.ConfigActor
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

/**
 * Main test client actor
 */
class TestClientActor extends Actor with ActorLogging {

  val configActor1 = context.actorOf(TestConfigActor.props("config.tmt.tel.base.pos"), name = s"TestConfigActorA")
  val configActor2 = context.actorOf(TestConfigActor.props("config.tmt.tel.ao.pos.one"), name = s"TestConfigActorB")

  val identifyId = 1
  val commandServiceActorSelection = context.actorSelection("akka.tcp://TestApp@127.0.0.1:2552/user/testActor/testAppCommandServiceActor")
  commandServiceActorSelection ! Identify(identifyId)

  def receive = {
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

  def start(commandServiceActor: ActorRef) {
    // Start two config actors and tell them to register to receive work from the command service,
    // then send a message to the test app to start sending a command
    implicit val timeout = Timeout(5.seconds)
    implicit val dispatcher = context.dispatcher
    for {
        ack1 <- configActor1 ? ConfigActor.Register(commandServiceActor)
        ack2 <- configActor2 ? ConfigActor.Register(commandServiceActor)
    } {
      context.actorSelection("akka.tcp://TestApp@127.0.0.1:2552/user/testActor") ! "Start"
    }
  }
}
