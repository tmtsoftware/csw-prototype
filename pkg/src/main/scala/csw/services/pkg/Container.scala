package csw.services.pkg

import java.net.URI

import akka.actor._
import com.typesafe.config.Config
import csw.services.ls.LocationService.RegInfo
import csw.services.ls.LocationServiceActor.{ ServiceId, ServiceType }
import scala.collection.JavaConversions._

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
  def props(config: Config): Props = Props(classOf[Container], config)

  /**
   * Creates a container actor with a new ActorSystem based on the given config and returns the ActorRef
   */
  def create(config: Config): ActorRef = {
    import csw.util.akka.Terminator
    val name = config.getString("container.name")
    val system = ActorSystem(s"$name-system")
    val actorRef = system.actorOf(props(config), name)
    // Exit when the container shuts down
    system.actorOf(Props(classOf[Terminator], actorRef), "terminator")
    actorRef
  }

  /**
   * Describes a container
   * @param system the container's actor system
   * @param container the container actor
   */
  case class ContainerInfo(system: ActorSystem, container: ActorRef)

  /**
   * Type of messages the container receives
   */
  sealed trait ContainerMessage

  /**
   * Returns
   */
  case object GetComponents

  //  /**
  //   * Creates a component in the container
  //   * @param props used to create the component actor
  //   * @param regInfo used to register the component with the location service
  //   * @param services list of services required by the component
  //   */
  //  case class CreateComponent(props: Props, regInfo: RegInfo, services: List[ServiceId]) extends ContainerMessage
  //
  //  /**
  //   * Deletes the component from the container
  //   * @param name name of the component
  //   */
  //  case class DeleteComponent(name: String) extends ContainerMessage

  /**
   * Reply messages.
   */
  sealed trait ContainerReplyMessage

  /**
   * Reply to GetComponents
   * @param map a map of component name to actor for the component (actually the lifecycle manager)
   */
  case class Components(map: Map[String, ActorRef])

  //  /**
  //   * Reply sent when a new child component was created.
  //   * @param actorRef the new actor
  //   * @param name the name of the component
  //   */
  //  case class CreatedComponent(actorRef: ActorRef, name: String) extends ContainerReplyMessage

}

/**
 * Implements the container actor based on the contents of the given config.
 * XXX TODO: Document config format.
 */
class Container(config: Config) extends Actor with ActorLogging {

  import LifecycleManager._
  import Container._

  // Maps component name to the info returned when creating it
  private val components = parseConfig()

  // Startup the components
  allComponents(Startup)

  // Receive messages
  override def receive: Receive = {
    case Initialize           ⇒ allComponents(Initialize)
    case Startup              ⇒ allComponents(Startup)
    case Shutdown             ⇒ allComponents(Shutdown)
    case Uninitialize         ⇒ allComponents(Uninitialize)

    case GetComponents        ⇒ sender() ! getComponents

    case Terminated(actorRef) ⇒ componentDied(actorRef)

    case x                    ⇒ log.error(s"Unexpected message: $x")
  }

  private def createComponent(props: Props, regInfo: RegInfo, services: List[ServiceId],
                              components: Map[String, Component.ComponentInfo]): Option[Component.ComponentInfo] = {
    val name = regInfo.serviceId.name
    components.get(name) match {
      case Some(componentInfo) ⇒
        log.error(s"Component $name already exists")
        None
      case None ⇒
        val componentInfo = Component.create(props, regInfo, services)
        context.watch(componentInfo.lifecycleManager)
        Some(componentInfo)
    }
  }

  // Called whan a component (lifecycle manager) terminates
  private def componentDied(actorRef: ActorRef): Unit = {
    log.info(s"Actor $actorRef terminated")
  }

  // Parses the config file argument and creates the container,
  // adding the components specified in the config file.
  private def parseConfig(): Map[String, Component.ComponentInfo] = {
    val conf = config.getConfig("container.components")
    val names = conf.root.keySet().toList
    val entries = for {
      key ← names
      value ← parseComponentConfig(key, conf.getConfig(key))
    } yield (key, value)
    Map(entries: _*)
  }

  // Parse the "components" section of the config file
  private def parseComponentConfig(name: String, conf: Config): Option[Component.ComponentInfo] = {
    val className = conf.getString("class")
    val args =
      if (conf.hasPath("args"))
        conf.getList("args").toList.map(_.unwrapped().toString)
      else List()
    log.info(s"Create component with class $className and args $args")
    val props = Props(Class.forName(className), args: _*)
    val serviceType = ServiceType(conf.getString("type"))
    if (serviceType == ServiceType.Unknown) {
      log.error(s"Unknown service type: ${conf.getString("type")}")
      None
    } else {
      val serviceId = ServiceId(name, serviceType)
      val configPath = if (conf.hasPath("path")) Some(conf.getString("path")) else None
      val uri = if (conf.hasPath("uri")) Some(new URI(conf.getString("uri"))) else None
      val regInfo = RegInfo(serviceId, configPath, uri)
      val services = if (conf.hasPath("services"))
        parseServices(conf.getConfig("services"))
      else Nil
      createComponent(props, regInfo, services, Map.empty)
    }
  }

  // Parse the "services" section of the component config
  private def parseServices(conf: Config): List[ServiceId] = {
    for (key ← conf.root.keySet().toList) yield ServiceId(key, ServiceType(conf.getString(key)))
  }

  // Sends the given lifecycle command to all components
  private def allComponents(cmd: LifecycleCommand): Unit =
    for ((name, info) ← components) {
      info.lifecycleManager ! Startup
    }

  private def getComponents: Components =
    Components(components.mapValues(_.lifecycleManager))
}

