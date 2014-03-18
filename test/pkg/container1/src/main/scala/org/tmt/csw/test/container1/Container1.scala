package org.tmt.csw.test.container1

import akka.actor._
import akka.kernel.Bootable
import akka.util.Timeout
import org.tmt.csw.pkg.Container
import org.tmt.csw.cmd.akka.CommandStatus
import org.tmt.csw.cmd.spray.CommandServiceHttpServer
import scala.concurrent.duration._
import akka.pattern.ask

// This class is started by the Akka microkernel in standalone mode
class Container1 extends Bootable {

  val system = ActorSystem("system")

  def startup(): Unit = {
    system.actorOf(Props[Container1Actor], "Container-1-Actor")
  }

  def shutdown(): Unit = {
    system.shutdown()
  }
}

// The main actor for this application
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


/**
 * The settings for Container1
 */
object Container1Settings extends ExtensionKey[Container1Settings]

class Container1Settings(system: ExtendedActorSystem) extends Extension {

  // The network interface the service gets bound to, e.g. `"localhost"`.
  val interface: String = system.settings.config getString "csw.test.assembly1-http.interface"

  // The port the service gets bound to, e.g. `8080`.
  val port: Int = system.settings.config getInt "csw.test.assembly1-http.port"

  // The amount of time to wait when polling for the command status
  val timeout: FiniteDuration = Duration(system.settings.config getMilliseconds "csw.test.assembly1-http.timeout", MILLISECONDS)
}



