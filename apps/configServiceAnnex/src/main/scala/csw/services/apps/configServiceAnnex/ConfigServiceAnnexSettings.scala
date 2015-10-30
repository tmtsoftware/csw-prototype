package csw.services.apps.configServiceAnnex

import java.io.File

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.util.Timeout
import com.typesafe.config.Config

import scala.concurrent.duration._

/**
  * Config Service Annex settings based on the Akka application.conf file
  * (under resources in this module).
  */
object ConfigServiceAnnexSettings extends ExtensionId[ConfigServiceAnnexSettings] with ExtensionIdProvider {
  override def lookup(): ConfigServiceAnnexSettings.type = ConfigServiceAnnexSettings

  override def createExtension(system: ExtendedActorSystem): ConfigServiceAnnexSettings = new ConfigServiceAnnexSettings(system.settings.config)
}

/**
  * Config Service Annex settings based on the Akka application.conf file
  * (under resources in this module).
  */
class ConfigServiceAnnexSettings(config: Config) extends Extension {
  val prefix = "csw.services.apps.configServiceAnnex"
  val interface = config.getString(s"$prefix.interface")
  val port = config.getInt(s"$prefix.port")
  val dir = new File(subst(config.getString(s"$prefix.dir")))
  val chunkSize = config.getInt(s"$prefix.chunkSize")
  val timeout = Timeout(config.getDuration(s"$prefix.timeout", MILLISECONDS), MILLISECONDS)

  // Do any required substitution on the setting values
  def subst(s: String): String = {
    s.replaceFirst("~", System.getProperty("user.home"))
  }
}

