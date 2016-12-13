package csw.services.cs.core

import java.io.File
import java.util.Date

import akka.actor.ActorSystem
import org.scalatest.FunSuite

import scala.concurrent.{Await, Future}
import scala.util.Try
import scala.concurrent.duration._

/**
 * Common test code for classes that implement the ConfigManager trait
 */
object ConfigManagerTestHelper extends FunSuite {
  val path1 = new File("some/test1/TestConfig1")
  val path2 = new File("some/test2/TestConfig2")

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  // Run tests using the given config manager instance
  def runTests(cm: ConfigManager, oversize: Boolean)(implicit system: ActorSystem): Unit = {
    val manager = BlockingConfigManager(cm)
    // Add, then update the file twice
    val date1 = new Date()
    Thread.sleep(100)
    val createId1 = manager.create(path1, ConfigData(contents1), oversize, comment1)
    val createId2 = manager.createOrUpdate(path2, ConfigData(contents1), oversize, comment1)
    val date1a = new Date()
    Thread.sleep(100) // make sure date is different
    val updateId1 = manager.update(path1, ConfigData(contents2), comment2)
    val date2 = new Date()
    Thread.sleep(100) // make sure date is different
    val updateId2 = manager.createOrUpdate(path1, ConfigData(contents3), oversize, comment3)
    val date3 = new Date()

    // Check that we can access each version
    assert(manager.get(path1).get.toString == contents3)
    assert(manager.get(path1, Some(createId1)).get.toString == contents1)
    assert(manager.get(path1, Some(updateId1)).get.toString == contents2)
    assert(manager.get(path1, Some(updateId2)).get.toString == contents3)
    assert(manager.get(path2).map(_.toString).get.toString == contents1)
    assert(manager.get(path2, Some(createId2)).get.toString == contents1)

    assert(manager.get(path1, date1).get.toString == contents1)
    assert(manager.get(path1, date1a).get.toString == contents1)
    assert(manager.get(path1, date2).get.toString == contents2)
    assert(manager.get(path1, date3).get.toString == contents3)

    val historyList1 = manager.history(path1)
    assert(historyList1.size == 3)
    assert(historyList1.head.comment == comment3)
    assert(historyList1(1).comment == comment2)

    val historyList2 = manager.history(path2)
    assert(historyList2.size == 1)
    assert(historyList2.head.comment == comment1)
    assert(historyList1(2).comment == comment1)

    // Test default file features
    assert(manager.getDefault(path1).get.toString == contents3)

    manager.setDefault(path1, Some(updateId1))
    assert(manager.getDefault(path1).get.toString == contents2)

    manager.resetDefault(path1)
    assert(manager.getDefault(path1).get.toString == contents3)

    manager.setDefault(path1, Some(updateId2))

    // test list()
    val list = manager.list()
    assert(list.size == 3) // +1 for default file
    for (info <- list) {
      info.path match {
        case this.path1 => assert(info.comment == this.comment3)
        case this.path2 => assert(info.comment == this.comment1)
        case _          =>
      }
    }

    // Test delete
//    manager.delete(path1)
//    assert(manager.get(path1).isEmpty)
    // XXX TODO: Fix getting previous versions of deleted file using the svn implementation
//    assert(manager.get(path1, Some(createId1)).get.toString == contents1)
//    assert(manager.get(path1, Some(updateId1)).get.toString == contents2)
//    assert(manager.getDefault(path1).get.toString == contents3)
  }

  // Verify that a second config service can still see all the files that were checked in by the first
  def runTests2(cm: ConfigManager, oversize: Boolean)(implicit system: ActorSystem): Unit = {
    val manager = BlockingConfigManager(cm)

    // Check that we can access each version
    assert(manager.get(path1).get.toString == contents3)
    assert(manager.get(path2).get.toString == contents1)

    // test history()
    val historyList1 = manager.history(path1)
    assert(historyList1.size == 3)
    assert(historyList1.head.comment == comment3)
    assert(historyList1(1).comment == comment2)
    assert(historyList1(2).comment == comment1)

    val historyList2 = manager.history(path2)
    assert(historyList2.size == 1)
    assert(historyList2.head.comment == comment1)

    // test list()
    val list = manager.list()
    for (info <- list) {
      info.path match {
        case this.path1 => assert(info.comment == this.comment3)
        case this.path2 => assert(info.comment == this.comment1)
        case _          => // other files: README, *.default...
      }
    }

    // Should throw exception if we try to create a file that already exists
    assert(Try(manager.create(path1, ConfigData(contents2), oversize, comment2)).isFailure)
  }

  // Does some updates and gets
  private def test3(cm: ConfigManager)(implicit system: ActorSystem): Unit = {
    val manager = BlockingConfigManager(cm)
    manager.get(path1)
    manager.update(path1, ConfigData(s"${contents2}Added by ${manager.name}\n"), s"$comment1 - ${manager.name}")
    manager.get(path2)
    manager.update(path2, ConfigData(s"${contents1}Added by ${manager.name}\n"), s"$comment2 - ${manager.name}")
  }

  // Tests concurrent access to a central repository (see if there are any conflicts, etc.)
  def concurrentTest(managers: List[ConfigManager], oversize: Boolean)(implicit system: ActorSystem): Future[Unit] = {
    import system.dispatcher
    val result = Future.sequence {
      val f = for (manager <- managers) yield {
        Future(test3(manager))
      }
      // wait here, since we want to do the updates sequentially for each configManager
      f.foreach(Await.ready(_, 10.seconds))
      f
    }
    result.map(_ => ())
  }
}

