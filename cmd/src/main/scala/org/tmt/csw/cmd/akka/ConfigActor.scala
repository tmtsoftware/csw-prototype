package org.tmt.csw.cmd.akka

import akka.actor.{ActorLogging, Actor, ActorRef, Terminated}


object ConfigActor {

  // -- Messages that operate on a running configuration --
  sealed trait ConfigMessage

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


/**
 * Command service targets can extend this class, which defines
 * methods for implementing the standard configuration control messages.
 */
trait ConfigActor extends Actor with ActorLogging {
  import ConfigActor._
  import CommandQueueActor._

  /**
   * A reference to this actor is needed to report the status of commands
   */
  def commandStatusActor: ActorRef

  /**
   * Messages received in the normal state.
   */
  def receiveConfigs: Receive = {
    case s: SubmitWithRunId => submit(s)
    case ConfigCancel(runId) => cancel(runId)
    case ConfigAbort(runId) => abort(runId)
    case ConfigPause(runId) => pause(runId)
    case ConfigResume(runId) => resume(runId)

    // An actor was terminated (normal when done)
    case Terminated(actor) => terminated(actor)
  }

  /**
   * Called when a configuration is submitted
   */
  def submit(submit: SubmitWithRunId): Unit

  /**
   * Report the command status to the command status actor.
   * All extending should call this to report the command status.
   * @param status the command status
   * @param submitter the (original) submitter of the command
   */
  def returnStatus(status: CommandStatus, submitter: ActorRef): Unit = {
    commandStatusActor ! CommandStatusActor.StatusUpdate(status, submitter)
  }

  /**
   * Work on the config matching the given runId should be paused
   */
  def pause(runId: RunId): Unit

  /**
   * Work on the config matching the given runId should be resumed
   */
  def resume(runId: RunId): Unit

  /**
   * Work on the config matching the given runId should be canceled
   */
  def cancel(runId: RunId): Unit

  /**
   * Work on the config matching the given runId should be aborted
   */
  def abort(runId: RunId): Unit

  /**
   * Called when a child (worker) actor terminates
   */
  def terminated(actorRef: ActorRef): Unit = {
    log.debug(s"Actor $actorRef terminated")
  }
}
