package org.tmt.csw.cmd.akka

import akka.actor.{ActorRef, Terminated, ActorLogging, Actor}
import org.tmt.csw.cmd.akka.CommandServiceMessage._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._


object ConfigActor {

  sealed trait ConfigActorMessage
  /**
   * Message used to tell a config actor to register with the given command service actor
   * @param commandServiceActor a reference to a CommandServiceActor
   */
  case class Register(commandServiceActor: ActorRef) extends ConfigActorMessage

  /**
   * Reply message sent when the registration (see above) is acknowledged
   */
  case object Registered extends ConfigActorMessage
}


/**
 * Command service targets can extend this class, which defines
 * methods for implementing the standard configuration control messages.
 *
 * @param configPaths a set of dot-separated paths: This actor will receive the parts
 *                    of configs containing any of these paths
 */
abstract class ConfigActor(configPaths: Set[String]) extends Actor with ActorLogging {

  /**
   * Initialize with a single config path
   * @param configPath a dot-separated path in a Configuration object: This actor will receive this part
   *                    of any configs containing this path
   */
  def this(configPath: String) = this(Set(configPath))

  /**
   * Messages received in the normal state.
   */
  override def receive: Receive = {
    case ConfigActor.Register(commandServiceActor) => register(commandServiceActor, sender, configPaths)
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
   * Register with the given command service actor to receive the parts of configs with any of the given configPaths.
   *
   * @param commandServiceActor the Akka name used to create the command service actor
   * @param replyTo reply to this actor when registration is acknowledged
   * @param configPaths if any configs containing any of these (dot separated) path are received, that
   *                    part of the config will be extracted and sent to this actor for processing
   */
  def register(commandServiceActor: ActorRef, replyTo: ActorRef, configPaths: Set[String]): Unit = {
    val configDistributorActor = context.actorSelection(commandServiceActor.path / "configDistributorActor")
    implicit val timeout = Timeout(3.seconds)
    implicit val dispatcher = context.system.dispatcher
    val f = (configDistributorActor ? ConfigDistributorActor.Register(configPaths, self)).recover {
      case ex =>
        log.error(ex, s"Failed to register $configPaths with $configDistributorActor, retrying...")
        register(commandServiceActor, replyTo, configPaths)
    }
    f.onSuccess {
      case ConfigDistributorActor.Registered =>
        log.debug(s"Registered config paths $configPaths with $commandServiceActor")
        replyTo ! ConfigActor.Registered
    }
  }

  /**
   * Called when a configuration is submitted
   */
  def submit(submit: SubmitWithRunId): Unit

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
