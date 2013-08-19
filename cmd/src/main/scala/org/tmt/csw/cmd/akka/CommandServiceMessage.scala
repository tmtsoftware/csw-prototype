package org.tmt.csw.cmd.akka

import org.tmt.csw.cmd.core.Configuration
import akka.actor.{Actor, ActorRef}

// Actor messages received

// Messages that operate on the queue
sealed trait CommandServiceMessage

// Messages that operate on a running config
sealed trait ConfigMessage

object CommandServiceMessage {

  /**
   * Submits a configuration without waiting for a reply. Status messages will be sent to the submitter.
   */
  object Submit {
    /**
     * Command service clients should normally use this method (only passing in the config argument)
     */
    def apply(config: Configuration)(implicit submitter: ActorRef = Actor.noSender, ignore: Int = 0): Submit =
      new Submit(config, submitter)
  }
  /**
   * Submit a configuration.
   * @param config the configuration
   * @param submitter the actor submitting the config (normally implicit)
   */
  case class Submit(config: Configuration, submitter: ActorRef) extends CommandServiceMessage

  /**
   * Queue bypass request configuration.
   * @param config the configuration to send
   */
  case class QueueBypassRequest(config: Configuration) extends CommandServiceMessage

  /**
   * Submit a configuration with an assigned runId (internal use only, not accepted by command service actor)
   * @param config the configuration
   * @param submitter the actor submitting the config (normally implicit)
   * @param runId the unique runId (normally generated automatically)
   */
  case class SubmitWithRunId(config: Configuration, submitter: ActorRef, runId: RunId = RunId()) extends CommandServiceMessage

  /**
   * Message to stop the command queue
   */
  case object QueueStop extends CommandServiceMessage

  /**
   * Message to pause the command queue
   */
  case object QueuePause extends CommandServiceMessage

  /**
   * Message to restart the command queue
   */
  case object QueueStart extends CommandServiceMessage

  /**
   * Message to delete the given runId from the command queue
   */
  case class QueueDelete(runId: RunId) extends CommandServiceMessage


  /**
   * Message to cancel the running config with the given runId
   */
  case class ConfigCancel(runId: RunId) extends ConfigMessage

  /**
   * Message to abort the running config with the given runId
   */
  case class ConfigAbort(runId: RunId) extends ConfigMessage

  /**
   * Message to pause the running config with the given runId
   */
  case class ConfigPause(runId: RunId) extends ConfigMessage

  /**
   * Message to resume the running config with the given runId
   */
  case class ConfigResume(runId: RunId) extends ConfigMessage

}
