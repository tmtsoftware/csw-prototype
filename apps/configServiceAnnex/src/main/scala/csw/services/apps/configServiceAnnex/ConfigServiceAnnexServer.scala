package csw.services.apps.configServiceAnnex

import java.io.{File, FileOutputStream}
import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import akka.http.Http
import akka.http.model.HttpEntity.ChunkStreamPart
import akka.http.model._
import akka.io.IO
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{ForeachSink, Sink, Source}
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.{Promise, Future, Await}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
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

  val requestHandler: HttpRequest ⇒ Future[HttpResponse] = {
    case HttpRequest(GET, uri, headers, _, _) ⇒
      val path = makePath(settings.dir, new File(uri.path.toString()))
      logger.info(s"Received GET request for $path (uri = $uri)")
      val result = Try {
        val mappedByteBuffer = FileUtils.mmap(path)
        val iterator = new FileUtils.ByteBufferIterator(mappedByteBuffer, 4096)
        val chunks = Source(iterator).map(ChunkStreamPart.apply)
        HttpResponse(entity = HttpEntity.Chunked(MediaTypes.`application/octet-stream`, chunks))
      } recover {
        case NonFatal(cause) ⇒
          logger.error(s"Nonfatal error: cause = ${cause.getMessage}")
          HttpResponse(StatusCodes.InternalServerError, entity = cause.getMessage)
      }
      Future.successful(result.get)

    case HttpRequest(POST, uri, headers, entity, _) ⇒
      val id = new File(uri.path.toString()).getName
      val file = new File(settings.dir, id)
      logger.info(s"Received POST request for $file (uri = $uri)")
      val out = new FileOutputStream(file)
      val sink = ForeachSink[ByteString] { bytes ⇒
        out.write(bytes.toArray)
      }
      val materialized = entity.getDataBytes().to(sink).run()
      val response = Promise[HttpResponse]()
      // ensure the output file is closed and the system shutdown upon completion
      materialized.get(sink).onComplete {
        case Success(_) ⇒
          Try(out.close())
          if (FileUtils.validate(id, file))
            response.success(HttpResponse(StatusCodes.OK))
          else
            response.success(HttpResponse(StatusCodes.InternalServerError, entity = FileUtils.validateError(id, file).getMessage))
        case Failure(e) ⇒
          logger.error(s"Failed to upload $uri to $file: ${e.getMessage}")
          Try(out.close())
          response.success(HttpResponse(StatusCodes.InternalServerError, entity = e.getMessage))
      }
      response.future

    case _: HttpRequest ⇒ Future.successful(HttpResponse(StatusCodes.NotFound, entity = "Unknown resource!"))
  }

  val bindingFuture = IO(Http) ? Http.Bind(interface = settings.interface, port = settings.port)
  bindingFuture foreach {
    case Http.ServerBinding(localAddress, connectionStream) ⇒
      logger.info(s"Listening on http:/$localAddress/")
      Source(connectionStream).foreach {
        case Http.IncomingConnection(remoteAddress, requestProducer, responseConsumer) ⇒
          logger.info(s"Accepted new connection from $remoteAddress")
          Source(requestProducer).mapAsync(requestHandler).to(Sink(responseConsumer)).run()
      }
  }

  bindingFuture.recover {
    case ex ⇒
      logger.error(ex.getMessage)
      system.shutdown() // XXX TODO error handling
  }


  // Returns the name of the file to use in the configured directory
  // XXX TODO: Split into subdirs based on parts of file name for better performance
  private def makePath(dir: File, file: File): Path = {
    Paths.get(dir.getPath, file.getName)
  }
}