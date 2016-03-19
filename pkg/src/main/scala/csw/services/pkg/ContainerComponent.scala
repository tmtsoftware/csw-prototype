package csw.services.pkg

import akka.actor._
import com.typesafe.config.Config
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.loc.LocationService
import csw.services.pkg.Component._
import csw.services.pkg.LifecycleManager.{ LifecycleCommand, Loaded, Startup, Uninitialize }
import csw.services.pkg.Supervisor.{ HaltComponent, LifecycleStateChanged, SubscribeLifecycleCallback, UnsubscribeLifecycleCallback }
import csw.util.Components._
import org.slf4j.LoggerFactory

import scala.language.higherKinds
import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/**
 * TMT Source Code: 3/5/16.
 */
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
object ContainerComponent {
  private val logger = Logger(LoggerFactory.getLogger("ContainerComponent"))

  // for parsing of file
  val CONTAINER = "container"
  val TYPE = "type"
  val CLASS = "class"
  val PREFIX = "prefix"
  val CONNTYPE = "conntype"
  val CONNECTIONS = "connections"
  val NAME = "name"
  val RATE = "rate"
  val DELAY = "delay"
  val INITIAL_DELAY = "initialdelay"
  val CREATION_DELAY = "creationdelay"
  val LIFECYCLE_DELAY = "lifecycledelay"

  val DEFAULT_INITIAL_DELAY = 1.seconds
  val DEFAULT_CREATION_DELAY = 1.seconds
  val DEFAULT_LIFECYCLE_DELAY = 1.seconds

  val DEFAULT_CONNECTION_TYPE = Set(AkkaType)
  /**
   * Used to create the actor
   */
  def props(config: Config): Props = Props(ContainerComponent(config))

  def props(containerInfo: ContainerInfo): Props = Props(classOf[ContainerComponent], containerInfo)

  /**
   * Creates a container actor with a new ActorSystem based on the given config and returns the ActorRef
   */
  def create(config: Config): ActorRef = {
    val containerInfo = parseConfigToContainerInfo(config)
    create(containerInfo)
  }

  def create(containerInfo: ContainerInfo): ActorRef = {
    val name = containerInfo.componentName
    val system = ActorSystem(s"$name-system")
    val actorRef = system.actorOf(props(containerInfo), name)
    // Exit when the container shuts down
    system.actorOf(Props(classOf[Terminator], actorRef), "terminator")
    actorRef
  }

  /**
   * Exits the application when the given actor stops
   *
   * @param ref reference to the main actor of an application
   */
  class Terminator(ref: ActorRef) extends Actor with ActorLogging {
    context watch ref

    def receive = {
      case Terminated(_) ⇒
        log.info("{} has terminated, shutting down system", ref.path)
        context.system.terminate()
    }
  }

  // Parsing Exception
  case class ConfigurationParsingException(message: String) extends Exception(message)

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

  case class CreateComponents(infos: List[ComponentInfo]) extends ContainerMessage

  case class LifecycleToAll(cmd: LifecycleCommand) extends ContainerMessage

  /**
   * Reply messages.
   */
  sealed trait ContainerReplyMessage

  /**
   * Reply to GetComponents
   *
   * @param components a list of component name to actor for the component (actually the lifecycle manager)
   */
  case class Components(components: List[SupervisorInfo]) extends ContainerReplyMessage

  // Parses the config file argument and creates the container,
  // adding the components specified in the config file.
  private[pkg] def parseConfig(config: Config): List[ComponentInfo] = {
    val conf = config.getConfig("container.components")
    val names = conf.root.keySet().toList
    val entries = for {
      key ← names
      value ← parseComponentConfig(key, conf.getConfig(key))
    } yield value
    List(entries: _*)
  }

  // Parse the "components" section of the config file
  private[pkg] def parseComponentConfig(name: String, conf: Config): Option[ComponentInfo] = {
    try {
      val componentType = ComponentType(conf.getString(TYPE))
      val info = componentType match {
        case HCD      ⇒ parseHcd(name, conf)
        case Assembly ⇒ parseAssembly(name, conf)
        case _        ⇒ None
      }
      info
    } catch {
      case UnknownComponentTypeException(msg) ⇒
        logger.error(s"Unknown component type: ${conf.getString("type")}")
        None
    }
  }

  private[pkg] def parseName(name: String, conf: Config): String = {
    if (!conf.hasPath(NAME)) throw ConfigurationParsingException(s"Missing configuration field: >$NAME< in connections for component: $name")
    conf.getString(NAME)
  }

  private[pkg] def parseClassName(name: String, conf: Config): String = {
    if (!conf.hasPath(CLASS)) throw ConfigurationParsingException(s"Missing configuration field: >$CLASS< for component: $name")
    conf.getString(CLASS)
  }

  private[pkg] def parsePrefix(name: String, conf: Config): String = {
    if (!conf.hasPath(PREFIX)) throw ConfigurationParsingException(s"Missing configuration field: >$PREFIX< for component: $name")
    conf.getString(PREFIX)
  }

  private[pkg] def parseComponentId(name: String, conf: Config): ComponentId = {
    if (!conf.hasPath(TYPE)) throw ConfigurationParsingException(s"Missing configuration field: >$TYPE< for component: $name")
    try {
      val componentType = ComponentType(conf.getString(TYPE))
      ComponentId(name, componentType)
    } catch {
      case UnknownComponentTypeException(msg) ⇒
        throw ConfigurationParsingException(s"Unknown component type: >$msg< for component: $name")
    }
  }

  // Parse the "conntype" section of the component config
  private[pkg] def parseConnType(name: String, conf: Config): Set[ConnectionType] = {
    if (!conf.hasPath(CONNTYPE)) throw ConfigurationParsingException(s"Missing configuration field: >$CONNTYPE< for component: $name")
    try {
      conf.getStringList(CONNTYPE).map(ctype ⇒ ConnectionType(ctype)).toSet
    } catch {
      case UnknownConnectionTypeException(msg) ⇒
        throw ConfigurationParsingException(s"Unknown component type in list: >${conf.getStringList(CONNTYPE)}< for component: $name")
    }
  }

  // Parse the "conntype" section of the component config
  private[pkg] def parseConnTypeWithDefault(name: String, conf: Config, default: Set[ConnectionType]): Set[ConnectionType] = {
    try {
      parseConnType(name, conf)
    } catch {
      case UnknownConnectionTypeException(msg) ⇒
        default
      case ConfigurationParsingException(msg) ⇒
        default
    }
  }

  // Parse the "services" section of the component config
  private[pkg] def parseRate(name: String, conf: Config): FiniteDuration = {
    import scala.concurrent.duration._
    if (!conf.hasPath(RATE)) throw ConfigurationParsingException(s"Missing configuration field: >$RATE< for component: $name")
    try {
      val s1 = conf.getString(RATE).split(" ")
      assert(s1.length == 2)
      FiniteDuration(s1(0).toLong, s1(1))
    } catch {
      case e: Exception ⇒
        logger.error(s"HCD rate for >$name< is not valid, returning: >1 second<.")
        1.second
    }
  }

  private[pkg] def parseDuration(name: String, configName: String, conf: Config, defaultDuration: FiniteDuration): FiniteDuration = {
    import scala.concurrent.duration._
    if (!conf.hasPath(configName)) return defaultDuration
    try {
      val s1 = conf.getString(configName).split(" ")
      assert(s1.length == 2)
      FiniteDuration(s1(0).toLong, s1(1))
    } catch {
      case e: Exception ⇒
        logger.error(s"Container delay for >$name< is not valid, returning: >1 second<.")
        defaultDuration
    }
  }

  private[pkg] def parseConnections(name: String, conf: Config): Set[Connection] = {
    if (!conf.hasPath(CONNECTIONS)) throw ConfigurationParsingException(s"Missing configuration field: >$CONNECTIONS< for Assembly: $name")
    val connections = conf.getConfigList(CONNECTIONS).flatMap { conf: Config ⇒
      val connName = parseName(name, conf)
      val componentId = parseComponentId(connName, conf)
      val connTypes = parseConnType(connName, conf)
      connTypes.map(ctype ⇒ Connection(componentId, ctype))
    }
    connections.toSet
  }

  // Parse the "services" section of the component config
  private[pkg] def parseHcd(name: String, conf: Config): Option[HcdInfo] = {
    val componentClassName = parseClassName(name, conf)
    val prefix = parsePrefix(name, conf)
    val registerAs = parseConnType(name, conf)
    val cycle = parseRate(name, conf)
    Some(HcdInfo(name, prefix, componentClassName, RegisterOnly, registerAs, cycle))
  }

  // Parse the "services" section of the component config
  private[pkg] def parseAssembly(name: String, conf: Config): Option[AssemblyInfo] = {
    try {
      val componentClassName = parseClassName(name, conf)
      val prefix = parsePrefix(name, conf)
      val registerAs = parseConnType(name, conf)
      val connections = parseConnections(name, conf)
      Some(AssemblyInfo(name, prefix, componentClassName, RegisterAndTrackServices, registerAs, connections))
    } catch {
      case UnknownComponentTypeException(msg) ⇒
        logger.error(s"Unknown component type: ${conf.getString(TYPE)}")
        None
      case UnknownConnectionTypeException(msg) ⇒
        logger.error(s"Unknown connection type: ${conf.getString(CONNTYPE)}")
        None
      case _: Throwable ⇒
        logger.error(s"An error occurred while parsing Assembly info for: $name")
        None
    }
  }

  private[pkg] def parseConfigToContainerInfo(config: Config): ContainerInfo = {
    val componentConfigs: List[ComponentInfo] = parseConfig(config)

    val containerConfig = config.getConfig(CONTAINER)
    val name = parseName("container", containerConfig)
    val initialDelay = parseDuration(name, INITIAL_DELAY, containerConfig, DEFAULT_INITIAL_DELAY)
    logger.info("Initial delay: " + initialDelay)
    val creationDelay = parseDuration(name, CREATION_DELAY, containerConfig, DEFAULT_CREATION_DELAY)
    logger.info("Creation delay: " + creationDelay)
    val lifecycleDelay = parseDuration(name, LIFECYCLE_DELAY, containerConfig, DEFAULT_LIFECYCLE_DELAY)
    logger.info("Lifecycle delay: " + lifecycleDelay)

    // For container, if no conntype, set to Akka
    val registerAs = parseConnTypeWithDefault(name, containerConfig, Set(AkkaType))

    val cinfo: ContainerInfo = ContainerInfo(name, RegisterOnly, registerAs, initialDelay, creationDelay, lifecycleDelay, componentConfigs)
    cinfo
  }

  case class SupervisorInfo(supervisor: ActorRef, componentInfo: ComponentInfo)

  def apply(config: Config): ContainerComponent = ContainerComponent(parseConfigToContainerInfo(config))
}

/**
 * ***************************
 * Implements the container actor based on the contents of the given config.
 */
final case class ContainerComponent(containerInfo: ContainerInfo) extends Container {

  implicit val ec = context.dispatcher

  import ContainerComponent._

  //registerWithLocationService()
  log.info("Container should be registering with Location Service!")

  val componentInfos = containerInfo.componentInfos

  // Send ourselves a message after initialDelay to create components
  override def preStart() {
    context.system.scheduler.scheduleOnce(containerInfo.initialDelay, self, CreateComponents(componentInfos))
  }

  // This is filled in as side effects of creating (components)
  private var supervisors = List.empty[SupervisorInfo]

  def receive = runningReceive

  // Receive messages
  private def runningReceive: Receive = {

    case LifecycleToAll(cmd: LifecycleCommand) ⇒ sendAllComponents(cmd, supervisors)

    case GetComponents                         ⇒ sender() ! getComponents
    case Stop                                  ⇒ stop()
    case Halt                                  ⇒ halt()
    case Restart                               ⇒ restart()

    case CreateComponents(infos) ⇒
      var cinfos = infos
      stagedCommand(cinfos.nonEmpty, containerInfo.creationDelay) {
        val cinfo = cinfos.head
        log.info(s"Creating component: " + cinfo.componentName)
        createComponent(cinfo)
        cinfos = cinfos.tail
      }

    case LifecycleStateChanged(state) ⇒
      log.info("Received state while running: " + state)
      val mysender = sender()
      val isUs = supervisors.find(si ⇒ si.supervisor == mysender)

    case Terminated(actorRef) ⇒ componentDied(actorRef)

    case x                    ⇒ log.error(s"Unexpected message: $x")
  }

  private def restartReceive(componentsLeft: List[SupervisorInfo]): Receive = {
    case LifecycleStateChanged(state) ⇒
      log.info(s"Received1: $state in ${containerInfo.componentName} with $componentsLeft")
      val mysender = sender()
      log.info("Received: " + state)
      if (state == Loaded) {
        if (componentsLeft.map(_.supervisor == mysender).nonEmpty) {
          mysender ! UnsubscribeLifecycleCallback(self)
          val newlist: List[SupervisorInfo] = componentsLeft.filter(_.supervisor != mysender)
          log.info("New list: " + newlist)
          checkDone(newlist)
        }
      }
    case x ⇒
      log.info(s"Unhandled command in receiveState: $x")
  }

  def checkDone(components: List[SupervisorInfo]): Unit = {
    if (components.isEmpty) {
      log.info("Empty")
      context.become(runningReceive)
      self ! LifecycleToAll(Startup)
      log.info("________ FINISHED ++++++++++")
    } else {
      log.info("Not empty")
      context.become(restartReceive(components))
    }
  }

  private def getComponents = supervisors.map(_.supervisor)

  // Tell all components to uninitialize and start an actor to wait until they do before restarting them.
  private def restart(): Unit = {
    log.info("RESTART RESTART")
    context.become(restartReceive(supervisors))
    supervisors.foreach(si ⇒ si.supervisor ! SubscribeLifecycleCallback(self))
    sendAllComponents(Uninitialize, supervisors)
  }

  // Starts an actor to manage registering this actor with the location service
  // (as a proxy for the component)
  private def registerWithLocationService(): Unit = {
    val name = containerInfo.componentName
    val componentId = ComponentId(name, Container)
    LocationService.registerAkkaConnection(componentId, self)(context.system)
  }

  private def createComponent(componentInfo: ComponentInfo): Option[ActorRef] = {
    supervisors.find(_.componentInfo == componentInfo) match {
      case Some(existingComponentInfo) ⇒
        log.error(s"In supervisor ${containerInfo.componentName}, component ${componentInfo.componentName} already exists")
        None
      case None ⇒
        val supervisor = Supervisor(componentInfo)
        supervisors = SupervisorInfo(supervisor, componentInfo) :: supervisors
        Some(supervisor)
    }
  }

  private def sendAllComponents(cmd: Any, infos: List[SupervisorInfo]) = {
    var sinfos = infos
    stagedCommand(sinfos.nonEmpty, containerInfo.creationDelay) {
      val sinfo: SupervisorInfo = sinfos.head
      log.info(s"Sending $cmd to: ${sinfo.componentInfo.componentName}")
      sinfo.supervisor ! cmd
      sinfos = sinfos.tail
    }
  }

  private def stagedCommand(conditional: ⇒ Boolean, duration: FiniteDuration = 1.seconds)(body: ⇒ Unit) {
    if (conditional) {
      context.system.scheduler.scheduleOnce(duration) {
        body
        stagedCommand(conditional, duration)(body)
      }
    }
  }

  /*
  private def createComponents(infos: List[ComponentInfo]): Map[String, SupervisorInfo] = {
    val supervisors = infos.map(cinfo ⇒ cinfo.componentName -> SupervisorInfo(Supervisor(cinfo), cinfo))
    supervisors.toMap
  }
  */

  // Called when a component (lifecycle manager) terminates
  private def componentDied(actorRef: ActorRef): Unit = {
    log.info(s"Actor $actorRef terminated")
  }

  //private def getComponents: Components = Components(supervisors.map(si => (si.componentInfo.componentName -> si.supervisor)).toMap)

  // Tell all components to uninitialize
  private def stop(): Unit = {
    sendAllComponents(Uninitialize, supervisors)
  }

  def staged[A, B, C](in: List[A], f: A ⇒ Option[B], f2: (List[A]) ⇒ C)(delay: FiniteDuration = 1.second) = {
    log.info("Staged!!! " + in)
    in match {
      case Nil ⇒ log.info("Staged Done") // Done
      case cinfo :: tail ⇒
        f(cinfo)
        val message = f2(tail)
        context.system.scheduler.scheduleOnce(delay, self, message)
    }
  }

  private def halt(): Unit = {
    log.info("Halting")
    //supervisors.foreach(si => si.supervisor ! SubscribeLifecycleCallback(self))
    sendAllComponents(HaltComponent, supervisors)
  }
}
