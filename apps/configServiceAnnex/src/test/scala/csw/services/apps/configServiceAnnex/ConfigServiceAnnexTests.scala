package csw.services.apps.configServiceAnnex

import java.io.RandomAccessFile
import java.nio.file.Files
import java.io.File

import net.codejava.security.HashGeneratorUtils

import scala.util.{ Failure, Success }
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Tests uploading and downloading files
 */
object ConfigServiceAnnexTests extends App {

  // Start the http server and wait for it to be ready for connections
  ConfigServiceAnnexServer.startup().onComplete {
    case Success(server) ⇒ runTest(server)
    case Failure(ex)     ⇒ println(s"Error starting server: $ex")
  }

  def runTest(server: ConfigServiceAnnexServer): Unit = {
    // client instance
    val client = ConfigServiceAnnexClient

    val file = makeTestFile()

    for {
      id ← client.post(file)
      exists1 ← client.head(id)
      file1 ← client.get(id, file)
      delete1 ← client.delete(id)
      exists2 ← client.head(id)
    } try {
      assert(id == HashGeneratorUtils.generateSHA1(file))
      assert(exists1)
      assert(file1 == file)
      assert(delete1)
      assert(!exists2)
      println("Test Passed")
    } finally done()

    def error(msg: String): Unit = {
      println(msg)
      done()
    }

    // Called when done to clean up and shutdown the http server and client
    def done(): Unit = {
      server.shutdown()
      client.shutdown()
      file.delete()
      System.exit(0)
    }
  }

  // Create a file for testing
  def makeTestFile(): File = {
    val file = Files.createTempFile(null, null).toFile
    val raf = new RandomAccessFile(file, "rw")
    raf.setLength(1048576)
    raf.close()
    file
  }
}
