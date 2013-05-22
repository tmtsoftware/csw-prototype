package org.tmt.csw.test.app

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
  override def createExtension(system: ExtendedActorSystem) = new Settings(system.settings.config)
}

class Settings(config: Config) extends Extension {
  val testMainRepository = subst(config.getString("csw.test.test-main-repository"))
  val testLocalRepository = new File(subst(config.getString("csw.test.test-local-repository")))

  // Do any required substitution on the setting values
  def subst(s : String) : String = {
    s.replaceFirst("~", System.getProperty("user.home"))
  }
}

