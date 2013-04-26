package org.tmt.csw.cs.git

import org.scalatest.FunSuite
import java.io.File
import org.tmt.csw.cs.{ConfigFileInfo, ConfigString}

/**
 * Tests the GitConfigManager class
 */
class TestGitConfigManager extends FunSuite {
  val path1 = "some/test1/TestConfig1"
  val path2 = "some/test2/TestConfig2"

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  test("Test creating a GitConfigManager, storing and retrieving some files") {
    val tmpDir = System.getProperty("java.io.tmpdir")
    val gitDir = new File(tmpDir, "testGitConfigManager")
    println("Test repo = " + gitDir)

    GitConfigManager.delete(gitDir)

    // create a new repo
    val manager = GitConfigManager(gitDir)

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


    // Test list()
    val list = manager.list
    assert(list.size == 2)
    for (info <- list) {
      info.path match {
        case this.path1 => {
          assert(info.comment == this.comment3)
        }
        case this.path2 => {
          assert(info.comment == this.comment1)
        }
        case _ => sys.error("Test failed for " + info)
      }
    }


    // test history()
    val historyList1 = manager.history(path1)
    val historyList2 = manager.history(path2)
    assert(historyList1.size == 3)
    assert(historyList2.size == 1)
    assert(historyList1(0).comment == comment1)
    assert(historyList2(0).comment == comment1)
    assert(historyList1(1).comment == comment2)
    assert(historyList1(2).comment == comment3)
  }
}
