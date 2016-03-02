package csw.services.cs.akka

import akka.actor.{ActorRefFactory, ActorSystem}
import csw.services.cs.core.ConfigManager

/**
  * Utility class to create temporary Svn repositories for use in testing.
  */
class TestSvnRepo {

  private def resetRepo(settings: ConfigServiceSettings)(implicit context: ActorRefFactory): Unit = {
//    // XXX FIXME TODO: Use generated temp dirs, not settings
//    println(s"Local repo = ${settings.svnLocalRepository}, remote = ${settings.svnMainRepository}")
//    if (settings.svnMainRepository.getScheme != "file")
//      throw new RuntimeException(s"Please specify a file URI for csw.services.cs.main-repository for testing")
//
//    val svnMainRepo = new File(settings.svnMainRepository.getPath)
//    // Delete the main and local test repositories (Only use this in test cases!)
//    SvnConfigManager.deleteDirectoryRecursively(svnMainRepo)
//    SvnConfigManager.initBareRepo(svnMainRepo)
//    SvnConfigManager.deleteDirectoryRecursively(settings.svnLocalRepository)
  }

  /**
    * Creates a temporary test Svn repository and a bare main repository for push/pull.
    * Any previous contents are deleted.
    *
    * @return a new ConfigManager set to manage the newly created Svn repositories
    */
  def getConfigManager(settings: ConfigServiceSettings = ConfigServiceSettings(ActorSystem()))(implicit context: ActorRefFactory): ConfigManager = {
//    resetRepo(settings)
//    SvnConfigManager(settings.svnLocalRepository, settings.svnMainRepository, settings.name)
  }

//  /**
//    * Java API: Creates a temporary test Svn repository and a bare main repository for push/pull.
//    * Any previous contents are deleted.
//    *
//    * @return a new ConfigManager set to manage the newly created Svn repositories
//    */
//  def getJConfigManager: JConfigManager = {
//    implicit val system = ActorSystem()
//    val settings = ConfigServiceSettings(system)
//    resetRepo(settings)
//    JSvnConfigManager(settings.svnLocalRepository, settings.svnMainRepository)
//  }
}
