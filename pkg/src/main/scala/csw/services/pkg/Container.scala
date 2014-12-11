package csw.services.pkg

import akka.actor._
import com.typesafe.config.Config
import csw.services.ls.LocationService.RegInfo

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
 *
 * See also "OSW TN012 Component Lifecycle Design".
 */
object Container {

  /**
   * Used to create the actor
   */
  def props(name: String): Props = Props(classOf[Container], name)

  /**
   * Creates a container actor with a new ActorSystem based on the given config and returns the ActorRef
   */
  def create(config: Config): ActorRef = {
    val name = config.getString("container.name")
    ActorSystem(s"$name-system").actorOf(props(name), name)
  }

  /**
   * Describes a container
   * @param system the container's actor system
   * @param container the container actor
   */
  case class ContainerInfo(system: ActorSystem, container: ActorRef)

  /**
   * The container handles the lifecycle of components. It accepts a few kinds of messages including:
   *
   * CreateComponent – creates a specific component with a name
   *
   * DeleteComponent – shuts a component down if it is running and removes it from the container
   */
  sealed trait ContainerMessage

  case class CreateComponent(props: Props, regInfo: RegInfo) extends ContainerMessage

  case class DeleteComponent(name: String) extends ContainerMessage

  /**
   * Reply messages.
   * CreatedComponent - sent when a new child component was createed
   */
  sealed trait ContainerReplyMessage

  case class CreatedComponent(actorRef: ActorRef, name: String) extends ContainerReplyMessage

}

/**
 * Implements the container actor based on the contents of the given config.
 * XXX TODO: Add description of format, pointer to example file
 */
class Container(name: String) extends Actor with ActorLogging {

  import Container._

  // Maps component name to the info returned when creating it
  private var components = Map[String, Component.ComponentInfo]()

  // Receive messages
  override def receive: Receive = {
    case CreateComponent(props, regInfo) ⇒ createComponent(props, regInfo)
    case DeleteComponent(name)           ⇒ deleteComponent(name)
    case Terminated(actorRef)            ⇒ componentDied(actorRef)
    case LifecycleManager.Stopped(name)  ⇒ shutdownComponent(name)

      /*
       case Initialize =>
      sender() ! InitializeFailed(name, LifecycleTransitionError(currentState, Initialize))

    case Startup =>
      sender() ! StartupFailed(name, LifecycleTransitionError(currentState, Startup))

    case Shutdown =>
      sender() ! ShutdownFailed(name, LifecycleTransitionError(currentState, Shutdown))

    case Uninitialize =>
      sender() ! UninitializeFailed(name, LifecycleTransitionError(currentState, Uninitialize))

    // Message from component confirming current state
    case Loaded(n) => updateState(n, component, _, loaded(component))
    case Initialized(n) => updateState(n, component, _, initialized(component))
    case Running(n) => updateState(n, component, _, running(component))

       */
  }

  private def createComponent(props: Props, regInfo: RegInfo): Unit = {
    val name = regInfo.serviceId.name
    components.get(name) match {
      case Some(componentInfo) ⇒ log.error(s"Component $name already exists")
      case None ⇒
        val componentInfo = Component.create(props, regInfo)
        components += (name -> componentInfo)
        context.watch(componentInfo.lifecycleManager)
        componentInfo.lifecycleManager.tell(LifecycleManager.Start, sender())
    }
  }

//  // Deletes a component by sending a stop message to the lifecycle manager.
//  // A Stopped message should be the reply. Then we shutdown the component's system.
//  private def deleteComponent(name: String): Unit = {
//    components.get(name) match {
//      case Some(componentInfo) ⇒ componentInfo.lifecycleManager ! LifecycleManager.Stop
//      case None                ⇒ log.error(s"Component $name does not exist")
//    }
//  }

//  // Completes deleting a component by shutting down it's actor system
//  // and removing it from the map
//  private def shutdownComponent(name: String): Unit = {
//    components.get(name) match {
//      case Some(componentInfo) ⇒
//        componentInfo.system.shutdown()
//        components -= name
//      case None ⇒ log.error(s"Component $name does not exist")
//    }
//  }

  // Called whan a component (lifecycle manager) terminates
  private def componentDied(actorRef: ActorRef): Unit = {
    // TODO: recreate if component was not deleted (restart count?)
    log.info(s"Actor $actorRef terminated")
  }
}

