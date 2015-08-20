package csw.services.cmd.akka

import akka.actor._
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import csw.shared.{ RunId, CommandStatus }
import scala.concurrent.duration._
import csw.services.cmd.akka.CommandServiceActor._
import scala.concurrent.Future
import akka.pattern.ask
import akka.util.Timeout
import csw.util.cfg.Configurations._
import csw.services.cmd.akka.CommandServiceActor.QueueBypassRequestWithRunId
import csw.services.cmd.akka.CommandServiceActor.CommandServiceStatus
import akka.actor.OneForOneStrategy

/**
 * Defines helper methods for implementing command service actor clients.
 */
trait CommandServiceActorClientHelper extends CommandServiceClientHelper with Actor with ActorLogging {

  import CommandQueueActor._
  import ConfigActor._

  // The reference to the command service actor to use
  val commandServiceActor: ActorRef

  // The timeout for polling for the command status of submitted commands
  val timeout: FiniteDuration

  // If there is an error in one of the monitor actors, stop it, but not the others
  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy() {
      case _ ⇒ SupervisorStrategy.Stop
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

  override def submitCommand(config: ConfigList): Source[CommandStatus, Unit] = {
    val runId = RunId()
    val monitor = newMonitorFor(runId)
    val publisher = ActorPublisher[CommandStatus](monitor)
    val submit = SubmitWithRunId(config, monitor, runId)
    // Submit to the command service actor using the monitor actor as the return address for status updates
    val result = Source(publisher)
    commandServiceActor ! submit
    result
  }

  /**
   * Handles a command (queue bypass) request and returns the runId, which can be used to request the command status.
   * @param config the command configuration
   * @return the runId for the command
   */
  override def requestCommand(config: ConfigList): Source[CommandStatus, Unit] = {
    val runId = RunId()
    val monitor = newMonitorFor(runId)
    val publisher = ActorPublisher[CommandStatus](monitor)
    // Send to the command service actor using the monitor actor as the return address for status updates
    val request = QueueBypassRequestWithRunId(config, monitor, runId)
    val result = Source(publisher)
    commandServiceActor ! request
    result
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
  override def configGet(config: SetupConfigList): Future[ConfigResponse] = {
    log.debug(s"configGet $config")
    implicit val askTimeout = Timeout(5.seconds) // XXX TODO FIXME
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
    // See src/main/twirl/csw/services/cmd/index.scala.html
    for {
      status ← (commandServiceActor ? StatusRequest).mapTo[CommandServiceStatus]
    } yield csw.services.cmd.html.index(status).toString()
  }
}
