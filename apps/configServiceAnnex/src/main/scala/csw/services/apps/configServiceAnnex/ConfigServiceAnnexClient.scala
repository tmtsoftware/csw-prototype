package csw.services.apps.configServiceAnnex

import java.io.{File, FileOutputStream, IOException}

import akka.actor.ActorSystem
import akka.http.Http
import akka.http.model.HttpEntity.ChunkStreamPart
import akka.http.model.HttpMethods._
import akka.http.model._
import akka.io.IO
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{ForeachSink, Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.slf4j.Logger
import net.codejava.security.HashGeneratorUtils
import org.slf4j.LoggerFactory

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

object ConfigServiceAnnexClient {
  val logger = Logger(LoggerFactory.getLogger("ConfigServiceAnnexClient"))

  implicit val system = ActorSystem("ConfigServiceAnnexClient")
  import system.dispatcher

  val settings = ConfigServiceAnnexSettings(system)
  val host = settings.interface
  val port = settings.port
  implicit val askTimeout = settings.timeout

  /**
   * Downloads the file with the given id from the server and saves it to the given file.
   * Once the file is downloaded, the content is validated against the SHA-1 id.
   * @param id the id of the file to get (SHA-1 hash of the contents)
   * @param file the local file in which to store the contents returned from the server
   * @return a future containing the file, if successful, or the exception if there was an error
   */
  def get(id: String, file: File): Future[File] = {
    val uri = s"http://$host:$port/$id"
    logger.info(s"Downloading $file from $uri")
    implicit val materializer = FlowMaterializer()

    val out = new FileOutputStream(file)
    val sink = ForeachSink[ByteString] { bytes ⇒
      out.write(bytes.toArray)
    }

    val result = for {
      connection ← IO(Http).ask(Http.Connect(host, port)).mapTo[Http.OutgoingConnection]
      response ← sendRequest(HttpRequest(GET, uri = s"/$id"), connection)
    } yield response

    val status = Promise[File]()
    result onComplete {
      case Success(res) if res.status == StatusCodes.OK ⇒
        val materialized = res.entity.getDataBytes().to(sink).run()
        // ensure the output file is closed and the system shutdown upon completion
        materialized.get(sink).onComplete {
          case Success(_) ⇒
            Try(out.close())
            FileUtils.validate(id, file, status)
          case Failure(e) ⇒
            logger.error(s"Failed to download $uri to $file: ${e.getMessage}")
            Try(out.close())
            status.failure(e)
        }

      case Success(res)   ⇒
        val s = s"HTTP response code for $uri: ${res.status}"
        logger.error(s)
        status.failure(new IOException(s))

      case Failure(error) ⇒
        logger.error(s"$error")
        status.failure(error)
    }
    status.future
  }

  /**
   * Uploads the given file to the server, storing it under the given SHA-1 hash of the contents.
   * @param file the local file to upload to the server
   * @return a future containing the file, if successful, or the exception if there was an error
   */
  def post(file: File): Future[File] = {
    val id = HashGeneratorUtils.generateSHA1(file)
    val uri = s"http://$host:$port/$id"
    logger.info(s"Uploading $file to $uri")
    implicit val materializer = FlowMaterializer()

    val mappedByteBuffer = FileUtils.mmap(file.toPath)
    val iterator = new FileUtils.ByteBufferIterator(mappedByteBuffer, settings.chunkSize)
    val chunks = Source(iterator).map(ChunkStreamPart.apply)

    val entity = HttpEntity.Chunked(MediaTypes.`application/octet-stream`, chunks)

    val result = for {
      connection ← IO(Http).ask(Http.Connect(host, port)).mapTo[Http.OutgoingConnection]
      response ← sendRequest(HttpRequest(method = POST, uri = s"/$id", entity = entity), connection)
    } yield response

    val status = Promise[File]()
    result onComplete {
      case Success(res) if res.status == StatusCodes.OK ⇒
        status.success(file)

      case Success(res)   ⇒
        val s = s"HTTP response code for $uri: ${res.status}"
        logger.error(s)
        status.failure(new IOException(s))

      case Failure(error) ⇒
        logger.error(s"$error")
        status.failure(error)
    }
    status.future
  }

  private def sendRequest(request: HttpRequest, connection: Http.OutgoingConnection)
                         (implicit fm: FlowMaterializer): Future[HttpResponse] = {
    Source(List(request -> 'NoContext))
      .to(Sink(connection.requestSubscriber))
      .run()
    Source(connection.responsePublisher).map(_._1).runWith(Sink.head)
  }

  def shutdown(): Unit = system.shutdown()
}
