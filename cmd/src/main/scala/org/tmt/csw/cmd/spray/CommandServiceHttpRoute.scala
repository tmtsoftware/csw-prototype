package org.tmt.csw.cmd.spray

import spray.routing._
import spray.http.MediaTypes._
import org.tmt.csw.cmd.akka.{CommandServiceClientHelper, CommandStatus, RunId}
import spray.http.StatusCodes
import spray.routing.directives.DebuggingDirectives
import akka.event.Logging
import scala.util._
import scala.concurrent.ExecutionContext
import com.typesafe.config.ConfigFactory
import org.tmt.csw.util.Configuration

/**
 * The command service HTTP (spray) route, defined as a trait, so that it can be used in tests
 * without actually running an HTTP server.
 */
trait CommandServiceHttpRoute extends HttpService
  with CommandServiceClientHelper
  with CommandServiceJsonFormats {

  import ExecutionContext.Implicits.global

  // marks with "get-user", log with info level, HttpRequest.toString
  DebuggingDirectives.logRequest("get-user", Logging.InfoLevel)

  // the root of the ExtJS workspace, which contains all the ExtJS web apps.
  // (use uncompiled sources during development, minified app.js from build dir in production release)
  val extjsRoot = ConfigFactory.defaultReference().getString("csw.extjs.root")
  ConfigFactory.defaultOverrides()
  println(s"Using ExtJS root = $extjsRoot")

  /**
   * Route for static web page
   * (default route is the top level index.html file that can provide links to the apps)
   */
  def staticRoute: Route =
    path("")(getFromDirectory(s"$extjsRoot/index.html")) ~
      getFromDirectory(extjsRoot)

  /**
   * This defines the HTTP/REST interface for the command service.
   */
  def apiRoute: Route =
    path("request")(
      // "POST /request {config: $config}" submits a config to the command service (bypassing the queue) and returns the runId
      post(
        entity(as[Configuration]) {
          config =>
            respondWithMediaType(`application/json`) {
              complete(StatusCodes.Accepted, requestCommand(config))
            }
        }
      )
    ) ~
    path("get")(
        // "POST /get {config: $config}" submits a config to be filled in with the current values
        post(
          entity(as[Configuration]) {
            config =>
              respondWithMediaType(`application/json`) {
                complete {
                  configGet(config).map {
                    resp => resp.tryConfig match {
                      case Success(c) => c.toJson.toString
                      case Failure(ex) => ""
                    }
                  }
                }
              }
          }
        )
      ) ~
      path("status")(
        // "GET /status" returns the internal status of the command server in HTML format
        get(
          respondWithMediaType(`text/html`) {
            complete {
              commandServiceStatus()
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
                respondWithMediaType(`application/json`) {
                    complete(StatusCodes.Accepted, submitCommand(config))
                }
            }
          )
        ) ~
          path("stop")(
            // "POST /queue/stop stops the command queue
            post(
              complete {
                queueStop()
                StatusCodes.Accepted
              }
            )
          ) ~
          path("pause")(
            // "POST /queue/pause pauses the command queue
            post(
              complete {
                queuePause()
                StatusCodes.Accepted
              }
            )
          ) ~
          path("start")(
            // "POST /queue/start restarts the command queue
            post(
              complete {
                queueStart()
                StatusCodes.Accepted
              }
            )
          ) ~
          path(JavaUUID)(uuid =>
          // "DELETE /queue/$runId" deletes the command with the given $runId from the command queue
            delete(
              complete {
                queueDelete(RunId(uuid))
                StatusCodes.Accepted
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
                produce(instanceOf[Option[CommandStatus]]) {
                  completer => _ => checkCommandStatus(runId, completer)
                }
              }
            )
          ) ~
            path("cancel")(
              // "POST /config/$runId/cancel" cancels the command with the given runId
              post(
                complete {
                  configCancel(runId)
                  StatusCodes.Accepted
                }
              )
            ) ~
            path("abort")(
              // "POST /config/$runId/abort" aborts the command with the given runId
              post(
                complete {
                  configAbort(runId)
                  StatusCodes.Accepted
                }
              )
            ) ~
            path("pause")(
              // "POST /config/$runId/pause" pauses the command with the given runId
              post(
                complete {
                  configPause(runId)
                  StatusCodes.Accepted
                }
              )
            ) ~
            path("resume")(
              // "POST /config/$runId/resume" resumes the command with the given runId
              post(
                complete {
                  configResume(runId)
                  StatusCodes.Accepted
                }
              )
            )
      } ~
      pathPrefix("test") {
        path("error") {
          // "POST /test/error" throws an exception (for testing error handling)
          post(
            complete(testError())
          )
        }
      } ~
      // If none of the above paths matched, it must be a bad request
      logRequestResponse("Unrecognized path passed to Spray route: ", Logging.InfoLevel) {
        complete(StatusCodes.BadRequest)
      }



  def route: Route = staticRoute ~ apiRoute

  /**
   *
   */
  def testError(): StatusCodes.Success = {
    if (true) throw new RuntimeException("Testing exception handling")
    StatusCodes.Accepted
  }
}
