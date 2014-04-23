package org.tmt.csw.event

import akka.actor.{ActorSystem, Extension, ExtensionKey}

object EventServiceSettings extends ExtensionKey[EventServiceSettings]

/**
 * The configuration settings for the event service
 */
case class EventServiceSettings(system: ActorSystem) extends Extension {
  val eventServiceHostname: String = system.settings.config getString "csw.event-service.hostname"
  val eventServicePort: Int = system.settings.config getInt "csw.event-service.port"
}

