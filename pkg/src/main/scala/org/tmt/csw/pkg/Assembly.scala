package org.tmt.csw.pkg

import akka.actor.{ActorPath, ActorLogging, ActorRef, Actor}
import org.tmt.csw.cmd.akka.{CommandServiceActor, ConfigActor}
import akka.pattern.ask
import scala.concurrent.duration.FiniteDuration
import akka.util.Timeout
import scala.concurrent.duration._


object Assembly {
  // Assembly actor messages
  sealed trait AssemblyMessage
  // Adds a component actor to the assembly
  case class AddComponent(actorRef: ActorRef) extends AssemblyMessage
  case class AddComponentByPath(actorPath: ActorPath) extends AssemblyMessage
  // Removes a component actor from the assembly
  case class RemoveComponent(actorRef: ActorRef) extends AssemblyMessage
  case class RemoveComponentByPath(actorPath: ActorPath) extends AssemblyMessage
}

/**
 * Assemblies represent user-oriented devices and can be assembled from multiple HCDs
 */
trait Assembly extends Component with CommandServiceActor {
  this: Actor with ActorLogging =>
  import Assembly._

  val duration: FiniteDuration = 5.seconds
  implicit val timeout = Timeout(duration)

  // Receive actor messages
  def receiveAssemblyMessages: Receive = receiveComponentMessages orElse receiveCommands orElse {
    case AddComponent(actorRef) => addComponent(actorRef)
    case AddComponentByPath(actorPath) => addComponentByPath(actorPath)
    case RemoveComponent(actorRef) => removeComponent(actorRef)
    case RemoveComponentByPath(actorPath) => removeComponentByPath(actorPath)
  }

  /**
   * Adds a component actor (Assembly, Hcd, ...) to this assembly.
   * The given actor is told to register itself with the command service for this assembly.
   * The sender should receive a ConfigActor.Registered(actorRef) message.
   * @param actorRef an Hcd or Assembly actor
   */
  def addComponent(actorRef: ActorRef): Unit = {
    actorRef.tell(ConfigActor.Register(self), sender)
  }

  /**
   * Adds a component actor (Assembly, Hcd, ...) to this assembly.
   * The given actor is told to register itself with the command service for this assembly.
   * The sender should receive a ConfigActor.Registered(actorRef) message.
   * @param actorPath an Hcd or Assembly actor
   */
  def addComponentByPath(actorPath: ActorPath): Unit = {
    log.info(s"XXX addComponent: actorSel=${context.actorSelection(actorPath)}, sender= $sender, self = $self")
    context.actorSelection(actorPath).tell(ConfigActor.Register(self), sender)
  }

  /**
   * Removes the component associated with the given actor from this assembly.
   * The given actor is told to deregister itself with the command service for this assembly.
   * The sender should receive a ConfigActor.Unregistered(actorRef) message.
   * @param actorRef a previously added Hcd or Assembly actor
   */
  def removeComponent(actorRef: ActorRef): Unit = actorRef.tell(ConfigActor.Deregister(self), sender)

  /**
   * Removes the component associated with the given actor from this assembly.
   * The given actor is told to deregister itself with the command service for this assembly.
   * The sender should receive a ConfigActor.Unregistered(actorRef) message.
   * @param actorPath a previously added Hcd or Assembly actor
   */
  def removeComponentByPath(actorPath: ActorPath): Unit = context.actorSelection(actorPath).tell(ConfigActor.Deregister(self), sender)

  override def terminated(actorRef: ActorRef): Unit = log.info(s"Actor $actorRef terminated")
}
