package org.tmt.csw.cmd.akka

import akka.actor.{ActorRef, Actor}
import ConfigActor._
import org.tmt.csw.cmd.core.Configuration

object ConfigActor {
  // TMT Standard Configuration Interaction Commands
  sealed trait ConfigInteractionCommand

  /**
   * Command to submit a configuration to the component
   * @param runId the runId for this configuration
   * @param config the configuration
   * @param statusActor send status messages to this actor
   * @param lastConfig true if this is the last config in the list for this runId
   */
  case class ConfigSubmit(runId: RunId, config: Configuration, statusActor: ActorRef, lastConfig: Boolean) extends ConfigInteractionCommand
  case class ConfigCancel() extends ConfigInteractionCommand
  case class ConfigAbort() extends ConfigInteractionCommand
  case class ConfigPause() extends ConfigInteractionCommand
  case class ConfigResume() extends ConfigInteractionCommand
}

/**
 * Manages the command queue for the given OMOA component.
 * CommandConfig objects are placed on the queue when received.
 * Later, each config is dequeued and passed to the component for processing.
 */
class ConfigActor(component: OmoaComponent) extends Actor {
  def receive = {
    case ConfigSubmit(runId, config, statusActor, lastConfig) => configSubmit(runId, config, statusActor, lastConfig)
    case ConfigCancel =>
    case ConfigAbort =>
    case ConfigPause =>
    case ConfigResume =>
  }

  // Request immediate execution of the given configs
  // XXX TODO: should this be done in an worker actor (so it can be killed)?
  private def configSubmit(runId: RunId, config: Configuration, statusActor: ActorRef, lastConfig: Boolean) {
    try {
      component.matchConfig(config)
      if (lastConfig) {
        sender ! CommandStatus.StatusComplete(runId)
      }
    } catch {
      case e: Exception => {
        sender ! CommandStatus.StatusError(runId, e)
      }
    }
  }

}
