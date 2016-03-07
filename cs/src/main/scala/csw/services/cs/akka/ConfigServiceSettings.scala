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
  // Main prefix for keys used below
  private val prefix = "csw.services.cs"

  /**
    * Name of this config service
    */
  val name = config.getString(s"$prefix.name")

  /**
    * URI for the main git repository to use
    */
  val gitMainRepository = new URI(config.getString(s"$prefix.git.main-repository"))

  /**
    * Directory to use for the local git repository
    */
  val gitLocalRepository = new File(subst(config.getString(s"$prefix.git.local-repository")))

  /**
    * URI for the svn repository to use
    */
  val svnRepository = new URI(config.getString(s"$prefix.svn.repository"))

  /**
    * Timeout while waiting for replies from actors
    */
  val timeout = Timeout(config.getDuration(s"$prefix.timeout", MILLISECONDS), MILLISECONDS)

  /**
    * If true, the config service http server is started
    */
  val startHttpServer = config.hasPath(s"$prefix.http.interface")

  /**
    * The interface (hostname or IP) to listen on for the http server
    */
  val httpInterface = if (startHttpServer) config.getString(s"$prefix.http.interface") else ""

  /**
    * The port to listen on for the http server
    */
  val httpPort = if (config.hasPath(s"$prefix.http.port")) config.getInt(s"$prefix.http.port") else 0

  // Do any required substitution on the setting values
  def subst(s: String): String = {
    s.replaceFirst("~", System.getProperty("user.home"))
  }
}

