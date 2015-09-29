package csw.services.cmd_old.spray

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionKey }
import scala.concurrent.duration.{ Duration, FiniteDuration, MILLISECONDS }

object CommandServiceSettings extends ExtensionKey[CommandServiceSettings]

/**
 * The settings for the command service as an Akka extension:
 * - `interface`: the network interface the service gets bound to, e.g. `"localhost"`.
 * - `port`: the port the service gets bound to, e.g. `8080`.
 * - `timeout`: the amount of time to wait when polling for the command status
 *
 * This class is set from the contents of the resources/reference.conf file.
 */
class CommandServiceSettings(system: ExtendedActorSystem) extends Extension {

  /**
   * The network interface the command service service gets bound to, e.g. `"localhost"`.
   */
  val interface: String = system.settings.config getString "csw.services.cmd_old.http.interface"

  /**
   * The port the command service service gets bound to, e.g. `8080`.
   */
  val port: Int = system.settings.config getInt "csw.services.cmd_old.http.port"

  val timeout: FiniteDuration =
    Duration(system.settings.config.getDuration("csw.services.cmd_old.http.timeout", MILLISECONDS),
      MILLISECONDS)
}
