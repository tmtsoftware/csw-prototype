package csw.services.cmd.spray

import akka.actor._
import akka.io.IO
import scala.concurrent.duration.FiniteDuration
import spray.can.Http
import spray.routing.HttpServiceActor
import csw.services.cmd.akka.{ CommandServiceActorClientHelper, CommandStatus }
import akka.io.Tcp.Event

object CommandServiceHttpServer {

  val unknownRunIdMessage = "Unknown runId: Request may have timed out"

  def props(commandServiceActor: ActorRef, interface: String, port: Int, timeout: FiniteDuration): Props =
    Props(classOf[CommandServiceHttpServer], commandServiceActor, interface, port, timeout)
}

/**
 * A service providing a REST-ful API to the command service actor
 */
case class CommandServiceHttpServer(commandServiceActor: ActorRef, interface: String, port: Int, timeout: FiniteDuration)
    extends HttpServiceActor with CommandServiceHttpRoute with CommandServiceActorClientHelper {

  // Starts the HTTP server for this service on the host and port configured in resources/reference.conf
  IO(Http)(context.system) ! Http.Bind(self, interface, port)

  override def receive: Receive = runRoute(route) orElse {
    case e: Event         ⇒ // ignore Akka Tcp events here
    case s: CommandStatus ⇒ log.debug(s"Received command status $s")
    case x                ⇒ log.error(s"Received unexpected message from ${sender()}: $x")
  }
}
