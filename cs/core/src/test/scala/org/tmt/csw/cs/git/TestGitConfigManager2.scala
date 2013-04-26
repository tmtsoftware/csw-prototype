package org.tmt.csw.cs.git

import org.scalatest.FunSuite
import java.io.File
import org.tmt.csw.cs.{ConfigFileInfo, ConfigString}

/**
 * Tests the GitConfigManager class
 */
class TestGitConfigManager2 extends FunSuite {

  test("Test listing and modifying files") {
    val tmpDir = System.getProperty("java.io.tmpdir")
    val gitDir = new File(tmpDir, "testGitConfigManager")
    println("Test repo = " + gitDir)

    // create a new repo
    val manager = GitConfigManager(gitDir)

    // Test list
    val list = manager.list
    for(info <- list) {
      println("XXX path = " + info.path + ", id = " + info.id)
      val configData1 = manager.get(info.path, Some(info.id)).get
      println("    XXX contents1 = " + configData1.toString)
      val updateId = manager.put(info.path, new ConfigString(configData1.toString + " XXX"), "Added XXX")
      println("    XXX updateId = " + updateId)


      // Test history
      val history = manager.history(info.path)
      for(h <- history) {
        println("    XXX version from " + h.time + " with id " + h.id + " and comment " + h.comment)
        val configData2 = manager.get(info.path, Some(h.id)).get
        println("    XXX contents2 = " + configData2.toString)
      }
    }
  }
}
