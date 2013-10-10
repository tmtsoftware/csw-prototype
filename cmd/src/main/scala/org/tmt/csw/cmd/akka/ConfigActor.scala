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
   * Message used to tell a config actor to deregister with the given command service actor
   * @param commandServiceActor a reference to a CommandServiceActor
   */
  case class Deregister(commandServiceActor: ActorRef) extends ConfigActorMessage

  /**
   * Reply message sent when the registration (see above) is acknowledged.
   * @param configActor a reference to this actor
   */
  case class Registered(configActor: ActorRef) extends ConfigActorMessage

  /**
   * Reply message sent when the de-registration (see above) is acknowledged
   * @param configActor a reference to this actor
   */
  case class Unregistered(configActor: ActorRef) extends ConfigActorMessage
}


/**
 * Command service targets can extend this class, which defines
 * methods for implementing the standard configuration control messages.
 */
trait ConfigActor extends Actor with ActorLogging {

  /**
   * a set of dot-separated paths:
   * This actor will receive the parts of configs containing any of these paths.
   * An empty set indicates that all messages can be handled.
   */
  val configPaths: Set[String]

  /**
   * Messages received in the normal state.
   */
  def receiveConfigs: Receive = {
    case ConfigActor.Register(commandServiceActor) => register(commandServiceActor, sender, configPaths)
    case ConfigActor.Deregister(commandServiceActor) => deregister(commandServiceActor, sender)
    case s: SubmitWithRunId => submit(s)
    case ConfigCancel(runId) => cancel(runId)
    case ConfigAbort(runId) => abort(runId)
    case ConfigPause(runId) => pause(runId)
    case ConfigResume(runId) => resume(runId)

    // An actor was terminated (normal when done)
    case Terminated(actor) => terminated(actor)
  }

  /**
   * Register with the given command service actor to receive the parts of configs with any of the given configPaths.
   *
   * @param commandServiceActor the command service actor
   * @param replyTo reply to this actor when registration is acknowledged
   * @param configPaths if any configs containing any of these (dot separated) path are received, that
   *                    part of the config will be extracted and sent to this actor for processing
   */
  private def register(commandServiceActor: ActorRef, replyTo: ActorRef, configPaths: Set[String]): Unit = {
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
        replyTo ! ConfigActor.Registered(self)
    }
  }

  /**
   * De-register with the given command service actor.
   *
   * @param commandServiceActor the command service actor
   * @param replyTo reply to this actor when de-registration is acknowledged
   */
  private def deregister(commandServiceActor: ActorRef, replyTo: ActorRef): Unit = {
    val configDistributorActor = context.actorSelection(commandServiceActor.path / "configDistributorActor")
    implicit val timeout = Timeout(3.seconds)
    implicit val dispatcher = context.system.dispatcher
    val f = (configDistributorActor ? ConfigDistributorActor.Deregister(self)).recover {
      case ex =>
        log.error(ex, s"Failed to deregister from $configDistributorActor, retrying...")
        deregister(commandServiceActor, replyTo)
    }
    f.onSuccess {
      case ConfigDistributorActor.Unregistered =>
        log.debug(s"Deregistered config paths $configPaths with $commandServiceActor")
        replyTo ! ConfigActor.Unregistered(self)
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
