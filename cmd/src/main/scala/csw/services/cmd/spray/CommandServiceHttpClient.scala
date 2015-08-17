package csw.services.cmd.spray

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import csw.services.cmd.akka.{ CommandStatus, RunId }
import csw.util.cfg.ConfigJsonFormats
import csw.util.cfg.Configurations._
import scala.concurrent.{ ExecutionContext, Future }
import akka.actor.ActorSystem
import com.typesafe.scalalogging.slf4j.LazyLogging
import akka.http.scaladsl.Http
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Flow, Source }
import de.heikoseeberger.akkasse.{ EventStreamUnmarshalling, ServerSentEvent }
import EventStreamUnmarshalling._
import spray.json._

/**
 * Helper methods for command service http clients
 */
trait CommandServiceHttpClient extends CommandServiceJsonFormats with ConfigJsonFormats with LazyLogging {

  /**
   * The HTTP server host
   */
  val host: String

  /**
   * The HTTP server port
   */
  val port: Int

  /**
   * required Akka system
   */
  implicit val system: ActorSystem

  /**
   * required Akka execution context
   */
  implicit val dispatcher: ExecutionContext

  // Sends a request to the http server
  private def sendRequest(request: HttpRequest,
                          connection: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]])(implicit fm: ActorMaterializer): Future[HttpResponse] = {
    Source.single(request).via(connection).runWith(Sink.head)
  }

  // Converts a ServerSentEvent to a CommandStatus
  private def serverSentEventToCommandStatus(event: ServerSentEvent): CommandStatus = {
    CommandStatusJsonFormat.read(event.data.parseJson)
  }

  /**
   * Posts a submit or request command with the given configuration
   * @param config the configuration to submit to the command queue
   * @param useQueue if true, queue the command, otherwise bypass the queue (request)
   * @return a future stream of CommandStatus values from the running command
   */
  private def submit(config: ConfigList, useQueue: Boolean): Future[Source[CommandStatus, Any]] = {
    implicit val mat = ActorMaterializer()
    val uri = if (useQueue) s"http://$host:$port/queue/submit" else s"http://$host:$port/request"
    val entity = HttpEntity(ContentTypes.`application/json`, config.toJson.toString())
    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(method = POST, uri = uri, entity = entity)

    for {
      result ← sendRequest(request, connection)
      sourceSse ← Unmarshal(result).to[Source[ServerSentEvent, Any]]
    } yield {
      sourceSse.map(event ⇒ serverSentEventToCommandStatus(event))
    }
  }

  /**
   * Posts a submit command with the given configuration
   * @param config the configuration to submit to the command queue
   * @return a future stream of CommandStatus values from the running command
   */
  def queueSubmit(config: ConfigList): Future[Source[CommandStatus, Any]] = submit(config, useQueue = true)

  /**
   * Posts a queue bypass request command with the given configuration
   * @param config the configuration to request for the command queue
   * @return a future runId with which to reference the command
   */
  def queueBypassRequest(config: ConfigList): Future[Source[CommandStatus, Any]] = submit(config, useQueue = false)

  /**
   * Posts a queue stop command
   * @return a future http response
   */
  def queueStop(): Future[HttpResponse] = queuePost("stop")

  /**
   * Posts a queue pause command
   * @return a future http response
   */
  def queuePause(): Future[HttpResponse] = queuePost("pause")

  /**
   * Posts a queue start command
   * @return a future http response
   */
  def queueStart(): Future[HttpResponse] = queuePost("start")

  /**
   * Deletes an item from the command queue
   * @param runId identifies the configuration to delete from the command queue
   * @return a future http response
   */
  def queueDelete(runId: RunId): Future[HttpResponse] = {
    implicit val mat = ActorMaterializer()
    val uri = s"http://$host:$port/queue/$runId"
    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(method = DELETE, uri = uri)
    sendRequest(request, connection)
  }

  /**
   * Posts a test/error command (will cause server to throw an exception), used for testing
   * @return a future http response
   */
  def testError(): Future[HttpResponse] = {
    implicit val mat = ActorMaterializer()
    val uri = s"http://$host:$port/test/error"
    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(method = POST, uri = uri)
    sendRequest(request, connection)
  }

  //  /**
  //   * Gets the command status (once). This method returns the current command status.
  //   * @param runId identifies a configuration previously submitted or requested
  //   * @return the future command status
  //   */
  //  def getCommandStatus(runId: RunId): Future[CommandStatus] = {
  //    logger.debug(s"Attempting to get command status for runId $runId")
  //    val pipeline = sendReceive ~> unmarshal[CommandStatus]
  //    pipeline(Get(s"http://$host:$port/config/$runId/status"))
  //  }
  //
  //  /**
  //   * Polls the command status for the given runId until the command completes (commandStatus.done is true).
  //   * The command status normally starts out as Queued, then becomes Busy and eventually Complete,
  //   * although other statuses are possible, such as Aborted or Canceled.
  //   * @param runId identifies a configuration previously submitted or requested
  //   * @param maxAttempts max number of times to ask for the command status before giving up if the command does not complete
  //   * @return the future command status
  //   */
  //  def pollCommandStatus(runId: RunId, maxAttempts: Int = 10): Future[CommandStatus] = {
  //    val f = for (commandStatus ← getCommandStatus(runId)) yield {
  //      if (commandStatus.done) {
  //        Future.successful(commandStatus)
  //      } else if (maxAttempts > 0) {
  //        pollCommandStatus(runId, maxAttempts - 1)
  //      } else {
  //        Future.successful(CommandStatus.Error(runId, "Timed out while waiting for command status"))
  //      }
  //    }
  //    // Flatten the result, which is of type Future[Future[CommandStatus]], to get a Future[CommandStatus]
  //    f.flatMap[CommandStatus] { x ⇒ x }
  //  }

  /**
   * Posts a config cancel command with the given runId
   * @return a future http response
   */
  def configCancel(runId: RunId): Future[HttpResponse] = configPost(runId, "cancel")

  /**
   * Posts a config abort command with the given runId
   * @return a future http response
   */
  def configAbort(runId: RunId): Future[HttpResponse] = configPost(runId, "abort")

  /**
   * Posts a config pause command with the given runId
   * @return a future http response
   */
  def configPause(runId: RunId): Future[HttpResponse] = configPost(runId, "pause")

  /**
   * Posts a config resume command with the given runId
   * @return a future http response
   */
  def configResume(runId: RunId): Future[HttpResponse] = configPost(runId, "resume")

  // Posts the queue command with the given name
  private def queuePost(name: String): Future[HttpResponse] = {
    implicit val mat = ActorMaterializer()
    val uri = s"http://$host:$port/queue/$name"
    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(method = POST, uri = uri)
    sendRequest(request, connection)
  }

  // Posts the config command with the given runId and name
  private def configPost(runId: RunId, name: String): Future[HttpResponse] = {
    implicit val mat = ActorMaterializer()
    val uri = s"http://$host:$port/config/$runId/$name"
    val connection = Http().outgoingConnection(host, port)
    val request = HttpRequest(method = POST, uri = uri)
    sendRequest(request, connection)
  }
}
