package csw.services.cs.akka

import java.io.File
import java.net.{ InetSocketAddress, URI }

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.Http
import akka.http.model.HttpEntity.ChunkStreamPart
import akka.http.model._
import akka.stream.FlowMaterializer
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.cs.core.{ ConfigData, ConfigId }
import csw.services.ls.LocationServiceActor.{ ServiceId, ServiceType }
import csw.services.ls.LocationServiceRegisterActor
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import spray.json._

case class ConfigServiceHttpServer(configServiceActor: ActorRef, settings: ConfigServiceSettings, registerWithLoc: Boolean = false)(implicit system: ActorSystem) extends ConfigServiceJsonFormats {
  val logger = Logger(LoggerFactory.getLogger("ConfigServiceHttpServer"))
  logger.info("Config service http server started")

  import akka.http.model.HttpMethods._
  import system.dispatcher

  implicit val askTimeout = settings.timeout
  val client = ConfigServiceClient(configServiceActor)
  implicit val materializer = FlowMaterializer()

  val binding = Http().bind(interface = settings.httpInterface, port = settings.httpPort)
  binding.connections.foreach { c ⇒
    logger.info(s"Accepted new connection from ${c.remoteAddress}")
    c.handleWithAsyncHandler {
      case HttpRequest(GET, uri, _, _, _)       ⇒ httpGet(uri)
      case HttpRequest(POST, uri, _, entity, _) ⇒ httpPost(uri, entity)
      case HttpRequest(HEAD, uri, _, _, _)      ⇒ httpHead(uri)
      case HttpRequest(DELETE, uri, _, _, _)    ⇒ httpDelete(uri)
      case x: HttpRequest                       ⇒ unknownResource(x.toString)
    }
  }

  //  binding.localAddress()
  if (registerWithLoc) {
    // XXX TODO FIXME
    val localAddress = InetSocketAddress.createUnresolved(settings.httpInterface, settings.httpPort)
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

  // Error returned for invalid requests
  private def unknownResource(msg: String): Future[HttpResponse] = {
    val s = s"Unknown resource: $msg"
    logger.error(s)
    Future.successful(HttpResponse(StatusCodes.NotFound, entity = s))
  }

  private def httpGet(uri: Uri): Future[HttpResponse] = {
    val path = uri.path.toString()
    logger.info(s"Received GET request for $path (uri = $uri)")

    path match {
      case "/get"        ⇒ get(uri)
      case "/list"       ⇒ list()
      case "/history"    ⇒ history(uri)
      case "/getDefault" ⇒ getDefault(uri)
      case _             ⇒ unknownResource(path)
    }
  }

  // Gets the file from the config service with the given path and id (optional)
  private def get(uri: Uri): Future[HttpResponse] = {
    val pathOpt = uri.query.get("path")
    val idOpt = uri.query.get("id").map(ConfigId(_))
    pathOpt match {
      case Some(path) ⇒
        val result = for {
          result ← client.get(new File(path), idOpt)
        } yield {
          result match {
            case Some(configData) ⇒
              val chunks = configData.source.map(ChunkStreamPart.apply)
              HttpResponse(entity = HttpEntity.Chunked(MediaTypes.`application/octet-stream`, chunks))
            case None ⇒
              HttpResponse(StatusCodes.NotFound, entity = s"Not found: $path with id $idOpt")
          }
        }
        result.recover {
          case ex ⇒ HttpResponse(StatusCodes.NotFound, entity = ex.toString)
        }
      case None ⇒
        unknownResource("")
    }
  }

  // Gets the list of files file from the config service
  private def list(): Future[HttpResponse] = {
    val result = for {
      result ← client.list()
    } yield {
      val json = result.toJson.toString()
      HttpResponse(StatusCodes.OK, entity = HttpEntity(MediaTypes.`application/json`, json))
    }
    result.recover {
      case ex ⇒ HttpResponse(StatusCodes.NotFound, entity = ex.toString)
    }
  }

  // Gets the file history from the config service
  private def history(uri: Uri): Future[HttpResponse] = {
    val pathOpt = uri.query.get("path")
    val maxResults = uri.query.get("maxResults").map(_.toInt).getOrElse(Int.MaxValue)
    pathOpt match {
      case Some(path) ⇒
        val result = for {
          result ← client.history(new File(path), maxResults)
        } yield {
          val json = result.toJson.toString()
          HttpResponse(StatusCodes.OK, entity = HttpEntity(MediaTypes.`application/json`, json))
        }
        result.recover {
          case ex ⇒ HttpResponse(StatusCodes.NotFound, entity = ex.toString)
        }
      case None ⇒
        unknownResource("")
    }
  }

  private def httpPost(uri: Uri, entity: RequestEntity): Future[HttpResponse] = {
    val path = uri.path.toString()
    logger.info(s"Received POST request for $path (uri = $uri)")
    path match {
      case "/create"       ⇒ create(uri, entity)
      case "/update"       ⇒ update(uri, entity)
      case "/setDefault"   ⇒ setDefault(uri)
      case "/resetDefault" ⇒ resetDefault(uri)
      case _               ⇒ unknownResource(path)
    }
  }

  // Creates a new file
  private def create(uri: Uri, entity: RequestEntity): Future[HttpResponse] = {
    val pathOpt = uri.query.get("path")
    val oversize = uri.query.get("oversize").getOrElse("false").equalsIgnoreCase("true")
    val comment = uri.query.get("comment").getOrElse("")

    logger.info(s"create $pathOpt oversize=$oversize comment=$comment")

    pathOpt match {
      case Some(path) ⇒
        val configData = ConfigData(entity.dataBytes)
        val result = for {
          configId ← client.create(new File(path), configData, oversize, comment)
        } yield {
          val json = configId.toJson.toString()
          logger.info(s"Returing configId as: $json")
          HttpResponse(StatusCodes.OK, entity = HttpEntity(MediaTypes.`application/json`, json))
        }
        result.recover {
          case ex ⇒ HttpResponse(StatusCodes.NotFound, entity = ex.toString)
        }
      case None ⇒
        unknownResource("")
    }
  }

  // Updates an existing file
  private def update(uri: Uri, entity: RequestEntity): Future[HttpResponse] = {
    val pathOpt = uri.query.get("path")
    val oversize = uri.query.get("oversize").getOrElse("false").equalsIgnoreCase("true")
    val comment = uri.query.get("comment").getOrElse("")

    logger.info(s"update $pathOpt comment=$comment")

    pathOpt match {
      case Some(path) ⇒
        val configData = ConfigData(entity.dataBytes)
        val result = for {
          configId ← client.update(new File(path), configData, comment)
        } yield {
          val json = configId.toJson.toString()
          logger.info(s"Returing configId as: $json")
          HttpResponse(StatusCodes.OK, entity = HttpEntity(MediaTypes.`application/json`, json))
        }
        result.recover {
          case ex ⇒ HttpResponse(StatusCodes.NotFound, entity = ex.toString)
        }
      case None ⇒
        unknownResource("")
    }
  }

  // Returns OK if the path exists on the server
  private def httpHead(uri: Uri): Future[HttpResponse] = {
    val result = for {
      exists ← client.exists(new File(uri.path.toString()))
    } yield HttpResponse(if (exists) StatusCodes.OK else StatusCodes.NotFound)
    result.recover {
      case ex ⇒ HttpResponse(StatusCodes.NotFound, entity = ex.toString)
    }
  }

  // Implements Http DELETE: Returns OK if the file could be deleted or did not exist.
  private def httpDelete(uri: Uri): Future[HttpResponse] = {
    val path = new File(uri.path.toString())
    val comment = uri.query.get("comment").getOrElse("")
    val result = for {
      _ ← client.delete(path, comment)
    } yield HttpResponse(StatusCodes.OK)
    result.recover {
      case ex ⇒ HttpResponse(StatusCodes.NotFound, entity = ex.toString)
    }
  }

  // Gets the default version of the file
  def getDefault(uri: Uri): Future[HttpResponse] = {
    val pathOpt = uri.query.get("path")
    pathOpt match {
      case Some(path) ⇒
        val result = for {
          result ← client.getDefault(new File(path))
        } yield {
          result match {
            case Some(configData) ⇒
              val chunks = configData.source.map(ChunkStreamPart.apply)
              HttpResponse(entity = HttpEntity.Chunked(MediaTypes.`application/octet-stream`, chunks))
            case None ⇒
              HttpResponse(StatusCodes.NotFound, entity = s"Not found: $path")
          }
        }
        result.recover {
          case ex ⇒ HttpResponse(StatusCodes.NotFound, entity = ex.toString)
        }
      case None ⇒
        unknownResource("")
    }

  }

  // Sets the default version of the file
  def setDefault(uri: Uri): Future[HttpResponse] = {
    val pathOpt = uri.query.get("path")
    val idOpt = uri.query.get("id").map(ConfigId(_))
    pathOpt match {
      case Some(path) ⇒
        val result = for {
          configId ← client.setDefault(new File(path), idOpt)
        } yield {
          HttpResponse(StatusCodes.OK)
        }
        result.recover {
          case ex ⇒ HttpResponse(StatusCodes.NotFound, entity = ex.toString)
        }
      case None ⇒
        unknownResource("")
    }

  }

  // Resets the default version of the file to the latest version
  def resetDefault(uri: Uri): Future[HttpResponse] = {
    val pathOpt = uri.query.get("path")
    pathOpt match {
      case Some(path) ⇒
        val result = for {
          _ ← client.resetDefault(new File(path))
        } yield {
          HttpResponse(StatusCodes.OK)
        }
        result.recover {
          case ex ⇒ HttpResponse(StatusCodes.NotFound, entity = ex.toString)
        }
      case None ⇒
        unknownResource("")
    }
  }
}