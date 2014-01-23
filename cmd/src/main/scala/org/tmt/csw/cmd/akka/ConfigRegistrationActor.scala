package org.tmt.csw.cmd.akka

import akka.actor.{Terminated, ActorRef, ActorLogging, Actor}
import org.tmt.csw.cmd.akka.CommandServiceActor.StatusRequest

// Defines registration messages
object ConfigRegistrationActor {

  sealed trait RegistrationMessage

  /**
   * Requests that an actor register itself with the given actor
   * @param actorRef A Register message should be sent to this actor
   */
  case class RegisterRequest(actorRef: ActorRef) extends RegistrationMessage

  /**
   * Requests that an actor unregister itself with the given actor
   * @param actorRef A Deregister message should be sent to this actor
   */
  case class DeregisterRequest(actorRef: ActorRef) extends RegistrationMessage

  /**
   * Message used by an actor to register interest in the given set of config paths.
   * @param actorRef a reference to the sender (not the same as the implicit sender if 'ask' is used)
   * @param configPaths an optional set of dot separated path expressions, each referring to a hierarchy in a
   *                    Configuration object (Default is an empty set, meaning any/all)
   */
  case class Register(actorRef: ActorRef, configPaths: Set[String] = Set.empty[String]) extends RegistrationMessage

  /**
   * Message used by an actor to deregister interest in its config paths.
   * @param actorRef a reference to the actor that was previously registered
   */
  case class Deregister(actorRef: ActorRef) extends RegistrationMessage

  /**
   * Reply sent when registration is complete
   * @param actorRef a reference to the actor that was registered
   */
  case class Registered(actorRef: ActorRef) extends RegistrationMessage

  /**
   * Reply sent when deregistration is complete
   * @param actorRef a reference to the actor that was unregistered
   */
  case class Unregistered(actorRef: ActorRef) extends RegistrationMessage

  /**
   * Subscribe to registration information (subscriber receives registry as message)
   * @param actorRef the subscriber actor
   */
  case class Subscribe(actorRef: ActorRef) extends RegistrationMessage

  /**
   * Unsubscribe from registration information.
   * @param actorRef the actor to unsubscribe
   */
  case class Unsubscribe(actorRef: ActorRef) extends RegistrationMessage

  // Describes a config path and the actor that registered to handle it
  case class RegistryEntry(path: String, actorRef: ActorRef)
  type Registry = Set[RegistryEntry]

  /**
   * Notifies subscribers of the latest contents of the registry
   * @param registry set of registry entries for actors that registered to receive configurations
   */
  case class RegistryUpdate(registry: Registry) extends RegistrationMessage

  /**
   * Reply to StatusRequest message
   */
  case class RegistryStatus(registry: Set[RegistryEntry])
}


/**
 * Used for registering (remote) actors that will receive configurations from the command service.
 * You can "Subscribe" to the registry information to receive a message containing the registry whenever it is updated.
 */
class ConfigRegistrationActor extends Actor with ActorLogging {
  import ConfigRegistrationActor._
  import ConfigActor._

  // Set of config paths and the actors that registered to handle them
  private var registry: Registry = Set[RegistryEntry]()

  // Set of actors that subscribed to receive registry information
  private var subscribers = Set[ActorRef]()


  /**
   * Handles registration messages
   */
  override def receive: Receive = {
    case Register(actorRef, configPaths) => register(actorRef, configPaths)
    case Deregister(actorRef) => deregister(actorRef)
    case Subscribe(actorRef) => subscribe(actorRef)
    case Unsubscribe(actorRef) => unsubscribe(actorRef)
    case Terminated(actorRef) => deregister(actorRef); unsubscribe(actorRef)
    case StatusRequest => sender ! RegistryStatus(registry)

    case x => log.warning(s"Received unknown ConfigRegistrationActor message: $x")
  }

  private def register(actorRef: ActorRef, configPaths: Set[String]): Unit = {
    registry = registry ++ configPaths.map(RegistryEntry(_, actorRef))
    sender ! Registered

    // Add a listener in case the actor dies?
    context.watch(actorRef)
    registryUpdate()
  }

  private def deregister(actorRef: ActorRef): Unit = {
    registry = registry.filterNot(entry => entry.actorRef == actorRef)
    registryUpdate()
  }

  private def registryUpdate(): Unit = {
    subscribers.foreach(_ ! RegistryUpdate(registry))
  }

  private def subscribe(actorRef: ActorRef): Unit = {
    subscribers = subscribers + actorRef
    actorRef ! RegistryUpdate(registry)
    context.watch(actorRef)
  }
  private def unsubscribe(actorRef: ActorRef): Unit = {
    subscribers = subscribers - actorRef
  }

}
