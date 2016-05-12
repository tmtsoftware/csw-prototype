package javacsw.services.cs.akka

import javacsw.services.cs.{JBlockingConfigManager, JConfigManager}

import akka.actor.{ActorRefFactory, ActorSystem}
import csw.services.cs.akka.ConfigServiceSettings
import csw.services.cs.core.ConfigManager

/**
 * Utility class to create temporary Git or Svn repositories for use in testing.
 */
object TestRepo {
  /**
   * Gets a temporary svn or git repo for testing and returns a blocking config manager
   */
  def getTestRepoBlockingConfigManager: JBlockingConfigManager = {
    implicit val system = ActorSystem()
    val settings = ConfigServiceSettings(system)
    if (settings.useSvn) TestSvnRepo.getJBlockingConfigManager else TestGitRepo.getJBlockingConfigManager
  }

  /**
    * Gets a temporary svn or git repo for testing and returns the config manager
    */
  def getTestRepoConfigManager: JConfigManager = {
    implicit val system = ActorSystem()
    val settings = ConfigServiceSettings(system)
    if (settings.useSvn) TestSvnRepo.getJConfigManager else TestGitRepo.getJConfigManager
  }
}
