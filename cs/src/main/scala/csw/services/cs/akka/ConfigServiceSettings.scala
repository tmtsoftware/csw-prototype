package csw.services.cs.akka

/**
 * Config Service settings based on the Akka reference.conf file (under resources in this module)
 */

import akka.actor._
import akka.util.Timeout
import com.typesafe.config.Config
import java.io.File
import java.net.URI

import csw.services.cs.core.ConfigManager
import csw.services.cs.core.git.GitConfigManager
import csw.services.cs.core.svn.SvnConfigManager
import csw.services.loc.Connection

import scala.concurrent.duration._

object ConfigServiceSettings extends ExtensionId[ConfigServiceSettings] with ExtensionIdProvider {
  override def lookup(): ConfigServiceSettings.type = ConfigServiceSettings

  override def createExtension(system: ExtendedActorSystem): ConfigServiceSettings = new ConfigServiceSettings(system.settings.config)

  def getConfigServiceSettings(system: ActorSystem): ConfigServiceSettings = ConfigServiceSettings(system)

  val defaultName = "Config Service"
}

case class ConfigServiceSettings(config: Config) extends Extension {
  // Main prefix for keys used below
  private val prefix = "csw.services.cs"

  /**
   * Name of this config service
   */
  val name: String = config.getString(s"$prefix.name")

  val useSvn: Boolean = if (config.hasPath(s"$prefix.use-svn")) config.getBoolean(s"$prefix.use-svn") else true

  /**
   * URI for the main svn or git repository to use
   */
  val mainRepository = new URI(subst(config.getString(s"$prefix.main-repository")))

  /**
   * Directory to use for the local git repository
   */
  val localRepository = new File(subst(config.getString(s"$prefix.local-repository")))

  /**
   * Timeout while waiting for replies from actors
   */
  val timeout = Timeout(config.getDuration(s"$prefix.timeout", MILLISECONDS), MILLISECONDS)

  /**
   * If true, the config service http server is started
   */
  val startHttpServer: Boolean = if (config.hasPath(s"$prefix.http.enabled")) config.getBoolean(s"$prefix.http.enabled") else false

  /**
   * The interface (hostname or IP) to listen on for the http server
   */
  val httpInterface: String = if (startHttpServer) config.getString(s"$prefix.http.interface") else ""

  /**
   * The port to listen on for the http server
   */
  val httpPort: Int = if (config.hasPath(s"$prefix.http.port")) config.getInt(s"$prefix.http.port") else 0

  // Do any required substitution on the setting values
  def subst(s: String): String = {
    s.replaceFirst("~", System.getProperty("user.home")).replace("$CSW_SERVICE_PREFIX", Connection.servicePrefix)
  }

  /**
   * Returns a ConfigManager instance based on the settings
   */
  def getConfigManager()(implicit context: ActorRefFactory): ConfigManager = {
    if (useSvn)
      SvnConfigManager(mainRepository, name)
    else
      GitConfigManager(localRepository, mainRepository, name)
  }
}

