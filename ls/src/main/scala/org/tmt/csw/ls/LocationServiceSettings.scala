package org.tmt.csw.ls

import akka.actor.{ActorSystem, Extension, ExtensionKey}

object Container1Settings extends ExtensionKey[LocationServiceSettings]

/**
 * The configuration settings for the location service
 */
case class LocationServiceSettings(system: ActorSystem) extends Extension {
  val hostname: String = system.settings.config getString "akka.remote.netty.tcp.hostname"
  val port: Int = system.settings.config getInt "akka.remote.netty.tcp.port"
}

