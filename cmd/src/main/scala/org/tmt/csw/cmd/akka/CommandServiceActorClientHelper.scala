package org.tmt.csw.cmd.akka

import akka.actor._
import org.tmt.csw.cmd.core.Configuration
import scala.Some
import akka.actor.OneForOneStrategy
import scala.concurrent.duration._
import org.tmt.csw.cmd.akka.CommandServiceActor._
import scala.concurrent.Future
import akka.pattern.ask
import akka.util.Timeout


/**
 * Defines helper methods for implementing command service actor clients.
 */
trait CommandServiceActorClientHelper extends CommandServiceClientHelper with Actor with ActorLogging {
  import CommandServiceClientHelper._
  import CommandQueueActor._
  import ConfigActor._

  // The reference to the command service actor to use
  val commandServiceActor: ActorRef

  // The timeout for polling for the command status of submitted commands
  val timeout: FiniteDuration

  // If there is an error in one of the monitor actors, stop it, but not the others
  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy() {
      case _ => SupervisorStrategy.Stop
    }


  // creates a new CommandServiceMonitor actor to listen for status messages for the given runId
  private def newMonitorFor(runId: RunId): ActorRef = {
    context.actorOf(CommandServiceStatusMonitor.props(timeout, runId), monitorName(runId))
  }

  // Gets an existing CommandServiceMonitor actor for the given runId
  private def getMonitorFor(runId: RunId): Option[ActorRef] = {
    context.child(monitorName(runId))
  }

  // Gets the name of the CommandServiceMonitor actor for the given runId
  private def monitorName(runId: RunId): String = {
    s"CommandServiceMonitor-${runId.id}"
  }


  /**
   * Handles a command submit and returns the runId, which can be used to request the command status.
   * @param config the command configuration
   * @return the runId for the command
   */
  override def submitCommand(config: Configuration): RunId = {
    val runId = RunId()
    val monitor = newMonitorFor(runId)
    val submit = SubmitWithRunId(config, monitor, runId)
    log.debug(s"Submitting $config")
    // Submit to the command service actor using the monitor actor as the return address for status updates
    commandServiceActor ! submit
    runId
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
    runId
  }

  /**
   * Handles a request for command status (using long polling). Since the command may take a long time to run
   * and the request may time out, this method does not return the status, but calls the completer
   * when the command status is known. If the request times out, nothing is done and the caller needs
   * to try again.
   */
  override def checkCommandStatus(runId: RunId, completer: CommandStatusCompleter): Unit = {
    // If the monitoring actor (which was started when the command was submitted) is still running,
    // send it the the "completer", which it can call to complete the request.
    // If it timed out and the actor quit, do nothing (This case is handled by the caller).
    log.debug(s"Checking status for $runId")
    getMonitorFor(runId) match {
      case Some(monitor) =>
        monitor ! completer
      case None =>
        completer(Some(CommandStatus.Error(runId, "Unknown runId: Request may have timed out").asInstanceOf[CommandStatus]))
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
  override def queueStop(): Unit = {
    commandServiceActor ! QueueStop
  }

  /**
   * Handles a request to pause the command queue.
   */
  override def queuePause(): Unit = {
    commandServiceActor ! QueuePause
  }

  //
  /**
   * Handles a request to restart the command queue.
   */
  def queueStart(): Unit = {
    commandServiceActor ! QueueStart
  }

  /**
   * Handles a request to delete a command from the command queue.
   */
  override def queueDelete(runId: RunId): Unit = {
    commandServiceActor ! QueueDelete(runId)
  }

  /**
   * Handles a request to fill in the blank values of the given config with the current values.
   */
  override def configGet(config: Configuration): Future[ConfigResponse] = {
    log.debug(s"configGet $config")
    implicit val askTimeout = Timeout(3 seconds)
    (commandServiceActor ? ConfigGet(config)).mapTo[ConfigResponse]
  }

  // Handles a request to pause the config with the given runId
  override def configCancel(runId: RunId): Unit = {
    commandServiceActor ! ConfigCancel(runId)
  }

  /**
   * Handles a request to pause the config with the given runId
   */
  override def configAbort(runId: RunId): Unit = {
    commandServiceActor ! ConfigAbort(runId)
  }

  /**
   * Handles a request to pause the config with the given runId
   */
  override def configPause(runId: RunId): Unit = {
    commandServiceActor ! ConfigPause(runId)
  }

  /**
   * Handles a request to pause the config with the given runId
   */
  override def configResume(runId: RunId): Unit = {
    commandServiceActor ! ConfigResume(runId)
  }

  /**
   * Returns a generated HTML page with command server status information
   */
  override def commandServiceStatus(): Future[String] = {
    implicit val statusTimeout = Timeout(5.seconds)
    implicit val dispatcher = context.system.dispatcher

    // Fill in the variables in the Twirl (Play) template and return the resulting HTML page
    // See src/main/twirl/org/tmt/csw/cmd/index.scala.html
    for {
      status <- (commandServiceActor ? StatusRequest).mapTo[CommandServiceStatus]
    } yield org.tmt.csw.cmd.html.index(status).toString()
  }
}
