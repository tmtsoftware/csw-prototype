package csw.services.apps.configServiceAnnex

import akka.actor.ActorSystem
import akka.http.Http
import akka.http.model.HttpMethods._
import akka.http.model._
import akka.io.IO
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TestClient extends App {

  implicit val system = ActorSystem("ServerTest")

  import system.dispatcher

  implicit val materializer = FlowMaterializer()
  implicit val askTimeout: Timeout = 5000.millis

  // Server info
  val host = "localhost"
  val port = 8549

  // File under /tmp to get
  val file = "test.txt"

  val result = for {
    connection ← IO(Http).ask(Http.Connect(host, port)).mapTo[Http.OutgoingConnection]
    response ← sendRequest(HttpRequest(GET, uri = s"/$file"), connection)
  } yield response

  def sendRequest(request: HttpRequest, connection: Http.OutgoingConnection): Future[HttpResponse] = {
    Source(List(request -> 'NoContext))
      .to(Sink(connection.requestSubscriber))
      .run()
    Source(connection.responsePublisher).map(_._1).runWith(Sink.head)
  }

  result onComplete {
    case Success(res) if res.status == StatusCodes.OK ⇒
      res.entity.getDataBytes().map { chunk ⇒
        System.out.write(chunk.toArray)
        System.out.flush()
      }.to(Sink.ignore).run()
    case Success(res)   ⇒ println(s"Got HTTP response code ${res.status}")
    case Failure(error) ⇒ println(s"Error: $error")
  }
  result onComplete { _ ⇒ system.shutdown() }
}