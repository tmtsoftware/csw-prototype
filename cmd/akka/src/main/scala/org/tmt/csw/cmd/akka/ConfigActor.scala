package org.tmt.csw.cmd.akka

import akka.actor._
import ConfigActor._
import org.tmt.csw.cmd.core.Configuration
import java.util.concurrent.atomic.AtomicReference
import org.tmt.csw.cmd.akka.ConfigState.ConfigState

/**
 * Defines messages and states for use by actors that are command service targets
 * (i.e.: actors that process Configuration messages.)
 */
object ConfigActor {
  // TMT Standard Configuration Interaction Commands
  sealed trait ConfigInteractionCommand
  case class ConfigSubmit(config: Configuration, state: AtomicReference[ConfigState]) extends ConfigInteractionCommand
  case object ConfigCancel extends ConfigInteractionCommand
  case object ConfigAbort extends ConfigInteractionCommand
  case object ConfigPause extends ConfigInteractionCommand
  case class ConfigResume(config: Configuration, state: AtomicReference[ConfigState]) extends ConfigInteractionCommand
}

/**
 * Command service targets can implement this trait, which defines
 * methods for implementing the standard configuration control messages.
 * One instance of this actor is created for each submitted config.
 */
abstract class ConfigActor extends Actor with ActorLogging {

  /**
   * Messages received in the normal state.
   */
  def receive = {
    case ConfigSubmit(config, state) => configSubmit(config, state)
    case ConfigCancel => cancel()
    case ConfigAbort => abort()
    case ConfigPause => context.become(paused); pause()
    case x => log.error(s"Unexpected ConfigActor message: $x")
  }

  /**
   * Messages received when paused.
   */
  def paused: Receive = {
    case ConfigResume(config, state) => configResume(config, state)
    case ConfigCancel => cancel()
    case ConfigAbort => abort()
    case x => log.error(s"Unexpected ConfigActor message while paused: $x")
  }

  // Calls the derived class implementation of submit() and sends the returned config state
  // to the sender (Paused and Resumed states are not currently sent).
  private def configSubmit(config: Configuration, state: AtomicReference[ConfigState]) {
    submit(config, state) match {
      case ConfigState.Paused() => log.debug(s"Config Paused")
      case configState: ConfigState => sender ! configState
    }
  }

  // Goes back to receive mode, then calls the derived class implementation of resume()
  // and sends the returned config state to the sender (Paused and Resumed states are not currently sent).
  private def configResume(config: Configuration, state: AtomicReference[ConfigState]) {
    context.become(receive)
    resume(config, state) match {
      case ConfigState.Paused() => log.debug(s"Config Paused again")
      case configState: ConfigState => sender ! configState
    }
  }

  /**
   * Execute the given config.
   * Implementations should monitor the state variable and stop work if needed,
   * due to a change of state to Aborted, Canceled or Paused.
   *
   * @param config the configuration to execute
   * @param state the current configuration state
   */
  def submit(config: Configuration, state: AtomicReference[ConfigState]) : ConfigState

  /**
   * Resume the paused actions associated with a specific Configuration.
   * This is like submit, but starting at the point where processing was paused.
   */
  def resume(config: Configuration, state: AtomicReference[ConfigState]) : ConfigState

  /**
   * Called when the config was paused. At this point context.become(paused) has already been called.
   */
  def pause() {}

  /**
   * Called when the config was canceled. The actor will be terminated (do optional cleanup here).
   */
  def cancel() {}

  /**
   * Called when the config was aborted. The actor will be terminated (do optional cleanup here).
   */
  def abort() {}

  override def postStop() {
    super.postStop()
    log.debug("ConfigActor stopped")
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    super.preRestart(reason, message)
    log.error("ConfigActor restarted")
  }
}
