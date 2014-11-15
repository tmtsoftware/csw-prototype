package csw.services.cs.akka

import java.io.File

import akka.actor.ActorSystem
import csw.services.cs.JConfigManager
import csw.services.cs.core.ConfigManager
import csw.services.cs.core.git.{GitConfigManager, JGitConfigManager}

/**
 * Utility class to create temporary Git repositories for use in testing.
 */
object TestRepo {

  private def resetRepo(settings: Settings): Unit = {
    println(s"Local repo = ${settings.gitLocalRepository}, remote = ${settings.gitMainRepository}, oversize files: ${settings.gitOversizeStorage}")
    if (settings.gitMainRepository.getScheme != "file")
      throw new RuntimeException(s"Please specify a file URI for csw.cs.git-main-repository for testing")

    val gitMainRepo = new File(settings.gitMainRepository.getPath)
    // Delete the main and local test repositories (Only use this in test cases!)
    GitConfigManager.deleteDirectoryRecursively(gitMainRepo)
    GitConfigManager.initBareRepo(gitMainRepo)
    GitConfigManager.deleteDirectoryRecursively(settings.gitLocalRepository)

    if (settings.gitOversizeStorage.getScheme == "file") {
      val dir = new File(settings.gitOversizeStorage.getPath)
      GitConfigManager.deleteDirectoryRecursively(dir)
      dir.mkdirs()
    }
  }

  /**
   * Creates a temporary test Git repository and a bare main repository for push/pull.
   * Any previous contents are deleted.
   *
   * @return a new ConfigManager set to manage the newly created Git repositories
   */
  def getConfigManager(settings: Settings = Settings(ActorSystem())): ConfigManager = {
    resetRepo(settings)
    GitConfigManager(settings.gitLocalRepository, settings.gitMainRepository, settings.gitOversizeStorage)
  }

  /**
   * Java API: Creates a temporary test Git repository and a bare main repository for push/pull.
   * Any previous contents are deleted.
   *
   * @return a new ConfigManager set to manage the newly created Git repositories
   */
  def getJConfigManager: JConfigManager = {
    val settings = Settings(ActorSystem())
    resetRepo(settings)
    JGitConfigManager(settings.gitLocalRepository, settings.gitMainRepository, settings.gitOversizeStorage)
  }

  /**
   * Creates a temporary test Git repository and a bare main repository for push/pull.
   * Any previous contents are deleted.
   * @param prefix prefix for temp repos created
   * @param create if true, create temporary git main and local repos for testing
   * @param system the actor system
   *
   * @return a new non-blocking config manager set to manage the newly created Git repositories
   */
  def getNonBlockingConfigManager(prefix: String, create: Boolean, system: ActorSystem): NonBlockingConfigManager = {
    NonBlockingConfigManager(getConfigManager(Settings(system)))(system.dispatcher)
  }
}
