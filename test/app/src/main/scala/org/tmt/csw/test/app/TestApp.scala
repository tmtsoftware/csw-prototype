package org.tmt.csw.test.app

import akka.actor.{ActorSystem}
import akka.kernel.Bootable
import org.tmt.csw.cs.akka.ConfigServiceActor
import java.io.File
import org.tmt.csw.cs.core.git.GitConfigManager

case object Start


// This class is started by the Akka microkernel in standalone mode
class TestApp extends Bootable {

  val system = ActorSystem("TestApp")
  val settings = Settings(system)


  def startup() {
    makeTestGitRepository()
    val configServiceActor = system.actorOf(ConfigServiceActor.props(settings.testLocalRepository, settings.testMainRepository),
      name = "configServiceActor")
    system.actorOf(TestActor.props(configServiceActor)) ! Start
  }

  def shutdown() {
    system.shutdown()
  }

  // Make sure the main test Git repository exists and is empty (only needed for tests or first time: Should normally already exist.)
  private def makeTestGitRepository() {
    val mainRepoDir = new File(settings.testMainRepository)
    GitConfigManager.deleteLocalRepo(mainRepoDir)
    GitConfigManager.initBareRepo(mainRepoDir)
    GitConfigManager.deleteLocalRepo(settings.testLocalRepository)
    if (!mainRepoDir.isDirectory) {
      GitConfigManager.initBareRepo(mainRepoDir)
    }
  }
}


