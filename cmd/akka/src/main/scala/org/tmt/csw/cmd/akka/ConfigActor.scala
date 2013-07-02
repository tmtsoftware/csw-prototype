package org.tmt.csw.cmd.akka

import akka.actor.{ActorRef, Terminated, ActorLogging, Actor}
import org.tmt.csw.cmd.akka.CommandServiceMessage._
import akka.pattern.ask
import akka.actor.Status._
import akka.util.Timeout
import scala.concurrent.duration._
import org.tmt.csw.cmd.akka.ConfigDistributorActor.Registered

/**
 * Command service targets can extend this class, which defines
 * methods for implementing the standard configuration control messages.
 */
abstract class ConfigActor() extends Actor with ActorLogging {

  /**
   * Register with the given command service actor name to receive parts of configs with the given configPath.
   * This method can be called multiple times with different paths or actors as needed.
   *
   * @param name the Akka name used to create the command service actor
   * @param configPath if any configs containing this (dot separated) path are received, that
   *                   part of the config will be extracted and sent to this actor for processing
   */
  def register(name: String, configPath: String) {
    val configDistributorActorPath = s"akka://${context.system.name}/user/$name/configDistributorActor"
    implicit val timeout = Timeout(3.seconds)
    implicit val dispatcher = context.system.dispatcher
    val f = (context.actorSelection(configDistributorActorPath) ? ConfigDistributorActor.Register(configPath, self)).recover {
      case ex =>
        log.error(ex, s"Failed to register $configPath with $configDistributorActorPath, retrying...")
        register(configDistributorActorPath, configPath)
    }
    f.onSuccess {
      case Registered(path) => log.debug(s"Registered config path $path")
    }
  }

  /**
   * Messages received in the normal state.
   */
  def receive = {
    case s: SubmitWithRunId => submit(s)
    case ConfigCancel(runId) => cancel(runId)
    case ConfigAbort(runId) => abort(runId)
    case ConfigPause(runId) => pause(runId) // XXX use become(paused)
    case ConfigResume(runId) => resume(runId) // XXX use become(receive)

    // An actor was terminated (normal when done)
    case Terminated(actor) => terminated(actor)

    case x => log.error(s"Unexpected ConfigActor message: $x")
  }

  /**
   * Called when a configuration is submitted
   */
  def submit(submit: SubmitWithRunId)

  /**
   * Work on the config matching the given runId should be paused
   */
  def pause(runId: RunId)

  /**
   * Work on the config matching the given runId should be resumed
   */
  def resume(runId: RunId)

  /**
   * Work on the config matching the given runId should be canceled
   */
  def cancel(runId: RunId)

  /**
   * Work on the config matching the given runId should be aborted
   */
  def abort(runId: RunId)

  /**
   * Called when a child (worker) actor terminates
   */
  def terminated(actorRef: ActorRef) {
    log.debug(s"Actor $actorRef terminated")
  }
}
