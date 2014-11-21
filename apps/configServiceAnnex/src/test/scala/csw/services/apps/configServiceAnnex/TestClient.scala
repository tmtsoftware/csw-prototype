package test

import java.io.FileOutputStream

import akka.actor.ActorSystem
import akka.http.Http
import akka.http.model.HttpMethods._
import akka.http.model._
import akka.io.IO
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{ForeachSink, Sink, Source}
import akka.util.{ByteString, Timeout}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Try, Failure, Success}

// The test client tries to get /tmp/test.txt by getting http://localhost:8549/test.txt
// and stores the result in /tmp/out.txt.
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

  // The local file in which to store the received data
  val localFile = "/tmp/out.txt"

  val out = new FileOutputStream(localFile)
  val sink = ForeachSink[ByteString] { bytes =>
    println(s"XXX writing ${bytes.size} bytes to $localFile")
    out.write(bytes.toArray)
  }

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
      val materialized = res.entity.getDataBytes().to(sink).run()

      // ensure the output file is closed (otherwise some bytes may not be written)
      materialized.get(sink).onComplete {
        case Success(_) =>
          println("Success: closing the file")
          Try(out.close())
          system.shutdown()
        case Failure(e) =>
          println(s"Failure: ${e.getMessage}")
          Try(out.close())
          system.shutdown()
      }

    case Success(res)   ⇒
      println(s"Got HTTP response code ${res.status}")
      system.shutdown()

    case Failure(error) ⇒ println(s"Error: $error")
      system.shutdown()
  }
}