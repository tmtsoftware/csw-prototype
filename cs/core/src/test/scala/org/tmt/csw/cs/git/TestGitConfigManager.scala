package org.tmt.csw.cs.git

import org.scalatest.FunSuite
import java.io.File
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.tmt.csw.cs.ConfigString

/**
 *
 */
class TestGitConfigManager extends FunSuite {

  test("Test creating a GitConfigManager, storing and retrieving a file") {
    val tmpDir = System.getProperty("java.io.tmpdir")
    val gitDir = new File(tmpDir, "testGitConfigManager")
    println("Test repo = " + gitDir)
    GitConfigManager.delete(gitDir)

    // doesn't exist yet, should throw exception
    intercept[RepositoryNotFoundException] {
      GitConfigManager(gitDir)
    }

    // create a new repo
    val manager = GitConfigManager(gitDir, true)
    val path = "some/test/TestConfig"

    val contents1 = "Contents of some file...\n"
    val contents2 = "New contents of some file...\n"
    val contents3 = "Even newer contents of some file...\n"

    val comment1 = "create comment\n"
    val comment2 = "update 1 comment\n"
    val comment3 = "update 2 comment\n"

    // Add, then update the file twice
    val createId = manager.put(path, new ConfigString(contents1), comment1)
    val updateId1 = manager.put(path, new ConfigString(contents2), comment2)
    val updateId2 = manager.put(path, new ConfigString(contents3), comment3)

    // Check that we can access each version
    val option1 = manager.get(path)
    assert(!option1.isEmpty)
    assert(option1.get.toString == contents3)

    val option2 = manager.get(path, Some(createId))
    assert(!option2.isEmpty)
    assert(option2.get.toString == contents1)

    val option3 = manager.get(path, Some(updateId1))
    assert(!option3.isEmpty)
    assert(option3.get.toString == contents2)

    val option4 = manager.get(path, Some(updateId2))
    assert(!option4.isEmpty)
    assert(option4.get.toString == contents3)
  }
}
