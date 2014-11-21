package test

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.{Path, Paths, StandardOpenOption}
import java.nio.{ByteBuffer, MappedByteBuffer}

import akka.actor.ActorSystem
import akka.http.Http
import akka.http.model.HttpEntity.ChunkStreamPart
import akka.http.model._
import akka.io.IO
import akka.pattern.ask
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}

import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

// A test HTTP server that returns files with a given name from /tmp.
// The test client tries to get /tmp/test.txt by getting http://localhost:8549/test.txt
// and stores the result in /tmp/out.txt.
object TestServer extends App {
  implicit val system = ActorSystem()

  import system.dispatcher

  implicit val materializer = FlowMaterializer()
  implicit val askTimeout: Timeout = 5000.millis

  val host = "localhost"
  val port = 8549

  // Serve files from this directory
  val dir = new File("/tmp")

  val chunkSize = 4096

  import akka.http.model.HttpMethods._

  val requestHandler: HttpRequest ⇒ HttpResponse = {
    case HttpRequest(GET, uri, headers, _, _) ⇒
      val path = makePath(dir, new File(uri.path.toString()))
      println(s"Received request for $path (uri = $uri)")
      val result = Try {
        val mappedByteBuffer = mmap(path)
        val iterator = new ByteBufferIterator(mappedByteBuffer, chunkSize)
        val chunks = Source(iterator).map(ChunkStreamPart.apply)
        HttpResponse(entity = HttpEntity.Chunked(MediaTypes.`application/octet-stream`, chunks))
      } recover {
        case NonFatal(cause) ⇒
          println(s"Nonfatal error: cause = ${cause.getMessage}")
          HttpResponse(StatusCodes.InternalServerError, entity = cause.getMessage)
      }
      result.get
    case _: HttpRequest ⇒ HttpResponse(StatusCodes.NotFound, entity = "Unknown resource!")
  }

  val bindingFuture = IO(Http) ? Http.Bind(host, port)
  bindingFuture foreach {
    case Http.ServerBinding(localAddress, connectionStream) ⇒
      println(s"Listening on http:/$localAddress/")
      Source(connectionStream).foreach {
        case Http.IncomingConnection(remoteAddress, requestProducer, responseConsumer) ⇒
          println(s"Accepted new connection from $remoteAddress")
          val materialized = Source(requestProducer).map(requestHandler).to(Sink(responseConsumer)).run()
      }
  }

  bindingFuture.recover {
    case ex =>
      println("error: " + ex.getMessage)
      system.shutdown()
  }

  def mmap(path: Path): MappedByteBuffer = {
    val channel = FileChannel.open(path, StandardOpenOption.READ)
    val result = channel.map(FileChannel.MapMode.READ_ONLY, 0L, channel.size())
    channel.close()
    result
  }

  class ByteBufferIterator(buffer: ByteBuffer, chunkSize: Int) extends Iterator[ByteString] {
    require(buffer.isReadOnly)
    require(chunkSize > 0)

    override def hasNext = buffer.hasRemaining

    override def next(): ByteString = {
      val size = chunkSize min buffer.remaining()
      val temp = buffer.slice()
      temp.limit(size)
      buffer.position(buffer.position() + size)
      ByteString(temp)
    }
  }

  def makePath(dir: File, file: File): Path = {
    Paths.get(dir.getPath, file.getName)
  }
}