package csw.services.apps.configServiceAnnex

import java.io.{ File, RandomAccessFile }
import java.nio.file.Files

import com.typesafe.scalalogging.slf4j.LazyLogging
import net.codejava.security.HashGeneratorUtils
import org.scalatest.FunSuite

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * Tests uploading and downloading files
 */
class ConfigServiceAnnexTests extends FunSuite with LazyLogging {

  test("Test config service annex") {
    val server = ConfigServiceAnnexServer()
    val client = ConfigServiceAnnexClient

    val file = makeTestFile()

    val x = for {
      id ← client.post(file)
      exists1 ← client.head(id)
      file1 ← client.get(id, file)
      delete1 ← client.delete(id)
      exists2 ← client.head(id)
    } yield {
      assert(id == HashGeneratorUtils.generateSHA1(file))
      assert(exists1)
      assert(file1 == file)
      assert(delete1)
      assert(!exists2)
      println("All Tests Passed")
      server.shutdown()
      client.shutdown()
      file.delete()
    }
    Await.result(x, 30.seconds)
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
