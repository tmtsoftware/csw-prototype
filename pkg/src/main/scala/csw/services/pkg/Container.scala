package csw.services.pkg

import akka.actor._
import com.typesafe.config.Config
import csw.services.loc.{ LocationService, ServiceType, ServiceId }

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
   * Requests information about the components being managed by the container (A Components(map) object is sent to the sender)
   */
  case object GetComponents extends ContainerMessage

  /**
   * Tells the container to uninitialize all of its components.
   */
  case object Stop extends ContainerMessage

  /**
   * Tells the container to stop all its components and then quit, ending execution of the container process.
   */
  case object Halt extends ContainerMessage

  /**
   * Indicates the container should take all its component to uninitialized and then to running.
   */
  case object Restart extends ContainerMessage

  /**
   * Reply messages.
   */
  sealed trait ContainerReplyMessage

  /**
   * Reply to GetComponents
   * @param map a map of component name to actor for the component (actually the lifecycle manager)
   */
  case class Components(map: Map[String, ActorRef]) extends ContainerReplyMessage

}

/**
 * Implements the container actor based on the contents of the given config.
 */
class Container(config: Config) extends Actor with ActorLogging {

  import csw.services.pkg.Container._
  import csw.services.pkg.LifecycleManager._

  // Maps component name to the info returned when creating it
  private val components = parseConfig()

  registerWithLocationService()

  // Receive messages
  override def receive: Receive = {
    case cmd: LifecycleCommand ⇒ allComponents(cmd)

    case GetComponents         ⇒ sender() ! getComponents

    case Stop                  ⇒ stop()
    case Halt                  ⇒ halt()
    case Restart               ⇒ restart()

    case Terminated(actorRef)  ⇒ componentDied(actorRef)

    case x                     ⇒ log.error(s"Unexpected message: $x")
  }

  // Starts an actor to manage registering this actor with the location service
  // (as a proxy for the component)
  private def registerWithLocationService(): Unit = {
    val name = config.getString("container.name")
    val serviceId = ServiceId(name, ServiceType.Container)
    LocationService.registerAkkaService(serviceId, self)(context.system)
  }

  private def createComponent(props: Props, serviceId: ServiceId, prefix: String, services: List[ServiceId],
                              components: Map[String, Component.ComponentInfo]): Option[Component.ComponentInfo] = {
    val name = serviceId.name
    components.get(name) match {
      case Some(componentInfo) ⇒
        log.error(s"Component $name already exists")
        None
      case None ⇒
        val componentInfo = Component.create(props, serviceId, prefix, services)
        context.watch(componentInfo.lifecycleManager)
        Some(componentInfo)
    }
  }

  // Called when a component (lifecycle manager) terminates
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
      val configPath = if (conf.hasPath("path")) conf.getString("path") else ""
      val services = if (conf.hasPath("services")) parseServices(conf.getConfig("services")) else Nil
      createComponent(props, serviceId, configPath, services, Map.empty)
    }
  }

  // Parse the "services" section of the component config
  private def parseServices(conf: Config): List[ServiceId] = {
    for (key ← conf.root.keySet().toList) yield ServiceId(key, ServiceType(conf.getString(key)))
  }

  // Sends the given lifecycle command to all components
  private def allComponents(cmd: LifecycleCommand): Unit =
    for ((name, info) ← components) {
      info.lifecycleManager ! cmd
    }

  private def getComponents: Components =
    Components(components.mapValues(_.lifecycleManager))

  // Tell all components to uninitialize
  private def stop(): Unit = {
    allComponents(Uninitialize)
  }

  // Tell all components to uninitialize and start an actor to wait until they do before exiting.
  private def halt(): Unit = {
    context.actorOf(ContainerUninitializeActor.props(components, exit = true))
  }

  // Tell all components to uninitialize and start an actor to wait until they do before restarting them.
  private def restart(): Unit = {
    context.actorOf(ContainerUninitializeActor.props(components, exit = false))
  }
}

