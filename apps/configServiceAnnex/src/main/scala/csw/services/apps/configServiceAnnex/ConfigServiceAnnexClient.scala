package csw.services.apps.configServiceAnnex

import akka.actor.ActorSystem
import akka.http.Http
import akka.http.model.HttpMethods._
import akka.http.model._
import akka.io.IO
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object ConfigServiceAnnexClient {
  val logger = Logger(LoggerFactory.getLogger("ConfigServiceAnnexClient"))

  implicit val system = ActorSystem("ConfigServiceAnnexClient")

  import system.dispatcher

  val settings = ConfigServiceAnnexSettings(system)
  val host = settings.interface
  val port = settings.port

  implicit val askTimeout: Timeout = 5000.millis
  implicit val materializer = FlowMaterializer()

  def get(file: String): Unit = {
    logger.info(s"Fetching $file from http://$host:$port/$file")

    val result = for {
      connection ← IO(Http).ask(Http.Connect(host, port)).mapTo[Http.OutgoingConnection]
      response ← sendRequest(HttpRequest(GET, uri = s"/$file"), connection)
    } yield response

    result onComplete {
      case Success(res) if res.status == StatusCodes.OK ⇒
        res.entity.getDataBytes().map { chunk ⇒
          System.out.write(chunk.toArray)
          System.out.flush()
        }.to(Sink.ignore).run()

      case Success(res)   ⇒ logger.error(s"HTTP response code: ${res.status}")
      case Failure(error) ⇒ logger.error(s"$error")
    }
  }

  private def sendRequest(request: HttpRequest, connection: Http.OutgoingConnection): Future[HttpResponse] = {
    Source(List(request -> 'NoContext))
      .to(Sink(connection.requestSubscriber))
      .run()
    Source(connection.responsePublisher).map(_._1).runWith(Sink.head)
  }
}
