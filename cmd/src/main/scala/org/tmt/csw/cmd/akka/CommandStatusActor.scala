package org.tmt.csw.cmd.akka

import akka.actor.{Terminated, ActorRef, Actor, ActorLogging}

// Defines messages used by the CommandStatusActor
object CommandStatusActor {

  sealed trait ConfigStatusActorMessage

  /**
   * Subscribe to command status information (subscriber receives command status messages)
   * @param actorRef the subscriber actor
   */
  case class Subscribe(actorRef: ActorRef)
    extends ConfigStatusActorMessage

  /**
   * Unsubscribe from command status information.
   * @param actorRef the actor to unsubscribe
   */
  case class Unsubscribe(actorRef: ActorRef) extends ConfigStatusActorMessage

  /**
   * Message used to report the status of a command
   * @param status the command status
   * @param originalSubmitter the original submitter of the command
   */
  case class StatusUpdate(status: CommandStatus, originalSubmitter: ActorRef)
}

/**
 * Manages a list of subscribers who want to be informed of the status
 * of executing configurations.
 */
class CommandStatusActor extends Actor with ActorLogging {
  import CommandStatusActor._

  // Collection of actors that subscribed to receive command status information
  private var subscribers = Set[ActorRef]()

  override def receive: Receive = {
    case Subscribe(actorRef) => subscribe(actorRef)
    case Unsubscribe(actorRef) => unsubscribe(actorRef)
    case Terminated(actorRef) => unsubscribe(actorRef)
    case StatusUpdate(commandStatus, originalSubmitter) => notifySubscribers(commandStatus, originalSubmitter)
  }

  private def subscribe(actorRef: ActorRef): Unit = {
    subscribers = subscribers + actorRef
  }
  private def unsubscribe(actorRef: ActorRef): Unit = {
    subscribers = subscribers - actorRef
  }

  private def notifySubscribers(commandStatus: CommandStatus, originalSubmitter: ActorRef): Unit = {
    originalSubmitter ! commandStatus
    subscribers.foreach(_ ! commandStatus)
  }
}
