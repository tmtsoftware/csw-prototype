package csw.services.cmd.akka

import akka.stream.scaladsl.Source
import csw.shared.cmd.CommandStatus
import csw.shared.cmd.RunId

import scala.concurrent.Future
import csw.util.cfg.Configurations._
import csw.services.cmd.akka.ConfigActor.ConfigResponse

object CommandServiceClientHelper {

  /**
   * Holds a function that completes a request when given an optional CommandStatus
   * (used for long polling of command status while a command is running).
   */
  case class CommandStatusCompleter(complete: Option[CommandStatus] ⇒ Unit)

}

/**
 * Defines the API for command service client helper methods
 */
trait CommandServiceClientHelper {

  /**
   * Handles a command submit and returns a Source reporting the command status changes.
   * @param config the command configuration
   * @return a stream of command status changes, such as Busy, PartiallyCompleted, and Done
   */
  def submitCommand(config: ConfigList): Source[CommandStatus, Unit]

  /**
   * Handles a command (queue bypass) request and returns the runId, which can be used to request the command status.
   * @param config the command configuration
   * @return the runId for the command
   */
  def requestCommand(config: ConfigList): Source[CommandStatus, Unit]

  /**
   * Handles a request to stop the command queue.
   */
  def queueStop(): Unit

  /**
   * Handles a request to pause the command queue.
   */
  def queuePause(): Unit

  //
  /**
   * Handles a request to restart the command queue.
   */
  def queueStart(): Unit

  /**
   * Handles a request to delete a command from the command queue.
   */
  def queueDelete(runId: RunId): Unit

  /**
   * Handles a request to fill in the blank values of the given config with the current values.
   */
  def configGet(config: SetupConfigList): Future[ConfigResponse]

  /**
   * Handles a request to pause the config with the given runId
   */
  def configCancel(runId: RunId): Unit

  /**
   * Handles a request to pause the config with the given runId
   */
  def configAbort(runId: RunId): Unit

  /**
   * Handles a request to pause the config with the given runId
   */
  def configPause(runId: RunId): Unit

  /**
   * Handles a request to pause the config with the given runId
   */
  def configResume(runId: RunId): Unit

  /**
   * Returns a future generated HTML page with command server status information
   */
  def commandServiceStatus(): Future[String]

}
