package org.tmt.csw.cs.akka

/**
 * Config Service settings based on the Akka reference.conf file (under resources in this module)
 */
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import com.typesafe.config.Config
import java.io.File

object Settings extends ExtensionId[Settings] with ExtensionIdProvider {

  override def lookup() = Settings

  override def createExtension(system: ExtendedActorSystem) =
    new Settings(system.settings.config)
}

class Settings(config: Config) extends Extension {
  val gitMainRepository = subst(config.getString("csw.cs.git-main-repository"))
  val gitLocalRepository = new File(subst(config.getString("csw.cs.git-local-repository")))

  // Do any required substitution on the setting values
  def subst(s : String) : String = {
    s.replaceFirst("~", System.getProperty("user.home"))
  }
}

