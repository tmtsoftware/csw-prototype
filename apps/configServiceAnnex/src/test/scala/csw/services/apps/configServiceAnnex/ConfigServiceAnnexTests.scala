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

  // Start the http server and wait for it to be ready for connections
  ConfigServiceAnnexServer.startup().onComplete {
    case Success(server) => runTest(server)
    case Failure(ex) => println(s"Error starting server: $ex")
  }

  def runTest(server: ConfigServiceAnnexServer): Unit = {
    // client instance
    val client = ConfigServiceAnnexClient

    // Create a file for testing
    val file = Files.createTempFile(null, null).toFile
    val raf = new RandomAccessFile(file, "rw")
    raf.setLength(1048576)
    raf.close()

    val id = HashGeneratorUtils.generateSHA1(file)

    // Upload the file
    client.post(file).onComplete {
      case Success(f) =>
        println(s"File $f uploaded successfully with id $id")
        file.delete()
        // Download the file again
        client.get(id, file).onComplete {
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
      server.shutdown()
      client.shutdown()
      file.delete()
      System.exit(0)
    }
  }
}
