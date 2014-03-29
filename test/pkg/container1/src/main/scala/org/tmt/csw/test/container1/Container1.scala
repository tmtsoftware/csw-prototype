package org.tmt.csw.test.container1

import akka.actor._
import org.tmt.csw.pkg.Container
import org.tmt.csw.cmd.akka.CommandStatus
import scala.concurrent.duration._

object Container1 {
  // Container1 main
  def main(args: Array[String]): Unit = {
    akka.Main.main(Array(classOf[Container1Actor].getName))
  }
}

// The main actor for this application
class Container1Actor extends Actor with ActorLogging {
  val container = Container.create("Container-1")
  val assembly1Props = Assembly1.props("Assembly-1")

  // Receive actor messages
  def receive: Receive = {
    case actorRef: ActorRef => log.info(s"Created actor component: $actorRef")
    case status: CommandStatus => log.info(s"received command status: $status")
    case x => log.warning(s"received unknown message $x")
  }

  container ! Container.CreateComponent(assembly1Props, "Assembly-1")
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
  val timeout: FiniteDuration = Duration(system.settings.config.getDuration("csw.test.assembly1-http.timeout", MILLISECONDS),
    MILLISECONDS)
}



