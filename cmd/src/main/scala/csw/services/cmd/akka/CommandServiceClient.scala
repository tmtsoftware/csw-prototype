package csw.services.cmd.akka

import akka.actor.ActorRef
import akka.stream.scaladsl.Source
import scala.concurrent.{ ExecutionContext, Future }
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import csw.util.cfg.Configurations._
import csw.services.cmd.akka.CommandServiceClientActor.GetStatus

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
   * Gets the current status for the given command (Queued, Busy, Completed, ...)
   * @param runId the runId for the command
   * @return the future status
   */
  def getCommandStatus(runId: RunId): Future[CommandStatus] =
    (commandServiceClientActor ? GetStatus(runId)).mapTo[CommandStatus]

  /**
   * Used to query the current state of a device. A config is passed in (the values are ignored)
   * and a reply will be sent containing the same config with the current values filled out.
   *
   * @param config used to specify the keys for the values that should be returned
   * @return the response (a config wrapped in a Try)
   */
  def configGet(config: SetupConfigList): Future[ConfigResponse] =
    (commandServiceClientActor ? ConfigGet(config)).mapTo[ConfigResponse]

  //  /**
  //   * Polls the command status for the given runId until the command completes (commandStatus.done is true).
  //   * The command status normally starts out as Queued, then becomes Busy and eventually Complete,
  //   * although other statuses are possible, such as Aborted or Canceled.
  //   * @param runId identifies a configuration previously submitted or requested
  //   * @param maxAttempts max number of times to ask for the command status before giving up if the command does not complete
  //   * @return the future command status
  //   */
  //  def pollCommandStatus(runId: RunId, maxAttempts: Int = 10): Future[CommandStatus] = {
  //    val f = for (commandStatus ← getCommandStatus(runId)) yield {
  //      if (commandStatus.done) {
  //        Future.successful(commandStatus)
  //      } else if (maxAttempts > 0) {
  //        pollCommandStatus(runId, maxAttempts - 1)
  //      } else {
  //        Future.successful(CommandStatus.Error(runId, "Timed out while waiting for command status"))
  //      }
  //    }
  //    // Flatten the result, which is of type Future[Future[CommandStatus]], to get a Future[CommandStatus]
  //    f.flatMap[CommandStatus] { x ⇒ x }
  //  }

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

