package csw.services.cs.akka

import java.io.{ File, IOException }

import akka.actor.ActorSystem
import akka.http.Http
import akka.http.Http.OutgoingConnection
import akka.http.model.HttpEntity.ChunkStreamPart
import akka.http.model.HttpMethods._
import akka.http.model._
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.cs.core._
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.Future

case class ConfigServiceHttpClient(settings: ConfigServiceSettings)(implicit system: ActorSystem)
    extends ConfigManager with ConfigServiceJsonFormats {

  val logger = Logger(LoggerFactory.getLogger("ConfigServiceHttpClient"))

  //  // Note: We could take the actor system as an implicit argument from the caller,
  //  // but using a separate one was suggested, to avoid congestion and slowing down actor
  //  // messages while large files are being transferred.
  //  implicit val system = ActorSystem("ConfigServiceHttpClient")

  import system.dispatcher

  val host = settings.httpInterface
  val port = settings.httpPort
  implicit val askTimeout = settings.timeout

  private def sendRequest(request: HttpRequest,
                          connection: Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]])(implicit fm: ActorFlowMaterializer): Future[HttpResponse] = {
    Source.single(request).via(connection).runWith(Sink.head())
  }

  private def makeUri(path: String, kvp: (String, String)*): Uri = Uri().withPath(Uri.Path(path)).withQuery(kvp: _*)

  override val name = settings.name

  override def create(path: File, configData: ConfigData, oversize: Boolean, comment: String): Future[ConfigId] = {
    val uri = makeUri("/create", "path" -> path.toString, "oversize" -> oversize.toString, "comment" -> comment)
    createOrUpdate(uri, configData, comment, create = true)
  }

  override def update(path: File, configData: ConfigData, comment: String): Future[ConfigId] = {
    val uri = makeUri("/update", "path" -> path.toString, "comment" -> comment)
    createOrUpdate(uri, configData, comment, create = false)
  }

  override def createOrUpdate(path: File, configData: ConfigData, oversize: Boolean, comment: String): Future[ConfigId] = {
    val uri = makeUri("/createOrUpdate", "path" -> path.toString, "oversize" -> oversize.toString, "comment" -> comment)
    createOrUpdate(uri, configData, comment, create = true)
  }

  def createOrUpdate(uri: Uri, configData: ConfigData, comment: String, create: Boolean): Future[ConfigId] = {
    logger.info(s"$uri")
    implicit val materializer = ActorFlowMaterializer()

    val chunks = configData.source.map(ChunkStreamPart.apply)
    val entity = HttpEntity.Chunked(MediaTypes.`application/octet-stream`, chunks)
    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(method = POST, uri = uri, entity = entity)

    for {
      result ← sendRequest(request, connection)
      json ← ConfigData(result.entity.dataBytes).toFutureString
    } yield {
      if (result.status == StatusCodes.OK) {
        json.parseJson.convertTo[ConfigId]
      } else {
        val s = s"HTTP response code for $uri: ${result.status}"
        logger.error(s)
        throw new IOException(s)
      }
    }
  }

  override def get(path: File, id: Option[ConfigId] = None): Future[Option[ConfigData]] = {
    val uri = if (id.isDefined)
      makeUri("/get", "path" -> path.toString, "id" -> id.get.id)
    else
      makeUri("/get", "path" -> path.toString)
    logger.info(s"$uri")

    implicit val materializer = ActorFlowMaterializer()
    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(GET, uri = uri)

    for {
      result ← sendRequest(request, connection)
    } yield if (result.status == StatusCodes.OK)
      Some(ConfigData(result.entity.dataBytes))
    else None
  }

  override def exists(path: File): Future[Boolean] = {
    val uri = Uri().withPath(Uri.Path(path.toString))
    logger.info(s"check if $path exists")

    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(HEAD, uri = uri)
    implicit val materializer = ActorFlowMaterializer()

    for {
      result ← sendRequest(request, connection)
    } yield result.status == StatusCodes.OK
  }

  override def delete(path: File, comment: String): Future[Unit] = {
    val uri = makeUri(path.toString, "comment" -> comment)
    logger.info(s"deleting $path")

    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(DELETE, uri = uri)
    implicit val materializer = ActorFlowMaterializer()

    for {
      result ← sendRequest(request, connection)
    } yield ()
  }

  override def list(): Future[List[ConfigFileInfo]] = {
    val uri = Uri("/list")
    logger.info(s"list files")

    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(GET, uri = uri)
    implicit val materializer = ActorFlowMaterializer()

    for {
      result ← sendRequest(request, connection)
      json ← ConfigData(result.entity.dataBytes).toFutureString
    } yield {
      if (result.status == StatusCodes.OK) {
        json.parseJson.convertTo[List[ConfigFileInfo]]
      } else {
        val s = s"HTTP response code for $uri: ${result.status}"
        logger.error(s)
        throw new IOException(s)
      }
    }
  }

  override def history(path: File, maxResults: Int = Int.MaxValue): Future[List[ConfigFileHistory]] = {
    val uri = makeUri("/history", "path" -> path.toString, "maxResults" -> maxResults.toString)
    logger.info(s"history for $path")

    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(GET, uri = uri)
    implicit val materializer = ActorFlowMaterializer()

    for {
      result ← sendRequest(request, connection)
      json ← ConfigData(result.entity.dataBytes).toFutureString
    } yield {
      if (result.status == StatusCodes.OK) {
        json.parseJson.convertTo[List[ConfigFileHistory]]
      } else {
        val s = s"HTTP response code for $uri: ${result.status}"
        logger.error(s)
        throw new IOException(s)
      }
    }
  }

  override def setDefault(path: File, id: Option[ConfigId]): Future[Unit] = {
    val uri = if (id.isDefined)
      makeUri("/setDefault", "path" -> path.toString, "id" -> id.get.id)
    else
      makeUri("/setDefault", "path" -> path.toString)
    logger.info(s"$uri")

    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(POST, uri = uri)
    implicit val materializer = ActorFlowMaterializer()

    for {
      _ ← sendRequest(request, connection)
    } yield ()
  }

  override def resetDefault(path: File): Future[Unit] = {
    val uri = makeUri("/resetDefault", "path" -> path.toString)
    logger.info(s"$uri")

    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(POST, uri = uri)
    implicit val materializer = ActorFlowMaterializer()

    for {
      _ ← sendRequest(request, connection)
    } yield ()
  }

  override def getDefault(path: File): Future[Option[ConfigData]] = {
    val uri = makeUri("/getDefault", "path" -> path.toString)
    logger.info(s"$uri")

    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(GET, uri = uri)
    implicit val materializer = ActorFlowMaterializer()

    for {
      result ← sendRequest(request, connection)
    } yield if (result.status == StatusCodes.OK)
      Some(ConfigData(result.entity.dataBytes))
    else None
  }
}
