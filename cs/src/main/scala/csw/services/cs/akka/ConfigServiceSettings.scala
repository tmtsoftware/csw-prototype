package csw.services.cs.akka

/**
 * Config Service settings based on the Akka reference.conf file (under resources in this module)
 */

import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import com.typesafe.config.Config
import java.io.File
import java.net.URI

object ConfigServiceSettings extends ExtensionId[ConfigServiceSettings] with ExtensionIdProvider {
  override def lookup(): ConfigServiceSettings.type = ConfigServiceSettings

  override def createExtension(system: ExtendedActorSystem): ConfigServiceSettings = new ConfigServiceSettings(system.settings.config)
}

class ConfigServiceSettings(config: Config) extends Extension {
  val gitMainRepository = new URI(config.getString("csw.config-service.git.main-repository"))
  val gitLocalRepository = new File(subst(config.getString("csw.config-service.git.local-repository")))

  // XXX temp
  val gitOversizeStorage = new URI(config.getString("csw.config-service.git.oversize-storage"))

  // Do any required substitution on the setting values
  def subst(s: String): String = {
    s.replaceFirst("~", System.getProperty("user.home"))
  }
}

