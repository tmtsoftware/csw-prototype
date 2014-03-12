package org.tmt.csw.test.container1

import akka.actor._
import org.tmt.csw.pkg.Container
import akka.pattern.ask
import org.tmt.csw.cmd.akka.CommandStatus
import org.tmt.csw.cmd.spray.CommandServiceHttpServer
import akka.util.Timeout
import org.tmt.csw.ls.LocationService
import org.tmt.csw.ls.LocationServiceActor.{LocationServiceInfo, ServicesReady, ServiceType, ServiceId}


class Container1Actor extends Actor with ActorLogging {
  import scala.concurrent.duration._
  val duration: FiniteDuration = 5.seconds
  implicit val timeout = Timeout(duration)
  implicit val dispatcher = context.system.dispatcher

  val container = Container.create("Container-1")
  val assembly1Props = Assembly1.props("Assembly-1")

  // Receive actor messages
  def receive: Receive = {
    case status: CommandStatus => log.info(s"received command status: $status")
    case x => log.warning(s"received unknown message $x")
  }

  for {
    assembly1 <- (container ? Container.CreateComponent(assembly1Props, "Assembly-1")).mapTo[ActorRef]
  } {
      // Start a HTTP server with the REST interface
      val interface = Container1Settings(context.system).interface
      val port = Container1Settings(context.system).port
      val timeout = Container1Settings(context.system).timeout
      context.actorOf(CommandServiceHttpServer.props(assembly1, interface, port, timeout), "commandService")
  }
}

