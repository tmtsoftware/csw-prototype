package org.tmt.csw.cs.akka

import org.tmt.csw.cs.api.ConfigManager
import java.io.File
import org.tmt.csw.cs.core.git.GitConfigManager

/**
 * Utility class to create temporary Git repositories for use in testing.
 */
object TestRepo {

  /**
   * Creates a temporary test Git repository and a bare main repository for push/pull.
   * Any previous contents are deleted.
   *
   * @return a new ConfigManager set to manage the newly created Git repositories
   */
  def getConfigManager(prefix : String) : ConfigManager = {
    // Create the temporary Git repos for the test
    val tmpDir = System.getProperty("java.io.tmpdir")
    val gitDir = new File(tmpDir, prefix + "-cs-akka-test")

    // Note: could also be something like: val gitMainRepo = "git@localhost:project.git"
    // However that would require some name/password handling
    val gitMainRepo = new File(tmpDir, prefix + "-cs-akka-test-MainRepo")
    println("Local repo = " + gitDir + ", remote = " + gitMainRepo)

    // Delete the main and local test repositories (Only use this in test cases!)
    GitConfigManager.deleteLocalRepo(gitMainRepo)
    GitConfigManager.initBareRepo(gitMainRepo)
    GitConfigManager.deleteLocalRepo(gitDir)

    // create a new repository and use it to create the actor
    GitConfigManager(gitDir, gitMainRepo.toURI)
  }
}
