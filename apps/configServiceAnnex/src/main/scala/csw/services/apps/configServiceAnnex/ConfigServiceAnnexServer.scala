package csw.services.apps.configServiceAnnex

import java.io.{ File, FileOutputStream }
import java.net.{ InetSocketAddress, URI }
import java.nio.file.{ Files, Path, Paths }

import akka.http.Http
import akka.http.model.HttpEntity.ChunkStreamPart
import akka.stream.scaladsl.{ ForeachSink, Source }
import akka.util.ByteString
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.ls.LocationServiceActor.{ ServiceType, ServiceId }
import csw.services.ls.LocationServiceRegisterActor
import org.slf4j.LoggerFactory
import akka.actor.ActorSystem
import akka.http.model._
import akka.stream.FlowMaterializer

import scala.concurrent.{ Promise, Future }
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

case class ConfigServiceAnnexServer(registerWithLoc: Boolean = false) {
  val logger = Logger(LoggerFactory.getLogger("ConfigServiceAnnexServer"))
  logger.info("Config service annex started")
  implicit val system = ActorSystem("ConfigServiceAnnexServer")

  import system.dispatcher
  import akka.http.model.HttpMethods._

  val settings = ConfigServiceAnnexSettings(system)
  if (!settings.dir.exists()) settings.dir.mkdirs()

  implicit val materializer = FlowMaterializer()

  val binding = Http().bind(interface = settings.interface, port = settings.port)
  binding.connections.foreach { c ⇒
    logger.info(s"Accepted new connection from ${c.remoteAddress}")
    c.handleWithAsyncHandler {
      case HttpRequest(GET, uri, _, _, _)       ⇒ httpGet(uri)
      case HttpRequest(POST, uri, _, entity, _) ⇒ httpPost(uri, entity)
      case HttpRequest(HEAD, uri, _, _, _)      ⇒ httpHead(uri)
      case HttpRequest(DELETE, uri, _, _, _)    ⇒ httpDelete(uri)
      case _: HttpRequest                       ⇒ Future.successful(HttpResponse(StatusCodes.NotFound, entity = "Unknown resource!"))
    }
  }

  //  binding.localAddress()
  if (registerWithLoc) {
    // XXX TODO FIXME
    val localAddress = InetSocketAddress.createUnresolved(settings.interface, settings.port)
    registerWithLocationService(localAddress)
  }

  /**
   * Register with the location service (which must be started as a separate process).
   */
  def registerWithLocationService(addr: InetSocketAddress) {
    val serviceId = ServiceId("ConfigServiceAnnex", ServiceType.Service)
    val httpUri = new URI(s"http://${addr.getHostString}:${addr.getPort}/")
    logger.info(s"Registering with the location service with URI $httpUri")
    // Start an actor to re-register when the location service restarts
    system.actorOf(LocationServiceRegisterActor.props(serviceId, actorRef = None, configPath = None, httpUri = Some(httpUri)))
  }

  // Implements Http GET
  private def httpGet(uri: Uri): Future[HttpResponse] = {
    val path = makePath(settings.dir, new File(uri.path.toString()))
    logger.info(s"Received GET request for $path (uri = $uri)")
    val result = Try {
      val mappedByteBuffer = FileUtils.mmap(path)
      val iterator = new FileUtils.ByteBufferIterator(mappedByteBuffer, settings.chunkSize)
      val chunks = Source(() ⇒ iterator).map(ChunkStreamPart.apply)
      HttpResponse(entity = HttpEntity.Chunked(MediaTypes.`application/octet-stream`, chunks))
    } recover {
      case NonFatal(cause) ⇒
        logger.error(s"Nonfatal error while attempting to get $path (uri = $uri): cause = ${cause.getMessage}")
        HttpResponse(StatusCodes.InternalServerError, entity = cause.getMessage)
      case ex ⇒
        logger.error(s"Error while attempting to get $path (uri = $uri): cause = ${ex.getMessage}", ex)
        HttpResponse(StatusCodes.InternalServerError, entity = ex.getMessage)
    }
    Future.successful(result.get)
  }

  // Implements Http POST
  private def httpPost(uri: Uri, entity: RequestEntity): Future[HttpResponse] = {
    val uriPath = uri.path.toString()
    val id = new File(uriPath).getName
    val path = makePath(settings.dir, new File(uriPath))
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
      // XXX TODO: Use a for comprehension here instead?
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
    val result = if (path.toFile.exists()) {
      logger.info(s"Received HEAD request for $path (uri = $uri) (exists)")
      HttpResponse(StatusCodes.OK)
    } else {
      logger.info(s"Received HEAD request for $path (uri = $uri) (not found)")
      HttpResponse(StatusCodes.NotFound)
    }
    Future.successful(result)
  }

  // Implements Http DELETE: Returns OK if the file could be deleted or did not exist.
  // Note: In normal operation files should probably never be deleted. This is for testing use.
  private def httpDelete(uri: Uri): Future[HttpResponse] = {
    val path = makePath(settings.dir, new File(uri.path.toString()))
    logger.info(s"Received DELETE request for $path (uri = $uri)")
    val result = Try { if (path.toFile.exists()) Files.delete(path) } match {
      case Success(_) ⇒
        HttpResponse(StatusCodes.OK)
      case Failure(ex) ⇒
        logger.error(s"Failed to delete file $path")
        HttpResponse(StatusCodes.InternalServerError, entity = ex.getMessage)
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