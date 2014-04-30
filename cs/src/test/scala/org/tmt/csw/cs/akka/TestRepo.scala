package org.tmt.csw.cs.akka

import java.io.File
import org.tmt.csw.cs.core.git.GitConfigManager
import scala.concurrent.ExecutionContextExecutor

/**
 * Utility class to create temporary Git repositories for use in testing.
 */
object TestRepo {

  /**
   * Creates a temporary test Git repository and a bare main repository for push/pull.
   * Any previous contents are deleted.
   * @param prefix prefix for temp repos created
   * @param create if true, create temporary git main and local repos for testing
   * @param dispatcher needed for the NonBlockingConfigManager (for using futures)
   *
   * @return a new non-blocking config manager set to manage the newly created Git repositories
   */
  def getConfigManager(prefix : String, create: Boolean)
                      (implicit dispatcher: ExecutionContextExecutor): NonBlockingConfigManager = {
    // Create the temporary Git repos for the test
    val tmpDir = System.getProperty("java.io.tmpdir")
    val gitDir = new File(tmpDir, prefix + "-cs-akka-test")

    // Note: could also be something like: val gitMainRepo = "git@localhost:project.git"
    // However that would require some name/password handling
    val gitMainRepo = new File(tmpDir, prefix + "-cs-akka-test-MainRepo")
    println("Local repo = " + gitDir + ", remote = " + gitMainRepo)


    if (create) {
      // Delete the main and local test repositories (Only use this in test cases!)
      GitConfigManager.deleteLocalRepo(gitMainRepo)
      GitConfigManager.initBareRepo(gitMainRepo)
      GitConfigManager.deleteLocalRepo(gitDir)
    }

    // create a new repository and use it to create the actor
    NonBlockingConfigManager(GitConfigManager(gitDir, gitMainRepo.toURI))
  }
}
