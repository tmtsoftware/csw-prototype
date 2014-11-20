package csw.services.ls

import akka.actor.{ ActorSystem, Extension, ExtensionKey }

object LocationServiceSettings extends ExtensionKey[LocationServiceSettings]

/**
 * The configuration settings for the location service
 */
case class LocationServiceSettings(system: ActorSystem) extends Extension {
  val systemName: String = system.settings.config getString "csw.location-service.systemName"
  val hostname: String = system.settings.config getString "csw.location-service.hostname"
  val port: Int = system.settings.config getInt "csw.location-service.port"
}

