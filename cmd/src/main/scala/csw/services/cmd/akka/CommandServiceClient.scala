package csw.services.cmd.akka

import akka.actor.ActorRef
import akka.stream.scaladsl.Source
import csw.shared.CommandStatus
import csw.shared.cmd.RunId
import scala.concurrent.{ ExecutionContext, Future }
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import csw.util.cfg.Configurations._

/**
 * A simplified interface to the command service actor.
 */
case class CommandServiceClient(commandServiceClientActor: ActorRef, statusTimeout: FiniteDuration) {

  import CommandServiceActor._
  import CommandQueueActor._
  import ConfigActor._

  implicit val timeout = Timeout(statusTimeout)

  /**
   * Handles a command submit and returns the runId, which can be used to request the command status.
   * @param config the command configuration
   * @return a future stream of command status changes, such as Busy, PartiallyCompleted, and Done
   */
  def queueSubmit(config: ConfigList): Future[Source[CommandStatus, Unit]] =
    (commandServiceClientActor ? Submit(config)).mapTo[Source[CommandStatus, Unit]]

  /**
   * Handles a command (queue bypass) request and returns the runId, which can be used to request the command status.
   * @param config the command configuration
   * @return a future stream of command status changes, such as Busy, PartiallyCompleted, and Done
   */
  def queueBypassRequest(config: ConfigList): Future[Source[CommandStatus, Unit]] =
    (commandServiceClientActor ? QueueBypassRequest(config)).mapTo[Source[CommandStatus, Unit]]

  /**
   * Used to query the current state of a device. A config is passed in (the values are ignored)
   * and a reply will be sent containing the same config with the current values filled out.
   *
   * @param config used to specify the keys for the values that should be returned
   * @return the response (a config wrapped in a Try)
   */
  def configGet(config: SetupConfigList): Future[ConfigResponse] =
    (commandServiceClientActor ? ConfigGet(config)).mapTo[ConfigResponse]

  /**
   * Handles a request to stop the command queue.
   */
  def queueStop(): Unit = commandServiceClientActor ! QueueStop

  /**
   * Handles a request to pause the command queue.
   */
  def queuePause(): Unit = commandServiceClientActor ! QueuePause

  /**
   * Handles a request to restart the command queue.
   */
  def queueStart(): Unit = commandServiceClientActor ! QueueStart

  /**
   * Handles a request to delete a command from the command queue.
   */
  def queueDelete(runId: RunId): Unit = commandServiceClientActor ! QueueDelete(runId)

  /**
   * Handles a request to pause the config with the given runId
   */
  def configCancel(runId: RunId): Unit = commandServiceClientActor ! ConfigCancel(runId)

  /**
   * Handles a request to pause the config with the given runId
   */
  def configAbort(runId: RunId): Unit = commandServiceClientActor ! ConfigAbort(runId)

  /**
   * Handles a request to pause the config with the given runId
   */
  def configPause(runId: RunId): Unit = commandServiceClientActor ! ConfigPause(runId)

  /**
   * Handles a request to pause the config with the given runId
   */
  def configResume(runId: RunId): Unit = commandServiceClientActor ! ConfigResume(runId)
}

