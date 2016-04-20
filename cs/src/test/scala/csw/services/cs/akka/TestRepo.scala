package csw.services.cs.akka

import akka.actor.{ActorRefFactory, ActorSystem}
import csw.services.cs.core.ConfigManager

/**
 * Utility class to create temporary Git or Svn repositories for use in testing.
 */
object TestRepo {
  /**
   * Gets a temporary svn or git repo for testing
   */
  def getTestRepoConfigManager(settings: ConfigServiceSettings = ConfigServiceSettings(ActorSystem()))(implicit context: ActorRefFactory): ConfigManager = {
    if (settings.useSvn) TestSvnRepo.getConfigManager(settings) else TestGitRepo.getConfigManager(settings)
  }
}
