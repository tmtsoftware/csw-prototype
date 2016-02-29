package csw.services.cs.akka

import java.io.File

import akka.actor.{ActorRefFactory, ActorSystem}
import csw.services.cs.JConfigManager
import csw.services.cs.core.ConfigManager
import csw.services.cs.core.git.{GitConfigManager, JGitConfigManager}

/**
 * Utility class to create temporary Git repositories for use in testing.
 */
object TestGitRepo {

  private def resetRepo(settings: ConfigServiceSettings)(implicit context: ActorRefFactory): Unit = {
    // XXX FIXME TODO: Use generated temp dirs, not settings
    println(s"Local repo = ${settings.gitLocalRepository}, remote = ${settings.gitMainRepository}")
    if (settings.gitMainRepository.getScheme != "file")
      throw new RuntimeException(s"Please specify a file URI for csw.services.cs.main-repository for testing")

    val gitMainRepo = new File(settings.gitMainRepository.getPath)
    // Delete the main and local test repositories (Only use this in test cases!)
    GitConfigManager.deleteDirectoryRecursively(gitMainRepo)
    GitConfigManager.initBareRepo(gitMainRepo)
    GitConfigManager.deleteDirectoryRecursively(settings.gitLocalRepository)
  }

  /**
   * Creates a temporary test Git repository and a bare main repository for push/pull.
   * Any previous contents are deleted.
   *
   * @return a new ConfigManager set to manage the newly created Git repositories
   */
  def getConfigManager(settings: ConfigServiceSettings = ConfigServiceSettings(ActorSystem()))(implicit context: ActorRefFactory): ConfigManager = {
    resetRepo(settings)
    GitConfigManager(settings.gitLocalRepository, settings.gitMainRepository, settings.name)
  }

  /**
   * Java API: Creates a temporary test Git repository and a bare main repository for push/pull.
   * Any previous contents are deleted.
   *
   * @return a new ConfigManager set to manage the newly created Git repositories
   */
  def getJConfigManager: JConfigManager = {
    implicit val system = ActorSystem()
    val settings = ConfigServiceSettings(system)
    resetRepo(settings)
    JGitConfigManager(settings.gitLocalRepository, settings.gitMainRepository)
  }
}
