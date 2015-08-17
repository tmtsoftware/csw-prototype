package csw.services.cmd.spray

import akka.actor._
import scala.concurrent.duration.FiniteDuration
import csw.services.cmd.akka.CommandServiceActorClientHelper
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http

object CommandServiceHttpServer {

  val unknownRunIdMessage = "Unknown runId: Request may have timed out"

  def props(commandServiceActor: ActorRef, interface: String, port: Int, timeout: FiniteDuration): Props =
    Props(classOf[CommandServiceHttpServer], commandServiceActor, interface, port, timeout)
}

/**
 * A service providing a REST-ful API to the command service actor
 */
case class CommandServiceHttpServer(commandServiceActor: ActorRef, interface: String, port: Int, timeout: FiniteDuration)
    extends CommandServiceHttpRoute with CommandServiceActorClientHelper {

  implicit val materializer = ActorMaterializer()

  // Starts the HTTP server for this service on the host and port configured in resources/reference.conf
  val bindingFuture = Http(context.system).bindAndHandle(route, interface, port)

  def receive: Receive = {
    case x ⇒ log.error(s"Received unexpected message: $x")
  }
}
