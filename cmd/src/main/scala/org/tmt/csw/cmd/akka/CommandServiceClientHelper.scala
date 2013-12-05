package org.tmt.csw.cmd.akka

import org.tmt.csw.cmd.core.Configuration
import scala.concurrent.Future
import org.tmt.csw.cmd.akka.ConfigActor.ConfigResponse

object CommandServiceClientHelper {
  /**
   * Function completing a request when given a CommandStatus option
   * (used for long polling of command status while a command is running).
   */
  type CommandStatusCompleter = Option[CommandStatus] => Unit
}

/**
 * Defines the API for command service client helper methods
 */
trait CommandServiceClientHelper {

  /**
   * Handles a command submit and returns the runId, which can be used to request the command status.
   * @param config the command configuration
   * @return the runId for the command
   */
  def submitCommand(config: Configuration): RunId

  /**
   * Handles a command (queue bypass) request and returns the runId, which can be used to request the command status.
   * @param config the command configuration
   * @return the runId for the command
   */
  def requestCommand(config: Configuration): RunId

  /**
   * Handles a request for command status (using long polling). Since the command may take a long time to run
   * and the HTTP request may time out, this method does not return the status, but calls the completer
   * when the command status is known. If the HTTP request times out, nothing is done and the caller needs
   * to try again.
   */
  def checkCommandStatus(runId: RunId, completer: CommandServiceClientHelper.CommandStatusCompleter): Unit

  /**
   * Returns true if the status request for the given runId timed out (and should be retried)
   */
  def statusRequestTimedOut(runId: RunId): Boolean

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
  def configGet(config: Configuration): Future[ConfigResponse]

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

}
