package org.tmt.csw.test.client

/**
 * Config Service settings based on the Akka reference.conf file (under resources in this module)
 */
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import com.typesafe.config.Config

object Settings extends ExtensionId[Settings] with ExtensionIdProvider {
  override def lookup(): Settings.type = Settings
  override def createExtension(system: ExtendedActorSystem): Settings = new Settings(system.settings.config)
}

class Settings(config: Config) extends Extension {
  val testCommandServerHost = config.getString("csw.test.command-server-host")
}

