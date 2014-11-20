package csw.services.apps.configServiceAnnex

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.{ Paths, Path, StandardOpenOption }
import java.nio.{ ByteBuffer, MappedByteBuffer }

import akka.actor.ActorSystem
import akka.http.Http
import akka.http.model.HttpEntity.ChunkStreamPart
import akka.http.model._
import akka.io.IO
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.{ ByteString, Timeout }
import com.typesafe.scalalogging.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

/**
 * An HTTP server application that allows storing and retrieving files.
 * This is intended to be used by the config service to store large/binary
 * files.
 *
 * The URL format is http://host:port/filename, where filename is the SHA-1
 * hash of the file's contents.
 *
 * To upload a file, use POST, to download, use GET.
 * Use HEAD to check if a file already exists, DELETE to delete.
 * Files are immutable. (TODO: Should delete be available?)
 */
object ConfigServiceAnnexServer extends App {
  val logger = Logger(LoggerFactory.getLogger("ConfigServiceAnnexServer"))
  logger.info("Config service annex started")
  implicit val system = ActorSystem("ConfigServiceAnnexServer")

  import system.dispatcher

  val settings = ConfigServiceAnnexSettings(system)
  if (!settings.dir.exists()) settings.dir.mkdirs()

  implicit val materializer = FlowMaterializer()
  implicit val askTimeout: Timeout = 5000.millis

  import akka.http.model.HttpMethods._

  val requestHandler: HttpRequest ⇒ HttpResponse = {
    case HttpRequest(GET, uri, headers, _, _) ⇒
      val path = makePath(settings.dir, new File(uri.path.toString()))
      logger.info(s"Received request for $path (uri = $uri)")
      val result = Try {
        val mappedByteBuffer = mmap(path)
        val iterator = new ByteBufferIterator(mappedByteBuffer, 4096)
        val chunks = Source(iterator).map(ChunkStreamPart.apply)
        HttpResponse(entity = HttpEntity.Chunked(MediaTypes.`application/octet-stream`, chunks))
      } recover {
        case NonFatal(cause) ⇒
          logger.error(s"Nonfatal error: cause = ${cause.getMessage}")
          HttpResponse(StatusCodes.InternalServerError, entity = cause.getMessage)
      }
      result.get
    case _: HttpRequest ⇒ HttpResponse(StatusCodes.NotFound, entity = "Unknown resource!")
  }

  val bindingFuture = IO(Http) ? Http.Bind(interface = settings.interface, port = settings.port)
  bindingFuture foreach {
    case Http.ServerBinding(localAddress, connectionStream) ⇒
      logger.info(s"Listening on http:/$localAddress/")
      Source(connectionStream).foreach {
        case Http.IncomingConnection(remoteAddress, requestProducer, responseConsumer) ⇒
          logger.info(s"Accepted new connection from $remoteAddress")
          Source(requestProducer).map(requestHandler).to(Sink(responseConsumer)).run()
        case _ ⇒ logger.error("XXX 1")
      }
    case _ ⇒ logger.error("XXX 2")
  }

  bindingFuture.recover {
    case ex ⇒
      logger.error(ex.getMessage)
      system.shutdown()
  }

  def mmap(path: Path): MappedByteBuffer = {
    val channel = FileChannel.open(path, StandardOpenOption.READ)
    val result = channel.map(FileChannel.MapMode.READ_ONLY, 0L, channel.size())
    channel.close()
    result
  }

  class ByteBufferIterator(buffer: ByteBuffer, chunkSize: Int) extends Iterator[ByteString] {
    require(buffer.isReadOnly)
    require(chunkSize > 0)

    override def hasNext = buffer.hasRemaining

    override def next(): ByteString = {
      val size = chunkSize min buffer.remaining()
      val temp = buffer.slice()
      temp.limit(size)
      buffer.position(buffer.position() + size)
      ByteString(temp)
    }
  }

  // Returns the name of the file to use in the configured directory
  // XXX TODO: Split into subdirs based on parts of file name for better performance
  def makePath(dir: File, file: File): Path = {
    logger.info(s"XXX makePath $dir , $file")
    Paths.get(dir.getPath, file.getName)
  }
}