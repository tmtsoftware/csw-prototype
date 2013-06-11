package org.tmt.csw.test.app

import akka.actor._
import org.tmt.csw.cs.akka.ConfigServiceActor
import org.tmt.csw.cs.core.git.GitConfigManager
import java.io.File


/**
 * Manages access to singleton actors
 */
object ActorFactory {
  val system = ActorSystem("TestApp")
  val settings = Settings(system)
//  val configServiceActor = system.actorOf(Props(ConfigServiceActor()), name = "configService")
  val configServiceActor = system.actorOf(Props(makeTestConfigServiceActor()), name = "configService")

  // Make sure the main test Git repository exists (only needed for tests)
  private def makeTestConfigServiceActor() : ConfigServiceActor = {
    val mainRepoDir = new File(settings.testMainRepository)

    GitConfigManager.deleteLocalRepo(mainRepoDir)
    GitConfigManager.initBareRepo(mainRepoDir)
    GitConfigManager.deleteLocalRepo(settings.testLocalRepository)

    if (!mainRepoDir.isDirectory) {
      GitConfigManager.initBareRepo(mainRepoDir)
    }

    ConfigServiceActor(settings.testLocalRepository, settings.testMainRepository)
  }
}
