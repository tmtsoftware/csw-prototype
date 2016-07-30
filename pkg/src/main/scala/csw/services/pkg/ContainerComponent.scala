package csw.services.pkg

import java.util.concurrent.TimeUnit

import akka.actor._
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions, ConfigSyntax}
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.loc.ConnectionType._
import csw.services.loc._
import csw.services.loc.ComponentType._
import csw.services.pkg.Component._
import csw.services.pkg.LifecycleManager._
import csw.services.pkg.Supervisor.{HaltComponent, LifecycleStateChanged, SubscribeLifecycleCallback, UnsubscribeLifecycleCallback}
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

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
  private val logger = Logger(LoggerFactory.getLogger(ContainerComponent.getClass))

  // for parsing of file
  private[pkg] val CONTAINER = "container"
  private[pkg] val TYPE = "type"
  private[pkg] val CLASS = "class"
  private[pkg] val PREFIX = "prefix"
  private[pkg] val CONNECTION_TYPE = "connectionType"
  private[pkg] val CONNECTIONS = "connections"
  private[pkg] val NAME = "name"
  private[pkg] val RATE = "rate"
  private[pkg] val DELAY = "delay"
  private[pkg] val INITIAL_DELAY = "initialDelay"
  private[pkg] val CREATION_DELAY = "creationDelay"
  private[pkg] val LIFECYCLE_DELAY = "lifecycleDelay"

  // XXX Should these be removed?
  private[pkg] val DEFAULT_INITIAL_DELAY = 0.seconds
  private[pkg] val DEFAULT_CREATION_DELAY = 0.seconds
  private[pkg] val DEFAULT_LIFECYCLE_DELAY = 0.seconds

  //  val DEFAULT_CONNECTION_TYPE = Set(AkkaType)

  /**
   * Used to create the component actor from a config (which may come from a config file)
   * Returns a Try[Props], since the config may or may not be valid.
   */
  def props(config: Config): Try[Props] = ContainerComponent(config).map(Props(_))

  /**
   * Used to create the component actor from the given info
   */
  def props(containerInfo: ContainerInfo): Props = Props(classOf[ContainerComponent], containerInfo)

  /**
   * Creates a container actor with a new ActorSystem based on the given config and returns the ActorRef
   */
  def create(config: Config): Try[ActorRef] = {
    parseConfigToContainerInfo(config).map(create)
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
      case Terminated(_) =>
        log.debug("{} has terminated, shutting down system", ref.path)
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

  case class CreateComponents(infos: Set[ComponentInfo]) extends ContainerMessage

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
  private[pkg] def parseConfig(config: Config): Try[Set[ComponentInfo]] = {
    Try {
      val conf = config.getConfig("container.components")
      val names = conf.root.keySet().toList
      val entries = for {
        key <- names
        value <- parseComponentConfig(key, conf.getConfig(key))
      } yield value
      Set(entries: _*)
    }
  }

  // Parse the "components" section of the config file
  private[pkg] def parseComponentConfig(name: String, conf: Config): Option[ComponentInfo] = {
    val t = conf.getString(TYPE)
    val info = ComponentType(t) match {
      case Success(HCD)      => parseHcd(name, conf)
      case Success(Assembly) => parseAssembly(name, conf)
      case Failure(ex) =>
        logger.error(s"Unknown component type: $t", ex); None
      case _ => None
    }
    info

  }

  private[pkg] def parseName(name: String, conf: Config): Try[String] = {
    if (!conf.hasPath(NAME)) Failure(ConfigurationParsingException(s"Missing configuration field: >$NAME< in connections for component: $name"))
    else Success(conf.getString(NAME))
  }

  private[pkg] def parseClassName(name: String, conf: Config): Try[String] = {
    if (!conf.hasPath(CLASS)) Failure(ConfigurationParsingException(s"Missing configuration field: >$CLASS< for component: $name"))
    else Success(conf.getString(CLASS))
  }

  private[pkg] def parsePrefix(name: String, conf: Config): Try[String] = {
    if (!conf.hasPath(PREFIX)) Failure(ConfigurationParsingException(s"Missing configuration field: >$PREFIX< for component: $name"))
    else Success(conf.getString(PREFIX))
  }

  private[pkg] def parseComponentId(name: String, conf: Config): Try[ComponentId] = {
    if (!conf.hasPath(TYPE))
      Failure(ConfigurationParsingException(s"Missing configuration field: >$TYPE< for component: $name"))
    else
      ComponentType(conf.getString(TYPE)).map(ComponentId(name, _))
  }

  // Parse the "connectionType" section of the component config
  private[pkg] def parseConnType(name: String, conf: Config): Try[Set[ConnectionType]] = {
    if (!conf.hasPath(CONNECTION_TYPE))
      Failure(ConfigurationParsingException(s"Missing configuration field: >$CONNECTION_TYPE< for component: $name"))
    else Try {
      // Note that conf.getStringList can throw an exception...
      val set = conf.getStringList(CONNECTION_TYPE).map(ctype => ConnectionType(ctype)).toSet
      if (set.exists(_.isFailure))
        throw ConfigurationParsingException(s"Unknown component type in list: >${conf.getStringList(CONNECTION_TYPE)}< for component: $name")
      set.map(_.asInstanceOf[Success[ConnectionType]].get)
    }
  }

  // Parse the "connectionType" section of the component config
  private[pkg] def parseConnTypeWithDefault(name: String, conf: Config, default: Set[ConnectionType]): Set[ConnectionType] = {
    parseConnType(name, conf).getOrElse(default)
  }

  // Parse the "services" section of the component config
  private[pkg] def parseRate(name: String, conf: Config): Try[FiniteDuration] = {
    import scala.concurrent.duration._
    if (!conf.hasPath(RATE))
      Failure(ConfigurationParsingException(s"Missing configuration field: >$RATE< for component: $name"))
    else
      Try(FiniteDuration(conf.getDuration(RATE).getSeconds, TimeUnit.SECONDS))
  }

  private[pkg] def parseDuration(name: String, configName: String, conf: Config, defaultDuration: FiniteDuration): FiniteDuration = {
    import scala.concurrent.duration._
    val t = Try(FiniteDuration(conf.getDuration(configName).getSeconds, TimeUnit.SECONDS))
    if (t.isFailure) logger.debug(s"Container $configName for $name is missing or not valid, returning: $defaultDuration.")
    t.getOrElse(defaultDuration)
  }

  private[pkg] def parseConnections(name: String, config: Config): Try[Set[Connection]] = {
    if (!config.hasPath(CONNECTIONS))
      Failure(ConfigurationParsingException(s"Missing configuration field: >$CONNECTIONS< for Assembly: $name"))
    else Try {
      // Note: config.getConfigList could throw an exception...
      val list = config.getConfigList(CONNECTIONS).toList.map { conf: Config =>
        for {
          connName <- parseName(name, conf)
          componentId <- parseComponentId(connName, conf)
          connTypes <- parseConnType(connName, conf)
        } yield connTypes.map(Connection(componentId, _))
      }
      val failed = list.find(_.isFailure).map(_.asInstanceOf[Failure[_]].exception)
      if (failed.nonEmpty)
        throw failed.get
      else
        list.flatMap(_.get).toSet
    }
  }

  /**
    * A function that can be used to parse a container config from a string.  Primarily useful for testing.
    *
    * @param s
    * @return
    */
  def parseStringConfig(s: String) = {
    val options = ConfigParseOptions.defaults().
      setOriginDescription("string container config").
      setSyntax(ConfigSyntax.CONF)
    ConfigFactory.parseString(s, options)
  }

  // Parse the "services" section of the component config
  def parseHcd(name: String, conf: Config): Option[HcdInfo] = {
    val x = for {
      componentClassName <- parseClassName(name, conf)
      prefix <- parsePrefix(name, conf)
      registerAs <- parseConnType(name, conf)
      cycle <- parseRate(name, conf)
    } yield HcdInfo(name, prefix, componentClassName, RegisterOnly, registerAs, cycle)
    if (x.isFailure) logger.error(s"An error occurred while parsing HCD info for: $name", x.asInstanceOf[Failure[_]].exception)
    x.toOption
  }

  // Parse the "services" section of the component config
  def parseAssembly(name: String, conf: Config): Option[AssemblyInfo] = {
    val x = for {
      componentClassName <- parseClassName(name, conf)
      prefix <- parsePrefix(name, conf)
      registerAs <- parseConnType(name, conf)
      connections <- parseConnections(name, conf)
    } yield AssemblyInfo(name, prefix, componentClassName, RegisterAndTrackServices, registerAs, connections)
    if (x.isFailure) logger.error(s"An error occurred while parsing Assembly info for: $name", x.asInstanceOf[Failure[_]].exception)
    x.toOption
  }

  def parseConfigToContainerInfo(config: Config): Try[ContainerInfo] = {
    for {
      componentConfigs <- parseConfig(config)
      containerConfig <- Try(config.getConfig(CONTAINER))
      name <- parseName("container", containerConfig)
    } yield {
      val initialDelay = parseDuration(name, INITIAL_DELAY, containerConfig, DEFAULT_INITIAL_DELAY)
      val creationDelay = parseDuration(name, CREATION_DELAY, containerConfig, DEFAULT_CREATION_DELAY)
      val lifecycleDelay = parseDuration(name, LIFECYCLE_DELAY, containerConfig, DEFAULT_LIFECYCLE_DELAY)
      logger.debug(s"Delays: init: $initialDelay, create: $creationDelay, lifecycle: $lifecycleDelay")
      // For container, if no connectionType, set to Akka
      val registerAs = parseConnTypeWithDefault(name, containerConfig, Set(AkkaType))
      ContainerInfo(name, RegisterOnly, registerAs, componentConfigs, initialDelay, creationDelay, lifecycleDelay)
    }
  }

  case class SupervisorInfo(componentActorSystem: ActorSystem, supervisor: ActorRef, componentInfo: ComponentInfo)

  def apply(config: Config): Try[ContainerComponent] = parseConfigToContainerInfo(config).map(ContainerComponent(_))
}

/**
 * Implements the container actor based on the contents of the given config.
 */
//noinspection ScalaUnusedSymbol
final case class ContainerComponent(override val info: ContainerInfo) extends Container {
  implicit val ec = context.dispatcher
  import ContainerComponent._

  val componentInfos = info.componentInfos
  private val name = info.componentName
  private val componentId = ComponentId(name, info.componentType)

  // This is set once the component is registered with the location service
  private var registrationOpt: Option[LocationService.RegistrationResult] = None

  registerWithLocationService()

  def receive = Actor.emptyBehavior
  context.become(runningReceive(Nil))

  createComponents(componentInfos, Nil)

  // Receive messages
  private def runningReceive(supervisors: List[SupervisorInfo]): Receive = {
    case LifecycleToAll(cmd: LifecycleCommand) => sendAllComponents(cmd, supervisors)
    case GetComponents                         => sender() ! Components(supervisors)
    case Stop                                  => stop(supervisors)
    case Halt                                  => halt(supervisors)
    case Restart                               => restart(supervisors)
    //    case CreateComponents(infos)               => createComponents(infos, supervisors)
    case LifecycleStateChanged(state)          => log.debug("Received state while running: " + state)
    case Terminated(actorRef)                  => componentDied(actorRef)
    case x                                     => log.debug(s"Unhandled command in runningReceive: $x")
  }

  private def restartReceive(supervisors: List[SupervisorInfo], restarted: List[SupervisorInfo]): Receive = {
    case LifecycleStateChanged(state) =>
      if (state == Loaded) {
        sender() ! UnsubscribeLifecycleCallback(self)
        val reloaded = (supervisors.find(_.supervisor == sender()) ++ restarted).toList
        if (reloaded.size == supervisors.size) {
          context.become(runningReceive(reloaded))
          sendAllComponents(Startup, supervisors)
        } else {
          context.become(restartReceive(supervisors, reloaded))
        }
      }
    case x =>
      log.debug(s"Unhandled command in restartReceive: $x")
  }

  // Tell all components to uninitialize and start an actor to wait until they do before restarting them.
  private def restart(supervisors: List[SupervisorInfo]): Unit = {
    context.become(restartReceive(supervisors, Nil))
    supervisors.foreach(_.supervisor ! SubscribeLifecycleCallback(self))
    sendAllComponents(Uninitialize, supervisors)
    // XXX allan: If the container is already stopped, this ensures that we get a message with the current state
    sendAllComponents(Heartbeat, supervisors)
  }

  private def createComponents(cinfos: Set[ComponentInfo], supervisors: List[SupervisorInfo]): Unit = {
    // XXX allan: Delayed start caused problems in tests - unpredictable when Loaded status is received
    //    stagedCommand(cinfos.nonEmpty, containerInfo.creationDelay) {
    //      val cinfo = cinfos.head
    //      log.debug(s"Creating component: " + cinfo.componentName)
    //      createComponent(cinfo)
    //      cinfos = cinfos.tail
    //    }

    val newSupervisors = supervisors ::: cinfos.flatMap(createComponent(_, supervisors)).toList
    context.become(runningReceive(newSupervisors))
  }

  // If the component is configured to register with the location service, do it,
  // and save the result for unregistering later.
  private def registerWithLocationService(): Unit = {
    if (info.locationServiceUsage != DoNotRegister) {
      LocationService.registerAkkaConnection(componentId, self, info.prefix)(context.system).onComplete {
        case Success(reg) =>
          registrationOpt = Some(reg)
          log.debug(s"$name: Registered $componentId with the location service")
        case Failure(ex) =>
          // XXX allan: What to do in case of error?
          log.error(s"$name: Failed to register $componentId with the location service")
      }
    }
  }

  // If the component is registered with the location service, unregister it
  private def unregisterFromLocationService(): Unit = {
    registrationOpt.foreach {
      log.debug(s"Unregistering $componentId from the location service")
      _.unregister()
    }
  }

  private def createComponent(componentInfo: ComponentInfo, supervisors: List[SupervisorInfo]): Option[SupervisorInfo] = {
    supervisors.find(_.componentInfo == componentInfo) match {
      case Some(existingComponentInfo) =>
        log.error(s"In supervisor ${info.componentName}, component ${componentInfo.componentName} already exists")
        None
      case None =>
        val (componentActorSystem, supervisor) = Supervisor(componentInfo)
        Some(SupervisorInfo(componentActorSystem, supervisor, componentInfo))
    }
  }

  private def sendAllComponents(cmd: Any, infos: List[SupervisorInfo]) = {
    var sinfos = infos
    stagedCommand(sinfos.nonEmpty, info.creationDelay) {
      val sinfo: SupervisorInfo = sinfos.head
      log.debug(s"Sending $cmd to: ${sinfo.componentInfo.componentName}")
      sinfo.supervisor ! cmd
      sinfos = sinfos.tail
    }
  }

  private def stagedCommand(conditional: => Boolean, duration: FiniteDuration = 1.seconds)(body: => Unit) {
    if (conditional) {
      context.system.scheduler.scheduleOnce(duration) {
        body
        stagedCommand(conditional, duration)(body)
      }
    }
  }

  // Called when a component (lifecycle manager) terminates
  private def componentDied(actorRef: ActorRef): Unit = {
    log.debug(s"Actor $actorRef terminated")
  }

  // Tell all components to uninitialize
  private def stop(supervisors: List[SupervisorInfo]): Unit = {
    sendAllComponents(Uninitialize, supervisors)
  }

  def staged[A, B, C](in: List[A], f: A => Option[B], f2: (List[A]) => C)(delay: FiniteDuration = 0.seconds) = {
    log.debug("Staged!!! " + in)
    in match {
      case Nil => log.debug("Staged Done") // Done
      case cinfo :: tail =>
        f(cinfo)
        val message = f2(tail)
        context.system.scheduler.scheduleOnce(delay, self, message)
    }
  }

  private def halt(supervisors: List[SupervisorInfo]): Unit = {
    log.debug("Halting")
    sendAllComponents(HaltComponent, supervisors)
  }
}
