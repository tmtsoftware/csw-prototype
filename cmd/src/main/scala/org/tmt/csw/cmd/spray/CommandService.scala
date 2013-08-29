package org.tmt.csw.cmd.spray

import akka.actor._
import akka.io.IO
import scala.concurrent.duration.FiniteDuration
import spray.can.Http
import spray.routing.{HttpServiceActor, Route}
import org.tmt.csw.cmd.core.Configuration
import org.tmt.csw.cmd.akka.{CommandStatus, RunId}
import spray.http.MediaTypes._
import org.tmt.csw.cmd.akka.CommandServiceMessage.SubmitWithRunId
import akka.actor.OneForOneStrategy
import spray.http.StatusCodes

/**
 * Messages and `akka.actor.Props` factories for the CommandService actor.
 * (Original algorithm based on Spray example at http://hseeberger.github.io/blog/2013/07/22/gabbler-part3/)
 */
object CommandService {

  /**
   * Function completing a request when given a list of CommandStatus objects.
   */
  type Completer = Option[CommandStatus] => Unit

  /**
   * Factory for `akka.actor.Props` for CommandService.
   */
  def props(commandServiceActor: ActorRef, interface: String, port: Int, timeout: FiniteDuration): Props =
    Props(new CommandService(commandServiceActor, interface, port, timeout))
}

/**
 * A service providing a REST-ful API to the command service actor
 */
class CommandService(commandServiceActor: ActorRef, interface: String, port: Int, timeout: FiniteDuration)
  extends HttpServiceActor with ActorLogging with DefaultJsonFormats {

  IO(Http)(context.system) ! Http.Bind(self, interface, port)

  override def receive: Receive = runRoute(route)

  private def route: Route =
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
    log.debug(s"Checking status for $runId ...")
    // The actor monitoring the command should already be running, unless it timed out
    getMonitorFor(runId) match {
      case Some(monitor) =>
        log.debug(s"Sending status request to $monitor")
        monitor ! completer
      case None =>
    }
  }

  // Handles a command submit and returns the runId, which can be used to request the command status.
  private def submitCommand(config: Configuration): RunId = {
    log.debug(s"Received a configuration: $config")
    val runId = RunId()
    val monitor = newMonitorFor(runId)
    val submit = SubmitWithRunId(config, monitor, runId)
    // Submit to the command service actor using the monitor actor as the return address for status updates
    commandServiceActor ! submit
    submit.runId
  }

  // creates a new CommandServiceMonitor actor to listen for status messages for the given runId
  private def newMonitorFor(runId: RunId): ActorRef = {
    context.actorOf(CommandServiceMonitor.props(timeout), monitorName(runId))
  }

  // Gets an existing CommandServiceMonitor actor for the given runId
  private def getMonitorFor(runId: RunId): Option[ActorRef] = {
    context.child(monitorName(runId))
  }

  // Gets the name of the CommandServiceMonitor actor for the given runId
  private def monitorName(runId: RunId): String = {
    s"CommandServiceMonitor-${runId.id}"
  }

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy() {
      case _ => SupervisorStrategy.Stop
    }
}
