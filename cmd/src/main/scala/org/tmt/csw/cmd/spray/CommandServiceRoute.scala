package org.tmt.csw.cmd.spray

import spray.routing._
import spray.http.MediaTypes._
import org.tmt.csw.cmd.akka.{CommandStatus, RunId}
import spray.http.StatusCodes
import org.tmt.csw.cmd.core.Configuration

/**
 * The command service spray route, defined as a trait, so that it can be used in tests
 * without actually running an HTTP server.
 */
trait CommandServiceRoute extends HttpService with CommandServiceJsonFormats {

  /**
   * This defines the HTTP/REST interface for the command service.
   */
  def route: Route =
    path("request")(
      // "POST /request {config: $config}" submits a config to the command service (bypassing the queue) and returns the runId
      post(
        entity(as[Configuration]) {
          config =>
            respondWithMediaType(`application/json`) {
              respondWithStatus(StatusCodes.Accepted) {
                complete(requestCommand(config))
              }
            }
        }
      )
    ) ~
      pathPrefix("queue") {
        path("submit")(
          // "POST /queue/submit {config: $config}" submits a config to the command service and returns the runId
          post(
            entity(as[Configuration]) {
              config =>
                println(s"XXX config = $config")
                respondWithMediaType(`application/json`) {
                  respondWithStatus(StatusCodes.Accepted) {
                    complete(submitCommand(config))
                  }
                }
            }
          )
        ) ~
          path("stop")(
            // "POST /queue/stop stops the command queue
            post(
              respondWithStatus(StatusCodes.OK) {
                complete(queueStop())
              }
            )
          ) ~
          path("pause")(
            // "POST /queue/pause pauses the command queue
            post(
              respondWithStatus(StatusCodes.OK) {
                complete(queuePause())
              }
            )
          ) ~
          path("start")(
            // "POST /queue/start restarts the command queue
            post(
              respondWithStatus(StatusCodes.OK) {
                complete(queueStart())
              }
            )
          ) ~
          path(JavaUUID)(uuid =>
          // "DELETE /queue/$runId" deletes the command with the given $runId from the command queue
            delete(
              respondWithStatus(StatusCodes.OK) {
                complete(queueDelete(RunId(uuid)))
              }
            )
          )
      } ~
      pathPrefix("config" / JavaUUID) {
        uuid =>
          val runId = RunId(uuid)
          path("status")(
            // "GET /config/$runId/status" returns the CommandStatus for the given $runId
            get(
              respondWithMediaType(`application/json`) {
                if (statusRequestTimedOut(runId)) {
                  complete(StatusCodes.Gone)
                } else {
                  produce(instanceOf[Option[CommandStatus]]) {
                    completer => _ => checkCommandStatus(runId, completer)
                  }
                }
              }
            )
          ) ~
            path("cancel")(
              // "POST /config/$runId/cancel" cancels the command with the given runId
              post(
                respondWithStatus(StatusCodes.OK) {
                  complete(configCancel(runId))
                }
              )
            ) ~
            path("abort")(
              // "POST /config/$runId/abort" aborts the command with the given runId
              post(
                respondWithStatus(StatusCodes.OK) {
                  complete(configAbort(runId))
                }
              )
            ) ~
            path("pause")(
              // "POST /config/$runId/pause" pauses the command with the given runId
              post(
                respondWithStatus(StatusCodes.OK) {
                  complete(configPause(runId))
                }
              )
            ) ~
            path("resume")(
              // "POST /config/$runId/resume" resumes the command with the given runId
              post(
                respondWithStatus(StatusCodes.OK) {
                  complete(configResume(runId))
                }
              )
            )
      }


  // -- Classes that extend this trait need to implement the methods below --

  /**
   * Handles a command submit and returns the runId, which can be used to request the command status.
   * @param config the command configuration
   * @return the runId for the command
   */
  def submitCommand(config: Configuration): RunId

  /**
   * Handles a command (queue bypass) request and returns the runId, which can be used to request the command status.
   * @param config the command configuration
   * @return the runId for the command
   */
  def requestCommand(config: Configuration): RunId

  /**
   * Handles a request for command status (using long polling). Since the command may take a long time to run
   * and the HTTP request may time out, this method does not return the status, but calls the completer
   * when the command status is known. If the HTTP request times out, nothing is done and the caller needs
   * to try again.
   */
  def checkCommandStatus(runId: RunId, completer: CommandService.Completer): Unit

  /**
   * Returns true if the status request for the given runId timed out (and should be retried)
   */
  def statusRequestTimedOut(runId: RunId): Boolean

  /**
   * Handles a request to stop the command queue.
   */
  def queueStop(): StatusCodes.Success

  /**
   * Handles a request to pause the command queue.
   */
  def queuePause(): StatusCodes.Success

  //
  /**
   * Handles a request to restart the command queue.
   */
  def queueStart(): StatusCodes.Success

  /**
   * Handles a request to delete a command from the command queue.
   */
  def queueDelete(runId: RunId): StatusCodes.Success

  /**
   * Handles a request to pause the config with the given runId
   */
  def configCancel(runId: RunId): StatusCodes.Success

  /**
   * Handles a request to pause the config with the given runId
   */
  def configAbort(runId: RunId): StatusCodes.Success

  /**
   * Handles a request to pause the config with the given runId
   */
  def configPause(runId: RunId): StatusCodes.Success

  /**
   * Handles a request to pause the config with the given runId
   */
  def configResume(runId: RunId): StatusCodes.Success
}
