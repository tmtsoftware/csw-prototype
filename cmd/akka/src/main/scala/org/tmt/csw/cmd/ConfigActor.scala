package org.tmt.csw.cmd

import akka.actor.Actor
import ConfigActor._
import com.typesafe.config.Config

object ConfigActor {
  // TMT Standard Configuration Interaction Commands
  sealed trait ConfigInteractionCommand
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
    case ConfigCancel =>
    case ConfigAbort =>
    case ConfigPause =>
    case ConfigResume =>
  }
}
