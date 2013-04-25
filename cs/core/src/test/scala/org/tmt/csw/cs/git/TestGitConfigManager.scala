package org.tmt.csw.cs.git

import org.scalatest.FunSuite
import java.io.File
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.tmt.csw.cs.ConfigString

/**
 * Tests the GitConfigManager class
 */
class TestGitConfigManager extends FunSuite {

  test("Test creating a GitConfigManager, storing and retrieving a file") {
    val tmpDir = System.getProperty("java.io.tmpdir")
    val gitDir = new File(tmpDir, "testGitConfigManager")
    println("Test repo = " + gitDir)

    GitConfigManager.delete(gitDir)

    // create a new repo
    val manager = GitConfigManager(gitDir)
    val path1 = "some/test1/TestConfig1"
    val path2 = "some/test2/TestConfig2"

    val contents1 = "Contents of some file...\n"
    val contents2 = "New contents of some file...\n"
    val contents3 = "Even newer contents of some file...\n"

    val comment1 = "create comment\n"
    val comment2 = "update 1 comment\n"
    val comment3 = "update 2 comment\n"

    // Add, then update the file twice
    val createId1 = manager.put(path1, new ConfigString(contents1), comment1)
    val createId2 = manager.put(path2, new ConfigString(contents1), comment1)
    val updateId1 = manager.put(path1, new ConfigString(contents2), comment2)
    val updateId2 = manager.put(path1, new ConfigString(contents3), comment3)

    // Check that we can access each version
    val option1 = manager.get(path1)
    assert(!option1.isEmpty)
    assert(option1.get.toString == contents3)

    val option2 = manager.get(path1, Some(createId1))
    assert(!option2.isEmpty)
    assert(option2.get.toString == contents1)

    val option3 = manager.get(path1, Some(updateId1))
    assert(!option3.isEmpty)
    assert(option3.get.toString == contents2)

    val option4 = manager.get(path1, Some(updateId2))
    assert(!option4.isEmpty)
    assert(option4.get.toString == contents3)

    val option5 = manager.get(path2)
    assert(!option5.isEmpty)
    assert(option5.get.toString == contents1)

    val option6 = manager.get(path2, Some(createId2))
    assert(!option6.isEmpty)
    assert(option6.get.toString == contents1)
  }
}
