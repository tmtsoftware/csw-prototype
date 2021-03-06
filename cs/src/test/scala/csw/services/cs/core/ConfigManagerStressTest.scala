package csw.services.cs.core

import java.io.File

import akka.actor.ActorSystem
import csw.services.cs.akka.{TestRepo, ConfigServiceSettings, TestGitRepo, TestSvnRepo}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Tests performance accessing earlier revisions of files after many commits
 *
 * Note: you might want to set the logging level to ERROR to avoid too much output.
 * This can be done in the src/main/resources/logback.xml file in the log package at:
 * logger name="csw" level="DEBUG"
 */
object ConfigManagerStressTest extends App {

  // create a test git or svn repository and use it to create the manager
  implicit val system = ActorSystem()

  //  import system.dispatcher

  val settings = ConfigServiceSettings(system)
  val manager = TestRepo.getTestRepoConfigManager(settings)
  val vcs = if (settings.useSvn) "svn" else "git"

  val (_, totalTime) = runTest().elapsed()
  println(s"Total time using $vcs: $totalTime sec")
  system.terminate()
  System.exit(0)

  def runTest(): Unit = {
    // number of files
    val nf = 10

    // number of times to commit updates to each file
    val nt = 10

    // If of first check in
    var first = ConfigId(-1)

    for (t <- 1 to nt) {
      for (f <- 1 to nf) {
        val fname = s"$f.txt"
        val configData = ConfigData(s"hello$t")
        val configId = if (nf == f) {
          val (result, time) = Await.result(manager.createOrUpdate(new File(fname), configData), 1.second).elapsed()
          println(s"Time to checkin commit #$t: $time sec")
          result
        } else {
          Await.result(manager.createOrUpdate(new File(fname), configData), 1.second)
        }
        if (t == 1 && f == 1) {
          first = configId
        } else if (t == nt && f == nf) {
          val last = configId
          printResults(first, last)
        }
      }
    }
  }

  // Used to time operations.
  implicit class RichElapsed[A](f: => A) {
    // Returns (result, timeInSeconds)
    def elapsed(): (A, Double) = {
      val start = System.nanoTime()
      val res = f
      val end = System.nanoTime()
      (res, (end - start) / 1e9)
    }
  }

  def printResults(first: ConfigId, last: ConfigId): Unit = {
    val (_, time1) = manager.get(new File("1.txt"), Some(first)).elapsed()
    println(s"Time to checkout first commit: $time1 sec")

    val (_, time2) = manager.get(new File("1.txt"), Some(last)).elapsed()
    println(s"Time to checkout last commit by sha1 ID rather than HEAD: $time2 sec")
  }
}
