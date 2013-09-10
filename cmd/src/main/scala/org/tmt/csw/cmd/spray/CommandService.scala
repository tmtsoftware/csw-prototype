package org.tmt.csw.cmd.spray

import akka.actor._
import akka.io.IO
import scala.concurrent.duration.FiniteDuration
import spray.can.Http
import spray.routing.HttpServiceActor
import org.tmt.csw.cmd.akka.{RunId, CommandStatus}
import org.tmt.csw.cmd.core.Configuration
import org.tmt.csw.cmd.akka.CommandServiceMessage._
import spray.http.StatusCodes
import org.tmt.csw.cmd.akka.CommandServiceMessage.QueueBypassRequestWithRunId
import org.tmt.csw.cmd.akka.CommandServiceMessage.ConfigAbort
import org.tmt.csw.cmd.akka.CommandServiceMessage.QueueDelete
import org.tmt.csw.cmd.akka.CommandServiceMessage.ConfigCancel
import org.tmt.csw.cmd.akka.CommandServiceMessage.ConfigPause
import org.tmt.csw.cmd.akka.CommandServiceMessage.SubmitWithRunId
import scala.Some
import akka.actor.OneForOneStrategy

/**
 * Messages and `akka.actor.Props` factories for the CommandService actor.
 * (Original algorithm based on Spray example at http://hseeberger.github.io/blog/2013/07/22/gabbler-part3/)
 */
object CommandService {

  val unknownRunIdMessage = "Unknown runId: Request may have timed out"

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
case class CommandService(commandServiceActor: ActorRef, interface: String, port: Int, timeout: FiniteDuration)
  extends HttpServiceActor with CommandServiceRoute with ActorLogging {

  // Starts the HTTP server for this service on the host and port configured in resources/reference.conf
  IO(Http)(context.system) ! Http.Bind(self, interface, port)

  // Entry point for the actor
  override def receive: Receive = runRoute(route)

  // If there is an error in one of the monitor actors, stop it, but not the others
  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy() {
      case _ => SupervisorStrategy.Stop
    }

  // -- Methods used to manage the monitor actors, which monitor the command status for a running command --

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


  // -- These methods are used by the command service HTTP route --

  /**
   * Handles a command submit and returns the runId, which can be used to request the command status.
   * @param config the command configuration
   * @return the runId for the command
   */
  override def submitCommand(config: Configuration): RunId = {
    val runId = RunId()
    val monitor = newMonitorFor(runId)
    val submit = SubmitWithRunId(config, monitor, runId)
    // Submit to the command service actor using the monitor actor as the return address for status updates
    commandServiceActor ! submit
    submit.runId
  }

  /**
   * Handles a command (queue bypass) request and returns the runId, which can be used to request the command status.
   * @param config the command configuration
   * @return the runId for the command
   */
  override def requestCommand(config: Configuration): RunId = {
    val runId = RunId()
    val monitor = newMonitorFor(runId)
    val request = QueueBypassRequestWithRunId(config, monitor, runId)
    // Send request to the command service actor using the monitor actor as the return address for status updates
    commandServiceActor ! request
    request.runId
  }

  /**
   * Handles a request for command status (using long polling). Since the command may take a long time to run
   * and the HTTP request may time out, this method does not return the status, but calls the completer
   * when the command status is known. If the HTTP request times out, nothing is done and the caller needs
   * to try again.
   */
  override def checkCommandStatus(runId: RunId, completer: CommandService.Completer): Unit = {
    // If the monitoring actor (which was started when the command was submitted) is still running,
    // send it the the "completer", which it can call to complete the HTTP request.
    // If it timed out and the actor quit, do nothing (This case is handled by the caller).
    getMonitorFor(runId) match {
      case Some(monitor) =>
        monitor ! completer
      case None =>
        completer(Some(CommandStatus.Error(runId, CommandService.unknownRunIdMessage).asInstanceOf[CommandStatus]))
    }
  }

  /**
   * Returns true if the status request for the given runId timed out (and should be retried)
   * or the runId is not known.
   */
  override def statusRequestTimedOut(runId: RunId): Boolean = {
    getMonitorFor(runId).isEmpty
  }

  /**
   * Handles a request to stop the command queue.
   */
  override def queueStop(): StatusCodes.Success = {
    commandServiceActor ! QueueStop
    StatusCodes.Accepted
  }

  /**
   * Handles a request to pause the command queue.
   */
  override def queuePause(): StatusCodes.Success = {
    commandServiceActor ! QueuePause
    StatusCodes.Accepted
  }

  //
  /**
   * Handles a request to restart the command queue.
   */
  override def queueStart(): StatusCodes.Success = {
    commandServiceActor ! QueueStart
    StatusCodes.Accepted
  }

  /**
   * Handles a request to delete a command from the command queue.
   */
  override def queueDelete(runId: RunId): StatusCodes.Success = {
    commandServiceActor ! QueueDelete(runId)
    StatusCodes.Accepted
  }

  // Handles a request to pause the config with the given runId
  override def configCancel(runId: RunId): StatusCodes.Success = {
    commandServiceActor ! ConfigCancel(runId)
    StatusCodes.Accepted
  }

  /**
   * Handles a request to pause the config with the given runId
   */
  override def configAbort(runId: RunId): StatusCodes.Success = {
    commandServiceActor ! ConfigAbort(runId)
    StatusCodes.Accepted
  }

  /**
   * Handles a request to pause the config with the given runId
   */
  override def configPause(runId: RunId): StatusCodes.Success = {
    commandServiceActor ! ConfigPause(runId)
    StatusCodes.Accepted
  }

  /**
   * Handles a request to pause the config with the given runId
   */
  override def configResume(runId: RunId): StatusCodes.Success = {
    commandServiceActor ! ConfigResume(runId)
    StatusCodes.Accepted
  }

}
