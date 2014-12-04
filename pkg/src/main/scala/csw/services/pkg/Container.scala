package csw.services.pkg

import akka.actor._

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
   * Describes a container
   * @param system the container's actor system
   * @param container the container actor
   */
  case class ContainerInfo(system: ActorSystem, container: ActorRef)

  /**
   * Creates a container actor with a new ActorSystem using the given name and returns the ActorRef
   */
  def create(name: String): ActorRef = ActorSystem(s"$name-system").actorOf(Props[Container], name)

  /**
   * The container handles the lifecycle of components. It accepts a few kinds of messages including:
   *
   * CreateComponent – creates a specific component with a name
   *
   * DeleteComponent – shuts a component down if it is running and removes it from the container
   */
  sealed trait ContainerMessage

  case class CreateComponent(props: Props, name: String) extends ContainerMessage

  case class DeleteComponent(name: String) extends ContainerMessage

  /**
   * Reply messages.
   * CreatedComponent - sent when a new child component was createed
   */
  sealed trait ContainerReplyMessage

  case class CreatedComponent(actorRef: ActorRef, name: String) extends ContainerReplyMessage

}

/**
 * Implements the container actor
 */
class Container extends Actor with ActorLogging {

  import Container._

  // Maps component name to the info returned when creating it
  private var components = Map[String, Component.ComponentInfo]()

  // Receive messages
  override def receive: Receive = {
    case CreateComponent(props, name)   ⇒ createComponent(props, name)
    case DeleteComponent(name)          ⇒ deleteComponent(name)
    case Terminated(actorRef)           ⇒ log.info(s"Actor $actorRef terminated")
    case LifecycleManager.Stopped(name) ⇒ shutdownComponent(name)
  }

  private def createComponent(props: Props, name: String): Unit = {
    components.get(name) match {
      case Some(componentInfo) ⇒ log.error(s"Component $name already exists")
      case None ⇒
        val componentInfo = Component.create(props, name)
        components += (name -> componentInfo)
        context.watch(componentInfo.lifecycleManager)
        componentInfo.lifecycleManager.tell(LifecycleManager.Start, sender())
    }
  }

  // Deletes a component by sending a stop message to the lifecycle manager.
  // A Stopped message should be the reply. Then we shutdown the component's system.
  private def deleteComponent(name: String): Unit = {
    components.get(name) match {
      case Some(componentInfo) ⇒ componentInfo.lifecycleManager ! LifecycleManager.Stop
      case None                ⇒ log.error(s"Component $name does not exist")
    }
  }

  // Completes deleting a component by shutting down it's actor system
  // and removing it from the map
  private def shutdownComponent(name: String): Unit = {
    components.get(name) match {
      case Some(componentInfo) ⇒
        componentInfo.system.shutdown()
        components -= name
      case None ⇒ log.error(s"Component $name does not exist")
    }
  }
}

