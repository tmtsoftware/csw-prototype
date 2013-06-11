package org.tmt.csw.cmd.akka

import akka.actor.{ActorLogging, Actor}
import ConfigActor._
import org.tmt.csw.cmd.core.Configuration

/**
 * Defines messages and states for use by actors that are command service targets.
 */
object ConfigActor {
  // TMT Standard Configuration Interaction Commands
  sealed trait ConfigInteractionCommand
  case class ConfigSubmit(runId: RunId, config: Configuration) extends ConfigInteractionCommand
  case class ConfigCancel(runId: RunId) extends ConfigInteractionCommand
  case class ConfigAbort(runId: RunId) extends ConfigInteractionCommand
  case class ConfigPause(runId: RunId) extends ConfigInteractionCommand
  case class ConfigResume(runId: RunId) extends ConfigInteractionCommand

  // Config states
  sealed trait ConfigState
  case class Initialized() extends ConfigState
  case class Submitted() extends ConfigState
  case class Canceled() extends ConfigState
  case class Aborted() extends ConfigState
  case class Stopped() extends ConfigState
  case class Paused() extends ConfigState
  case class Resumed() extends ConfigState
}

/**
 * Command service targets can implement this trait, which defines
 * methods for implementing the standard configuration control messages.
 */
abstract class ConfigActor(val name: String) extends Actor with ActorLogging {
  protected var configState : ConfigState = Initialized()

  def receive = {
    case ConfigSubmit(runId, config) => configSubmit(runId, config)
    case ConfigCancel(runId) => configCancel(runId)
    case ConfigAbort(runId) => configAbort(runId)
    case ConfigPause(runId) => configPause(runId)
    case ConfigResume(runId) => configResume(runId)
  }

  /**
   * Submits the given configuration for execution (to "match" the configuration).
   * @param runId unique id for this run
   * @param config the configuration to execute
   */
  def configSubmit(runId: RunId, config: Configuration) {
    log.debug(s"Submit config with runId: $runId")
    configState = Submitted()
  }

  /**
   * Actions due to a previous request should be stopped immediately without completing.
   * @param runId unique id for this run
   */
  def configAbort(runId: RunId) {
    log.debug(s"Abort config with runId: $runId")
    configState = Aborted()
  }

  /**
   * Actions due to a Configuration should be stopped cleanly as soon as convenient without necessarily completing.
   * @param runId unique id for this run
   */
  def configCancel(runId: RunId) {
    log.debug(s"Cancel config with runId: $runId")
    configState = Canceled()
  }

  /**
   * Pause the actions associated with a specific Configuration.
   * @param runId unique id for this run
   */
  def configPause(runId: RunId){
    log.debug(s"Pause config with runId: $runId")
    configState = Paused()
  }

  /**
   * Resume the paused actions associated with a specific Configuration.
   * @param runId unique id for this run
   */
  def configResume(runId: RunId) {
    log.debug(s"Resume config with runId: $runId")
    configState = Resumed()
  }
}
