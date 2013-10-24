package org.tmt.csw.cmd.akka

import akka.actor.{ActorLogging, ActorRef, Actor}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask

/**
 * Adds support to an actor for receiving and replying to registration requests.
 * When an actor receives a Register(actorRef) message, it registers itself with the given actor.
 */
trait ConfigRegistrationClient {
  this: Actor with ActorLogging =>
  import ConfigRegistrationActor._

  /**
   * a set of dot-separated paths:
   * This actor will receive the parts of configs containing any of these paths.
   * An empty set indicates that all messages can be handled.
   */
  def configPaths: Set[String]

  /**
   * Messages received in the normal state.
   */
  def receiveRegistrationRequest: Receive = {
    case RegisterRequest(actorRef) => register(actorRef, sender, configPaths)
    case DeregisterRequest(actorRef) => deregister(actorRef, sender)
  }

  /**
   * Register with the given command service actor to receive the parts of configs with any of the given configPaths.
   *
   * @param actorRef send a Register message to this actor
   * @param replyTo reply to this actor when registration is acknowledged
   * @param configPaths if any configs containing any of these (dot separated) path are received, that
   *                    part of the config will be extracted and sent to this actor for processing
   */
  private def register(actorRef: ActorRef, replyTo: ActorRef, configPaths: Set[String]): Unit = {
    implicit val timeout = Timeout(3.seconds)
    implicit val dispatcher = context.system.dispatcher
    val f = (actorRef ? Register(self, configPaths)).recover {
      case ex =>
        log.error(ex, s"Failed to register $configPaths with $actorRef, retrying...")
        register(actorRef, replyTo, configPaths)
    }
    f.onSuccess {
      case Registered =>
        log.info(s"Registered config paths $configPaths with $actorRef, reply to $replyTo")
        replyTo ! Registered(self)
    }
  }

  /**
   * Deregister with the given command service actor.
   *
   * @param actorRef the actor to send the Deregister message to
   * @param replyTo reply to this actor when deregistration is acknowledged
   */
  private def deregister(actorRef: ActorRef, replyTo: ActorRef): Unit = {
    implicit val timeout = Timeout(3.seconds)
    implicit val dispatcher = context.system.dispatcher
    val f = (actorRef ? Deregister(self)).recover {
      case ex =>
        log.error(ex, s"Failed to deregister from $actorRef., retrying...")
        deregister(actorRef, replyTo)
    }
    f.onSuccess {
      case Unregistered =>
        log.debug(s"Deregistered config paths $configPaths from $actorRef")
        replyTo ! Unregistered(self)
    }
  }

}
