package org.tmt.csw.cs.git

import org.scalatest.FunSuite
import java.io.File
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.tmt.csw.cs.ConfigString

/**
 * Tests the GitConfigManager class
 */
class TestGitConfigManager2 extends FunSuite {

  test("Test listing and modifying files") {
    val tmpDir = System.getProperty("java.io.tmpdir")
    val gitDir = new File(tmpDir, "testGitConfigManager")
    println("Test repo = " + gitDir)

    // create a new repo
    val manager = GitConfigManager(gitDir, true)

    // Test list
    val list = manager.list
    list.foreach {
      case (id, path) => println("XXX id = " + id + ", path = " + path)
    }
  }
}
