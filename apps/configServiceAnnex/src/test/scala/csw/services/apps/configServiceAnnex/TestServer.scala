package csw.services.apps.configServiceAnnex

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.{Path, Paths, StandardOpenOption}
import java.nio.{ByteBuffer, MappedByteBuffer}

import akka.actor.ActorSystem
import akka.http.Http
import akka.http.model.HttpEntity.ChunkStreamPart
import akka.http.model._
import akka.io.IO
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

object TestServer extends App {
  val logger = Logger(LoggerFactory.getLogger("ConfigServiceAnnexServer"))
  implicit val system = ActorSystem()

  import csw.services.apps.configServiceAnnex.TestServer.system.dispatcher

  implicit val materializer = FlowMaterializer()
  implicit val askTimeout: Timeout = 5000.millis

  val host = "localhost"
  val port = 8549

  // Serve files from this directory
  val dir = new File("/tmp")

  import akka.http.model.HttpMethods._

  val requestHandler: HttpRequest ⇒ HttpResponse = {
    case HttpRequest(GET, uri, headers, _, _) ⇒
      val path = makePath(dir, new File(uri.path.toString()))
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

  val bindingFuture = IO(Http) ? Http.Bind(host, port)
  bindingFuture foreach {
    case Http.ServerBinding(localAddress, connectionStream) ⇒
      logger.info(s"Listening on http:/$localAddress/")
      Source(connectionStream).foreach {
        case Http.IncomingConnection(remoteAddress, requestProducer, responseConsumer) ⇒
          logger.info(s"Accepted new connection from $remoteAddress")
          Source(requestProducer).map(requestHandler).to(Sink(responseConsumer)).run()
      }
  }

  bindingFuture.recover {
    case ex =>
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

  def makePath(dir: File, file: File): Path = {
    logger.info(s"XXX makePath $dir , $file")
    Paths.get(dir.getPath, file.getName)
  }
}