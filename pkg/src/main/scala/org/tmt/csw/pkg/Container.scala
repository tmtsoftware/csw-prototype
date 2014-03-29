package org.tmt.csw.pkg

import akka.actor._
import Component._

/**
 * From OSW TN009 - "TMT CSW PACKAGING SOFTWARE DESIGN DOCUMENT":
 *
 * A container is a software environment for deploying software components.
 * A single container can host several components. The container manages lifecycle activities like starting,
 * stopping and initialization of components. It also provides software interfaces for the services a component
 * can use to access lower-level functionality needed for the component’s operation.
 *
 * A single computer can host 0 to N containers (one per JVM).
 * A container can include zero or more components (HCDs, assemblies, sequence components).
 *
 * The Akka interface consists of messages that can be sent to the container.
 * These messages will be needed for automated startup and shutdown.
 * Supporting these messages requires one or more Akka actors that can instantiate and delete OMOA components.
 * The componentState message will allow us to take each component through a lifecycle similar to what is shown in
 * Figure 5 from AP03.
 *
 * The Container then keeps a collection of Components. If the Component includes the CommandService (some may not),
 * then it will be visible externally for commands.
 */
object Container {
  /**
   * Creates a container actor with a new ActorSystem using the given name and returns the ActorRef
   */
  def create(name: String): ActorRef = ActorSystem(name).actorOf(Props[Container], name)

  /**
   * The container handles the lifecycle of components. It accepts a few kinds of messages including:
   *
   * CreateComponent – creates a specific component with a name
   *
   * DeleteComponent – shuts a component down if it is running and removes it from the container
   *
   * SetComponentState – requires the component to enter a specific lifecycle state (XXX for future discussion).
   */
  sealed trait ContainerMessage
  case class CreateComponent(props: Props, name: String) extends ContainerMessage
  case class DeleteComponent(name: String) extends ContainerMessage
  case class SetComponentState(name: String, state: ComponentLifecycleState) extends ContainerMessage

  /**
   * Reply messages.
   * CreatedComponent - sent when a new child component was createed
   */
  sealed trait ContainerReplyMessage
  case class CreatedComponent(actorRef: ActorRef, name: String) extends ContainerMessage
}

/**
 * Implements the container actor
 */
class Container extends Actor with ActorLogging {
  import Container._

  // Receive messages
  override def receive: Receive = {
    case CreateComponent(props, name) => createComponent(props, name)
    case DeleteComponent(name) => deleteComponent(name)
    case SetComponentState(name, state) => setComponentState(name, state)
    case Terminated(actorRef) => log.info(s"Actor $actorRef terminated")
  }

  private def createComponent(props: Props, name: String): Unit = {
    context.child(name) match {
      case Some(actorRef) => log.error(s"Component $name already exists")
      case None =>
        log.info(s"Container.createComponent($name)")
        val actorRef = Component.create(props, name)
        log.info(s"Container.createComponent($name) -> $actorRef, reply to ${sender()}")
        context.watch(actorRef)
        sender ! actorRef
    }
  }

  private def deleteComponent(name: String): Unit = {
    context.child(name) match {
      case Some(actorRef) => setComponentState(name, Component.Remove)
      case None => log.error(s"Component $name does not exist")
    }
  }

  private def setComponentState(name: String, state: ComponentLifecycleState): Unit = {
    context.child(name) match {
      case Some(actorRef) => actorRef ! state
      case None => log.error(s"Component $name does not exist")
    }
  }
}

