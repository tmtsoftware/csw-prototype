package csw.services.cs.akka

import java.io.File
import java.net.{InetSocketAddress, URI}
import java.util.Date

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import com.typesafe.scalalogging.Logger
import csw.services.cs.core.{ConfigData, ConfigId}
import csw.services.loc.{LocationService, ComponentType, ComponentId}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import spray.json._

import scala.util.Try

case class ConfigServiceHttpServer(configServiceActor: ActorRef, settings: ConfigServiceSettings, registerWithLoc: Boolean = false) extends ConfigServiceJsonFormats {
  val logger = Logger(LoggerFactory.getLogger(ConfigServiceHttpServer.getClass))
  logger.debug("Config service http server started")

  implicit val system = ActorSystem("ConfigServiceHttpServer")

  import system.dispatcher

  implicit val askTimeout = settings.timeout
  val client = ConfigServiceClient(configServiceActor, settings.name)
  implicit val materializer = ActorMaterializer()

  val binding = Http().bind(interface = settings.httpInterface, port = settings.httpPort)
  binding.runForeach { c =>
    logger.debug(s"${c.localAddress} accepted new connection from ${c.remoteAddress}")
    c.handleWithAsyncHandler {
      case HttpRequest(GET, uri, _, _, _)       => httpGet(uri)
      case HttpRequest(POST, uri, _, entity, _) => httpPost(uri, entity)
      case HttpRequest(PUT, uri, _, entity, _)  => httpPut(uri, entity)
      case HttpRequest(HEAD, uri, _, _, _)      => httpHead(uri)
      case HttpRequest(DELETE, uri, _, _, _)    => httpDelete(uri)
      case x: HttpRequest                       => unknownResource(x.toString)
    }
  }

  if (registerWithLoc) {
    // XXX TODO FIXME: binding.localAddress()?
    val localAddress = InetSocketAddress.createUnresolved(settings.httpInterface, settings.httpPort)
    registerWithLocationService(localAddress)
  }

  /**
   * Shuts down this HTTP server
   */
  def shutdown(): Unit = {
    // XXX Is there another way? unbind()?
    system.terminate()
  }

  /**
   * Register with the location service (which must be started as a separate process).
   */
  def registerWithLocationService(addr: InetSocketAddress) {
    val componentId = ComponentId("ConfigServiceHttpServer", ComponentType.Service)
    val httpUri = new URI(s"http://${addr.getHostString}:${addr.getPort}/")
    logger.debug(s"Registering with the location service with URI $httpUri")
    LocationService.registerHttpConnection(componentId, addr.getPort)
  }

  // Error returned for invalid requests
  private def unknownResource(msg: String): Future[HttpResponse] = {
    val s = s"Unknown resource: $msg"
    logger.error(s)
    Future.successful(HttpResponse(StatusCodes.NotFound, entity = s))
  }

  private def httpGet(uri: Uri): Future[HttpResponse] = {
    val path = uri.path.toString()
    logger.debug(s"Received GET request for $path (uri = $uri)")

    path match {
      case "/get"        => get(uri)
      case "/list"       => list()
      case "/history"    => history(uri)
      case "/getDefault" => getDefault(uri)
      case _             => unknownResource(path)
    }
  }

  // Gets the file from the config service with the given path and id or date (optional)
  // If the date is given, it should be the number of secs since 1970 GMT.
  private def get(uri: Uri): Future[HttpResponse] = {
    val pathOpt = uri.query().get("path")
    val idOpt = uri.query().get("id").map(ConfigId(_))
    def getDate(s: String): Date = Try(new Date(s.toLong)).getOrElse(new Date)
    val dateOpt = uri.query().get("date").map(getDate)
    pathOpt match {
      case Some(path) =>
        val result = for {
          result <- if (dateOpt.nonEmpty) client.get(new File(path), dateOpt.get) else client.get(new File(path), idOpt)
        } yield {
          result match {
            case Some(configData) =>
              val chunks = configData.source.map(ChunkStreamPart.apply)
              HttpResponse(entity = HttpEntity.Chunked(MediaTypes.`application/octet-stream`, chunks))
            case None =>
              HttpResponse(StatusCodes.NotFound, entity = s"Not found: $path with id $idOpt")
          }
        }
        result.recover {
          case ex => HttpResponse(StatusCodes.NotFound, entity = ex.toString)
        }
      case None =>
        unknownResource("")
    }
  }

  // Gets the list of files file from the config service
  private def list(): Future[HttpResponse] = {
    val result = for {
      result <- client.list()
    } yield {
      val json = result.toJson.toString()
      HttpResponse(StatusCodes.OK, entity = HttpEntity(MediaTypes.`application/json`, json))
    }
    result.recover {
      case ex => HttpResponse(StatusCodes.NotFound, entity = ex.toString)
    }
  }

  // Gets the file history from the config service
  private def history(uri: Uri): Future[HttpResponse] = {
    val pathOpt = uri.query().get("path")
    val maxResults = uri.query().get("maxResults").map(_.toInt).getOrElse(Int.MaxValue)
    pathOpt match {
      case Some(path) =>
        val result = for {
          result <- client.history(new File(path), maxResults)
        } yield {
          val json = result.toJson.toString()
          HttpResponse(StatusCodes.OK, entity = HttpEntity(MediaTypes.`application/json`, json))
        }
        result.recover {
          case ex => HttpResponse(StatusCodes.NotFound, entity = ex.toString)
        }
      case None =>
        unknownResource("")
    }
  }

  private def httpPost(uri: Uri, entity: RequestEntity): Future[HttpResponse] = {
    val path = uri.path.toString()
    logger.debug(s"Received POST request for $path (uri = $uri)")
    path match {
      case "/create"         => createOrUpdate(uri, entity)
      case "/createOrUpdate" => createOrUpdate(uri, entity)
      case _                 => unknownResource(path)
    }
  }

  private def httpPut(uri: Uri, entity: RequestEntity): Future[HttpResponse] = {
    val path = uri.path.toString()
    logger.debug(s"Received PUT request for $path (uri = $uri)")
    path match {
      case "/update"       => createOrUpdate(uri, entity)
      case "/setDefault"   => setDefault(uri)
      case "/resetDefault" => resetDefault(uri)
      case _               => unknownResource(path)
    }
  }

  // Creates a new file
  private def createOrUpdate(uri: Uri, entity: RequestEntity): Future[HttpResponse] = {
    val op = uri.path.toString().substring(1)
    val pathOpt = uri.query().get("path")
    val oversize = uri.query().get("oversize").getOrElse("false").equalsIgnoreCase("true")
    val comment = uri.query().get("comment").getOrElse("")

    if (op.startsWith("create"))
      logger.debug(s"$op $pathOpt oversize=$oversize comment=$comment")
    else
      logger.debug(s"$op $pathOpt comment=$comment")

    pathOpt match {
      case Some(path) =>
        val configData = ConfigData(entity.dataBytes)
        val file = new File(path)
        val result = for {
          configId <- op match {
            case "create" => client.create(file, configData, oversize, comment)
            case "update" => client.update(file, configData, comment)
            case _        => client.createOrUpdate(file, configData, oversize, comment)
          }
        } yield {
          val json = configId.toJson.toString()
          //          logger.debug(s"Returing configId as: $json")
          HttpResponse(StatusCodes.OK, entity = HttpEntity(MediaTypes.`application/json`, json))
        }
        result.recover {
          case ex =>
            logger.error(s"error processing $uri", ex)
            HttpResponse(StatusCodes.NotFound, entity = ex.toString)
        }
      case None =>
        unknownResource("")
    }
  }

  // Returns OK if the path exists on the server
  private def httpHead(uri: Uri): Future[HttpResponse] = {
    val result = for {
      exists <- client.exists(new File(uri.path.toString()))
    } yield HttpResponse(if (exists) StatusCodes.OK else StatusCodes.NotFound)
    result.recover {
      case ex => HttpResponse(StatusCodes.NotFound)
    }
  }

  // Implements Http DELETE: Returns OK if the file could be deleted or did not exist.
  private def httpDelete(uri: Uri): Future[HttpResponse] = {
    val path = new File(uri.path.toString())
    val comment = uri.query().get("comment").getOrElse("")
    val result = for {
      _ <- client.delete(path, comment)
    } yield HttpResponse(StatusCodes.OK)
    result.recover {
      case ex => HttpResponse(StatusCodes.NotFound, entity = ex.toString)
    }
  }

  // Gets the default version of the file
  def getDefault(uri: Uri): Future[HttpResponse] = {
    val pathOpt = uri.query().get("path")
    pathOpt match {
      case Some(path) =>
        val result = for {
          result <- client.getDefault(new File(path))
        } yield {
          result match {
            case Some(configData) =>
              val chunks = configData.source.map(ChunkStreamPart.apply)
              HttpResponse(entity = HttpEntity.Chunked(MediaTypes.`application/octet-stream`, chunks))
            case None =>
              HttpResponse(StatusCodes.NotFound, entity = s"Not found: $path")
          }
        }
        result.recover {
          case ex => HttpResponse(StatusCodes.NotFound, entity = ex.toString)
        }
      case None =>
        unknownResource("")
    }

  }

  // Sets the default version of the file
  def setDefault(uri: Uri): Future[HttpResponse] = {
    val pathOpt = uri.query().get("path")
    val idOpt = uri.query().get("id").map(ConfigId(_))
    pathOpt match {
      case Some(path) =>
        val result = for {
          configId <- client.setDefault(new File(path), idOpt)
        } yield {
          HttpResponse(StatusCodes.OK)
        }
        result.recover {
          case ex => HttpResponse(StatusCodes.NotFound, entity = ex.toString)
        }
      case None =>
        unknownResource("")
    }

  }

  // Resets the default version of the file to the latest version
  def resetDefault(uri: Uri): Future[HttpResponse] = {
    val pathOpt = uri.query().get("path")
    pathOpt match {
      case Some(path) =>
        val result = for {
          _ <- client.resetDefault(new File(path))
        } yield {
          HttpResponse(StatusCodes.OK)
        }
        result.recover {
          case ex => HttpResponse(StatusCodes.NotFound, entity = ex.toString)
        }
      case None =>
        unknownResource("")
    }
  }
}