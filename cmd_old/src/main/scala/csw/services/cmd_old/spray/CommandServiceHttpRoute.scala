package csw.services.cmd_old.spray

import akka.actor.ActorRefFactory
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import csw.shared.cmd_old.CommandStatus
import csw.shared.cmd_old.RunId
import csw.util.cfg_old.ConfigJsonFormats
import csw.util.cfg_old.Configurations._
import csw.services.cmd_old.akka.CommandServiceClientHelper
import akka.event.LoggingAdapter
import de.heikoseeberger.akkasse.{ EventStreamMarshalling, ServerSentEvent }
import scala.language.implicitConversions
import scala.util.Success
import scala.util.Failure
import spray.json._
import akka.http.scaladsl.server.Directives._

/**
 * The command service HTTP (spray) route, defined as a trait, so that it can be used in tests
 * without actually running an HTTP server.
 */
trait CommandServiceHttpRoute extends CommandServiceClientHelper
    with ConfigJsonFormats
    with EventStreamMarshalling {

  // Implementing classes need to define logging
  def log: LoggingAdapter

  // Converts a command status to a JSON string wrapped in a ServerSentEvent
  def commandStatusToServerSentEvent(status: CommandStatus): ServerSentEvent = {
    import upickle.default._

    val json = write(status)
    ServerSentEvent(json)
  }

  /**
   * This defines the HTTP/REST interface for the command service.
   */
  def route(implicit actorRefFactory: ActorRefFactory): Route =
    logRequestResult("command service") {
      implicit val mat = ActorMaterializer()
      import actorRefFactory.dispatcher

      path("request")(
        // "POST /request {config: $config}" submits a config to the command service (bypassing the queue) and returns
        // a stream of server sent events containing the command status
        post(
          entity(as[List[ConfigType]]) {
            config ⇒
              complete {
                requestCommand(config).map(commandStatusToServerSentEvent)
              }
          })) ~
        path("get")(
          // "POST /get {config: $config}" submits a config to be filled in with the current values
          post(
            entity(as[List[SetupConfig]]) {
              config ⇒
                complete {
                  configGet(config).map {
                    resp ⇒
                      resp.tryConfig match {
                        case Success(setupConfigList) ⇒
                          val json = setupConfigList.toJson.toString()
                          log.debug(s"configGet http response $json")
                          json
                        case Failure(ex) ⇒
                          log.error(s"$ex")
                          // XXX TODO: How to return an error to here?
                          // throw ex
                          ""
                      }
                  }
                }
            })) ~
          path("status")(
            // "GET /status" returns the internal status of the command server in HTML format
            get(
              complete {
                commandServiceStatus()
              })) ~
            pathPrefix("queue") {
              path("submit")(
                // "POST /queue/submit {config: $config}" submits a config to the command service and returns
                // a stream of server sent events containing the command status
                post(
                  entity(as[List[ConfigType]]) {
                    config ⇒
                      complete {
                        submitCommand(config).map(commandStatusToServerSentEvent)
                      }
                  })) ~
                path("stop")(
                  // "POST /queue/stop stops the command queue
                  post(
                    complete {
                      queueStop()
                      StatusCodes.Accepted
                    })) ~
                  path("pause")(
                    // "POST /queue/pause pauses the command queue
                    post(
                      complete {
                        queuePause()
                        StatusCodes.Accepted
                      })) ~
                    path("start")(
                      // "POST /queue/start restarts the command queue
                      post(
                        complete {
                          queueStart()
                          StatusCodes.Accepted
                        })) ~
                      path(JavaUUID)(uuid ⇒
                        // "DELETE /queue/$runId" deletes the command with the given $runId from the command queue
                        delete(
                          complete {
                            queueDelete(RunId(uuid))
                            StatusCodes.Accepted
                          }))
            } ~
            pathPrefix("config" / JavaUUID) {
              uuid ⇒
                val runId = RunId(uuid)
                path("cancel")(
                  // "POST /config/$runId/cancel" cancels the command with the given runId
                  post(
                    complete {
                      configCancel(runId)
                      StatusCodes.Accepted
                    })) ~
                  path("abort")(
                    // "POST /config/$runId/abort" aborts the command with the given runId
                    post(
                      complete {
                        configAbort(runId)
                        StatusCodes.Accepted
                      })) ~
                    path("pause")(
                      // "POST /config/$runId/pause" pauses the command with the given runId
                      post(
                        complete {
                          configPause(runId)
                          StatusCodes.Accepted
                        })) ~
                      path("resume")(
                        // "POST /config/$runId/resume" resumes the command with the given runId
                        post(
                          complete {
                            configResume(runId)
                            StatusCodes.Accepted
                          }))
            } ~
            pathPrefix("test") {
              path("error") {
                // "POST /test/error" throws an exception (for testing error handling)
                post(
                  complete(testError()))
              }
            } ~
            // If none of the above paths matched, it must be a bad request
            complete(StatusCodes.BadRequest)
    }

  /**
   *
   */
  def testError(): StatusCodes.Success = {
    if (true) throw new RuntimeException("Testing exception handling")
    StatusCodes.Accepted
  }
}
