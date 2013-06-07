package org.tmt.csw.cmd.akka

import akka.actor.{ActorRef, Actor}
import ConfigActor._
import org.tmt.csw.cmd.core.Configuration

object ConfigActor {
  // TMT Standard Configuration Interaction Commands
  sealed trait ConfigInteractionCommand
  case class ConfigSubmit(runId: RunId, config: Configuration) extends ConfigInteractionCommand
  case class ConfigCancel(runId: RunId) extends ConfigInteractionCommand
  case class ConfigAbort(runId: RunId) extends ConfigInteractionCommand
  case class ConfigPause(runId: RunId) extends ConfigInteractionCommand
  case class ConfigResume(runId: RunId) extends ConfigInteractionCommand
}

/**
 * Base class for command service targets.
 * Subclasses can implement the abstract methods declared here to implement the commands.
 */
abstract class ConfigActor extends Actor {
  def receive = {
    case ConfigSubmit(runId, config) => configSubmit(runId, config)
    case ConfigCancel(runId) => configCancel(runId)
    case ConfigAbort(runId) => configAbort(runId)
    case ConfigPause(runId) => configPause(runId)
    case ConfigResume(runId) => configResume(runId)
  }

  /**
   * The name of the target component
   */
  def getName() : String

  /**
   * Submits the given configuration
   * @param runId identifies the configuration
   * @param config the configuration to execute
   */
  def configSubmit(runId: RunId, config: Configuration)

  def configAbort(runId: RunId)

  def configCancel(runId: RunId)

  def configPause(runId: RunId)

  def configResume(runId: RunId)
}
