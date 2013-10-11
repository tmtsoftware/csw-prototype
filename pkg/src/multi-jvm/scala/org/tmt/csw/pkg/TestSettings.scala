package org.tmt.csw.pkg

import akka.actor.{Extension, ExtendedActorSystem, ExtensionKey}
import scala.concurrent.duration._

object TestSettings extends ExtensionKey[TestSettings]

/**
 * The settings for Container1
 */
class TestSettings(system: ExtendedActorSystem) extends Extension {

  // The network interface the service gets bound to, e.g. `"localhost"`.
  val interface: String = system.settings.config getString "csw.test.assembly1-http.interface"

  // The port the service gets bound to, e.g. `8080`.
  val port: Int = system.settings.config getInt "csw.test.assembly1-http.port"

  // The amount of time to wait when polling for the command status
  val timeout: FiniteDuration = Duration(system.settings.config getMilliseconds "csw.test.assembly1-http.timeout", MILLISECONDS)

  // Paths for two remote HCDs
  val hcd2a: String = system.settings.config getString "csw.test.hcd2a"
  val hcd2b: String = system.settings.config getString "csw.test.hcd2b"
}

