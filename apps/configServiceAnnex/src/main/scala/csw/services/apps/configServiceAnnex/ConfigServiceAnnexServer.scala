package csw.services.apps.configServiceAnnex

import java.io.{ File, FileOutputStream }
import java.nio.file.{ Files, Path, Paths }

import akka.actor.ActorSystem
import akka.http.Http
import akka.http.model.HttpEntity.ChunkStreamPart
import akka.http.model._
import akka.io.IO
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{ ForeachSink, Sink, Source }
import akka.util.ByteString
import com.typesafe.scalalogging.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.{ Promise, Future }
import scala.util.{ Failure, Success, Try }
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
object ConfigServiceAnnexServerApp extends App {
  ConfigServiceAnnexServer.startup()
}

object ConfigServiceAnnexServer {
  /**
   * Starts the server and returns a future that completes when it is ready to accept connections
   * (needed for testing)
   */
  def startup(): Future[ConfigServiceAnnexServer] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val server = new ConfigServiceAnnexServer()
    server.startup().map(_ ⇒ server)
  }
}

class ConfigServiceAnnexServer {
  val logger = Logger(LoggerFactory.getLogger("ConfigServiceAnnexServer"))
  logger.info("Config service annex started")
  implicit val system = ActorSystem("ConfigServiceAnnexServer")

  import system.dispatcher
  import akka.http.model.HttpMethods._

  val settings = ConfigServiceAnnexSettings(system)
  if (!settings.dir.exists()) settings.dir.mkdirs()

  implicit val materializer = FlowMaterializer()
  implicit val askTimeout = settings.timeout

  val requestHandler: HttpRequest ⇒ Future[HttpResponse] = {
    case HttpRequest(GET, uri, _, _, _)       ⇒ httpGet(uri)
    case HttpRequest(POST, uri, _, entity, _) ⇒ httpPost(uri, entity)
    case HttpRequest(HEAD, uri, _, _, _)      ⇒ httpHead(uri)
    case HttpRequest(DELETE, uri, _, _, _)    ⇒ httpDelete(uri)
    case _: HttpRequest                       ⇒ Future.successful(HttpResponse(StatusCodes.NotFound, entity = "Unknown resource!"))
  }

  private def startup(): Future[Any] = {
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
  }

  // Implements Http GET
  private def httpGet(uri: Uri): Future[HttpResponse] = {
    val path = makePath(settings.dir, new File(uri.path.toString()))
    logger.info(s"Received GET request for $path (uri = $uri)")
    val result = Try {
      val mappedByteBuffer = FileUtils.mmap(path)
      val iterator = new FileUtils.ByteBufferIterator(mappedByteBuffer, settings.chunkSize)
      val chunks = Source(iterator).map(ChunkStreamPart.apply)
      HttpResponse(entity = HttpEntity.Chunked(MediaTypes.`application/octet-stream`, chunks))
    } recover {
      case NonFatal(cause) ⇒
        logger.error(s"Nonfatal error: cause = ${cause.getMessage}")
        HttpResponse(StatusCodes.InternalServerError, entity = cause.getMessage)
    }
    Future.successful(result.get)
  }

  // Implements Http POST
  private def httpPost(uri: Uri, entity: RequestEntity): Future[HttpResponse] = {
    val id = new File(uri.path.toString()).getName
    val path = makePath(settings.dir, new File(uri.path.toString()))
    val file = path.toFile
    file.getParentFile.mkdirs()
    val response = Promise[HttpResponse]()
    if (file.exists) {
      logger.info(s"Ignoring POST request for existing $file (uri = $uri)")
      response.success(HttpResponse(StatusCodes.OK))
    } else {
      logger.info(s"Received POST request for $file (uri = $uri)")
      val out = new FileOutputStream(file)
      val sink = ForeachSink[ByteString] { bytes ⇒
        out.write(bytes.toArray)
      }
      val materialized = entity.getDataBytes().to(sink).run()
      // ensure the output file is closed and the system shutdown upon completion
      materialized.get(sink).onComplete {
        case Success(_) ⇒
          Try(out.close())
          if (FileUtils.validate(id, file)) {
            response.success(HttpResponse(StatusCodes.OK))
          } else {
            file.delete()
            response.success(HttpResponse(StatusCodes.InternalServerError, entity = FileUtils.validateError(id, file).getMessage))
          }
        case Failure(e) ⇒
          logger.error(s"Failed to upload $uri to $file: ${e.getMessage}")
          Try(out.close())
          file.delete()
          response.success(HttpResponse(StatusCodes.InternalServerError, entity = e.getMessage))
      }
    }
    response.future
  }

  // Implements Http HEAD: Returns OK if the file exists on the server
  private def httpHead(uri: Uri): Future[HttpResponse] = {
    val path = makePath(settings.dir, new File(uri.path.toString()))
    logger.info(s"Received HEAD request for $path (uri = $uri)")
    val result = if (path.toFile.exists())
      HttpResponse(StatusCodes.OK)
    else
      HttpResponse(StatusCodes.NotFound)
    Future.successful(result)
  }

  // Implements Http DELETE: Returns OK if the file could be deleted or did not exist.
  // Note: In normal operation files should probably never be deleted. This is for testing use.
  private def httpDelete(uri: Uri): Future[HttpResponse] = {
    val path = makePath(settings.dir, new File(uri.path.toString()))
    logger.info(s"Received DELETE request for $path (uri = $uri)")
    val result = Try { if (path.toFile.exists()) Files.delete(path) } match {
      case Success(_)  ⇒ HttpResponse(StatusCodes.OK)
      case Failure(ex) ⇒ HttpResponse(StatusCodes.InternalServerError, entity = ex.getMessage)
    }
    Future.successful(result)
  }

  /**
   * Shuts down the server
   */
  def shutdown(): Unit = system.shutdown()

  // Returns the name of the file to use in the configured directory.
  // Like Git, distribute the files in directories based on the first 2 chars of the SHA-1 hash
  private def makePath(dir: File, file: File): Path = {
    val (subdir, name) = file.getName.splitAt(2)
    Paths.get(dir.getPath, subdir, name)
  }
}