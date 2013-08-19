package org.tmt.csw.cs.core.git

import org.scalatest.FunSuite
import java.io.{FileNotFoundException, IOException, File}
import java.util.Date
import org.tmt.csw.cs.core.ConfigString

/**
 * Tests the GitConfigManager class
 */
class TestGitConfigManager extends FunSuite {
  val path1 = new File("some/test1/TestConfig1")
  val path2 = new File("some/test2/TestConfig2")

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  val startTime = new Date().getTime

  test("Test creating a GitConfigManager, storing and retrieving some files") {
    val manager = TestRepo.getConfigManager
    if (manager.exists(path1)) {
      manager.delete(path1)
    }
    if (manager.exists(path2)) {
      manager.delete(path2)
    }

    // Should get exception if we try to delete a file that does not exist
    intercept[FileNotFoundException] {
      manager.delete(path1)
    }
    intercept[FileNotFoundException] {
      // Should get exception if we try to delete a file that does not exist
      manager.delete(path2)
    }

    // Should throw exception if we try to update a file that does not exist
    intercept[IOException] {
      manager.update(path1, new ConfigString(contents2), comment2)
    }
    intercept[IOException] {
      manager.update(path1, new ConfigString(contents3), comment3)
    }

    // Add, then update the file twice
    val createId1 = manager.create(path1, new ConfigString(contents1), comment1)
    val createId2 = manager.create(path2, new ConfigString(contents1), comment1)
    val updateId1 = manager.update(path1, new ConfigString(contents2), comment2)
    val updateId2 = manager.update(path1, new ConfigString(contents3), comment3)

    // Should throw exception if we try to create a file that already exists
    intercept[IOException] {
      manager.create(path1, new ConfigString(contents2), comment2)
    }
    intercept[IOException] {
      manager.create(path2, new ConfigString(contents2), comment2)
    }

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

    // test history()
    val historyList1 = manager.history(path1)
    val historyList2 = manager.history(path2)

    assert(historyList1.size >= 3)
    assert(historyList2.size >= 1)

    assert(historyList1(0).comment == comment1)
    assert(historyList2(0).comment == comment1)
    assert(historyList1(1).comment == comment2)
    assert(historyList1(2).comment == comment3)

    // Test list()
    val list = manager.list()
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

    // Test getting history of document that has been deleted
    manager.delete(path1, "test delete")
    assert(!manager.exists(path1))
    manager.delete(path2)
    assert(!manager.exists(path2))
    val historyList1d = manager.history(path1)
    val historyList2d = manager.history(path2)

    assert(historyList1d.size >= 3)
    assert(historyList2d.size >= 1)

    assert(historyList1d(0).comment == comment1)
    assert(historyList2d(0).comment == comment1)
    assert(historyList1d(1).comment == comment2)
    assert(historyList1d(2).comment == comment3)

  }
}
