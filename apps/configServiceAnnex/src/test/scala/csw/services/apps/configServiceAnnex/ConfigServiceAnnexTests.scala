package csw.services.apps.configServiceAnnex

import java.io.RandomAccessFile
import java.nio.file.Files
import java.io.File

import net.codejava.security.HashGeneratorUtils

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Tests uploading and downloading files
 */
object ConfigServiceAnnexTests extends App {

  // Create a file for testing
  val file = Files.createTempFile(null, null).toFile
  val raf = new RandomAccessFile(file, "rw")
  raf.setLength(1048576)
  raf.close()

  val id = HashGeneratorUtils.generateSHA1(file)

  // Upload the file
  ConfigServiceAnnexClient.post(file).onComplete {
    case Success(f) =>
      println(s"File $f uploaded successfully with id $id")
      file.delete()
      // Download the file again
      ConfigServiceAnnexClient.get(id, file).onComplete {
        case Success(ff) =>
          assert(FileUtils.validate(id, file))
          println(s"File $ff downloaded successfully with id $id")
          done(file)
        case Failure(ex) =>
          println(s"Error downloading id $id to $file: $ex")
          done(file)
      }
    case Failure(ex) =>
      println(s"Error uploading $file: $ex")
      done(file)
  }

  def done(file: File): Unit = {
    file.delete()
    System.exit(0)
  }
}
