package org.tmt.csw.cmd.spray

import spray.routing._
import spray.http.MediaTypes._
import scala.Some
import org.tmt.csw.cmd.akka.{CommandStatus, RunId}
import spray.http.StatusCodes
import org.tmt.csw.cmd.core.Configuration
import org.tmt.csw.cmd.akka.CommandServiceMessage.SubmitWithRunId
import scala.concurrent.duration.FiniteDuration
import akka.actor.ActorRef

/**
 * The command service spray route, defined as a trait, so that it can be used in tests
 * without actually running an HTTP server.
 */
trait CommandServiceRoute extends HttpService with CommandServiceJsonFormats {

  // defined in implementing class
  def commandServiceActor: ActorRef
  def timeout: FiniteDuration

  def route: Route =
    path("status" / JavaUUID)(uuid =>
      get(
        respondWithMediaType(`application/json`) {
          val runId = RunId(uuid)
          if (getMonitorFor(runId).isEmpty) {
            complete(StatusCodes.Gone)
          } else {
            produce(instanceOf[Option[CommandStatus]]) {
              completer => _ => checkCommandStatus(runId, completer)
            }
          }
        }
      )
    ) ~
      path("submit")(
        post(
          entity(as[Configuration]) {
            config =>
              respondWithMediaType(`application/json`) {
                complete(submitCommand(config))
              }
          }
        )
      )

  // Handles a request for command status.
  // If the monitoring actor is still running (it was started when the command was submitted),
  // send it the request (completer). If it timed out and the actor quit, return an error.
  private def checkCommandStatus(runId: RunId, completer: CommandService.Completer): Unit = {
//    log.debug(s"Checking status for $runId ...")
    // The actor monitoring the command should already be running, unless it timed out
    getMonitorFor(runId) match {
      case Some(monitor) =>
        monitor ! completer
      case None =>
    }
  }

  // Handles a command submit and returns the runId, which can be used to request the command status.
  private def submitCommand(config: Configuration): RunId = {
//    log.debug(s"Received a configuration: $config")
    val runId = RunId()
    val monitor = newMonitorFor(runId)
    val submit = SubmitWithRunId(config, monitor, runId)
    // Submit to the command service actor using the monitor actor as the return address for status updates
    commandServiceActor ! submit
    submit.runId
  }

//  // creates a new CommandServiceMonitor actor to listen for status messages for the given runId
//  private def newMonitorFor(runId: RunId): ActorRef = {
//    context.actorOf(CommandServiceMonitor.props(timeout), monitorName(runId))
//  }
//
//  // Gets an existing CommandServiceMonitor actor for the given runId
//  private def getMonitorFor(runId: RunId): Option[ActorRef] = {
//    context.child(monitorName(runId))
//  }
//
//// Gets the name of the CommandServiceMonitor actor for the given runId
//private def monitorName(runId: RunId): String = {
//  s"CommandServiceMonitor-${runId.id}"
//}

  // creates a new CommandServiceMonitor actor to listen for status messages for the given runId
  def newMonitorFor(runId: RunId): ActorRef

  // Gets an existing CommandServiceMonitor actor for the given runId
  def getMonitorFor(runId: RunId): Option[ActorRef]

}
