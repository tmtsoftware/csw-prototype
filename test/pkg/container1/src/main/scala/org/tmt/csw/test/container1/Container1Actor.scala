package org.tmt.csw.test.container1

import akka.actor._
import org.tmt.csw.pkg.{Assembly, Container}
import akka.pattern.ask
import org.tmt.csw.cmd.akka.CommandStatus
import org.tmt.csw.cmd.spray.CommandServiceHttpServer
import akka.util.Timeout


class Container1Actor extends Actor with ActorLogging {
  import scala.concurrent.duration._
  val duration: FiniteDuration = 5.seconds
  implicit val timeout = Timeout(duration)
  implicit val dispatcher = context.system.dispatcher

  val container = Container.create("Container-1")

  val assembly1Props = Assembly1.props("Assembly-1")

  // XXX TODO: This would normally be handled by the location service
  val hcd2aPath = ActorPath.fromString(Container1Settings(context.system).hcd2a)
  val hcd2bPath = ActorPath.fromString(Container1Settings(context.system).hcd2b)

  // Receive actor messages
  def receive: Receive = {
    case status: CommandStatus => log.info(s"received command status: $status")
    case x => log.warning(s"received unknown message $x")
  }

  for {
    assembly1 <- (container ? Container.CreateComponent(assembly1Props, "Assembly-1")).mapTo[ActorRef]
    ack2a <- assembly1 ? Assembly.AddComponentByPath(hcd2aPath)
    ack2b <- assembly1 ? Assembly.AddComponentByPath(hcd2bPath)
  } {
      // Start a HTTP server with the REST interface
      val interface = Container1Settings(context.system).interface
      val port = Container1Settings(context.system).port
      val timeout = Container1Settings(context.system).timeout
      context.actorOf(CommandServiceHttpServer.props(assembly1, interface, port, timeout), "commandService")
  }
}

