package org.tmt.csw.cmd.akka

import akka.actor._
import ConfigActor._
import ConfigState._
import org.tmt.csw.cmd.core.Configuration
import akka.pattern._
import akka.util.Timeout

/**
 * Defines messages and states for use by actors that are command service targets.
 */
object ConfigActor {
  // TMT Standard Configuration Interaction Commands
  sealed trait ConfigInteractionCommand
  case class ConfigSubmit(config: Configuration, timeout: Timeout) extends ConfigInteractionCommand
  case object ConfigCancel extends ConfigInteractionCommand
  case object ConfigAbort extends ConfigInteractionCommand
  case object ConfigPause extends ConfigInteractionCommand
  case object ConfigResume extends ConfigInteractionCommand
}

/**
 * Command service targets can implement this trait, which defines
 * methods for implementing the standard configuration control messages.
 * One instance of this actor is created for each submitted config.
 * The specifics of how to submit or pause a config are left to the
 * implementing class, which is expected to have a worker actor that
 * receives Configuration objects to execute.
 */
abstract class ConfigActor extends Actor with ActorLogging {

  implicit val execContext = context.dispatcher

  private var status: ConfigState = Initialized

  private def setStatus(s: ConfigState) {
    status = s
    log.debug(s"Status: ${s.getClass.getSimpleName}")
  }

  private def configSubmit(sender: ActorRef, config: Configuration, timeout: Timeout) {
    implicit val t = timeout
    getWorkerActor ? config map {
      case state: ConfigState =>
        self ! PoisonPill
        status match {
          case Canceled => status
          case Aborted => status
          case _ => Completed
        }
      case x => log.error(s"Received unknown message: $x")
    } recover {
      case ex: Exception =>
        log.error("Error returned from submit", ex)
        self ! PoisonPill
        ex
    } pipeTo sender
  }

  /**
   * Returns the current status, which is set from the received messages
   */
  def getStatus = status

  def receive = {
    case ConfigSubmit(config, timeout) => setStatus(Submitted); configSubmit(sender, config, timeout)
    case ConfigCancel => setStatus(Canceled); configCancel()
    case ConfigAbort => setStatus(Aborted); configAbort()
    case ConfigPause => setStatus(Paused); configPause()
    case ConfigResume => setStatus(Resumed); configResume()
    case x => log.error(s"Unknown ConfigActor message: $x")
  }

  /**
   * Return a reference to a worker actor that receives [Configuration] objects in the background
   * (so that this actor is always ready to receive other messages, such as ConfigPause or ConfigAbort).
   * The worker actor should reply with the message Completed() when done.
   */
  def getWorkerActor : ActorRef

  /**
   * Actions due to a previous request should be stopped immediately without completing.
   */
  def configAbort()

  /**
   * Actions due to a Configuration should be stopped cleanly as soon as convenient without necessarily completing.
   */
  def configCancel(){}

  /**
   * Pause the actions associated with a specific Configuration.
   */
  def configPause(){}

  /**
   * Resume the paused actions associated with a specific Configuration.
   */
  def configResume(){}
}
