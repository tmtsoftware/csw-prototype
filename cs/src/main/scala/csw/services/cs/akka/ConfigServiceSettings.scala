package csw.services.cs.akka

/**
 * Config Service settings based on the Akka reference.conf file (under resources in this module)
 */

import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import akka.util.Timeout
import com.typesafe.config.Config
import java.io.File
import java.net.URI

import scala.concurrent.duration._

object ConfigServiceSettings extends ExtensionId[ConfigServiceSettings] with ExtensionIdProvider {
  override def lookup(): ConfigServiceSettings.type = ConfigServiceSettings

  override def createExtension(system: ExtendedActorSystem): ConfigServiceSettings = new ConfigServiceSettings(system.settings.config)
}

case class ConfigServiceSettings(config: Config) extends Extension {
  val prefix = "csw.services.cs"
  val name = config.getString(s"$prefix.name")
  val gitMainRepository = new URI(config.getString(s"$prefix.main-repository"))
  val gitLocalRepository = new File(subst(config.getString(s"$prefix.local-repository")))
  val chunkSize = config.getInt(s"$prefix.chunkSize")
  val httpInterface = config.getString(s"$prefix.http-interface")
  val httpPort = config.getInt(s"$prefix.http-port")
  val timeout = Timeout(config.getDuration(s"$prefix.timeout", MILLISECONDS), MILLISECONDS)

  // Do any required substitution on the setting values
  def subst(s: String): String = {
    s.replaceFirst("~", System.getProperty("user.home"))
  }
}

