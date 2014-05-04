package org.tmt.csw.cmd.spray

import org.tmt.csw.cmd.akka.{CommandStatus, RunId}
import spray.client.pipelining._
import scala.concurrent.{ExecutionContext, Future}
import spray.http.HttpResponse
import akka.actor.ActorSystem
import org.tmt.csw.util.Configuration
import com.typesafe.scalalogging.slf4j.LazyLogging

/**
 * Helper methods for command service clients
 */
trait CommandServiceHttpClient extends CommandServiceJsonFormats with LazyLogging {

  /**
   * The HTTP server host
   */
  val interface: String

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


  /**
   * Posts a submit command with the given configuration
   * @param config the configuration to submit to the command queue
   * @return a future runId with which to reference the command
   */
  def queueSubmit(config: Configuration): Future[RunId] = {
    val pipeline = sendReceive ~> unmarshal[RunId]
    pipeline(Post(s"http://$interface:$port/queue/submit", config))
  }

  /**
   * Posts a queue bypass request command with the given configuration
   * @param config the configuration to request for the command queue
   * @return a future runId with which to reference the command
   */
  def queueBypassRequest(config: Configuration): Future[RunId] = {
    val pipeline = sendReceive ~> unmarshal[RunId]
    pipeline(Post(s"http://$interface:$port/request", config))
  }

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
    val pipeline = sendReceive
    pipeline(Delete(s"http://$interface:$port/queue/$runId"))
  }

  /**
   * Posts a test/error command (will cause server to throw an exception), used for testing
   * @return a future http response
   */
  def testError(): Future[HttpResponse] = {
    val pipeline = sendReceive
    pipeline(Post(s"http://$interface:$port/test/error"))
  }

  /**
   * Gets the command status (once). This method returns the current command status.
   * @param runId identifies a configuration previously submitted or requested
   * @return the future command status
   */
  def getCommandStatus(runId: RunId): Future[CommandStatus] = {
    logger.debug(s"Attempting to get command status for runId $runId")
    val pipeline = sendReceive ~> unmarshal[CommandStatus]
    pipeline(Get(s"http://$interface:$port/config/$runId/status"))
  }

  /**
   * Polls the command status for the given runId until the command completes (commandStatus.done is true).
   * The command status normally starts out as Queued, then becomes Busy and eventually Complete,
   * although other statuses are possible, such as Aborted or Canceled.
   * @param runId identifies a configuration previously submitted or requested
   * @param maxAttempts max number of times to ask for the command status before giving up if the command does not complete
   * @return the future command status
   */
  def pollCommandStatus(runId: RunId, maxAttempts: Int = 10): Future[CommandStatus] = {
    val f = for (commandStatus <- getCommandStatus(runId)) yield {
      if (commandStatus.done) {
        Future.successful(commandStatus)
      } else if (maxAttempts > 0) {
        pollCommandStatus(runId, maxAttempts-1)
      } else {
        Future.successful(CommandStatus.Error(runId, "Timed out while waiting for command status"))
      }
    }
    // Flatten the result, which is of type Future[Future[CommandStatus]], to get a Future[CommandStatus]
    f.flatMap[CommandStatus] {x => x}
  }

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
    val pipeline = sendReceive
    pipeline(Post(s"http://$interface:$port/queue/$name"))
  }

  // Posts the config command with the given runId and name
  private def configPost(runId: RunId, name: String): Future[HttpResponse] = {
    val pipeline = sendReceive
    pipeline(Post(s"http://$interface:$port/config/$runId/$name"))
  }
}
