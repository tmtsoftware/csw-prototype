package csw.services.sysControl

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import csw.services.loc.LocationService.{Location, ResolvedAkkaLocation, ResolvedHttpLocation}
import csw.services.loc._
import csw.services.pkg.LifecycleManager
import csw.services.pkg.LifecycleManager.LifecycleCommand

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * The sysControl application allows you to send messages to CSW system actors to
 * change the log level for a given set of packages or
 * change the lifecycle state of a given component (startup, shutdown a component).
 * Other features may be added in a later version.
 */
object SysControl extends App {
  LocationService.initInterface()

  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val timeout = Timeout(10.seconds)

  private val componentTypes = Set("Container", "HCD", "Assembly", "Service")
  private val connectionTypes = Set("akka", "http")
  private val logLevels = Set("ERROR", "WARN", "INFO", "DEBUG")

  private val lifecycleCommands: Map[String, LifecycleCommand] = Map(
    "Load" -> LifecycleManager.Load,
    "Initialize" ->   LifecycleManager.Initialize,
    "Startup" ->  LifecycleManager.Startup,
    "Shutdown" ->  LifecycleManager.Shutdown,
    "Uninitialize" ->  LifecycleManager.Uninitialize,
    "Remove" ->  LifecycleManager.Uninitialize,
    "Heartbeat" ->  LifecycleManager.Heartbeat)


  /**
   * Command line options ("sysControl --help" prints a usage message with descriptions of all the options)
   * See val parser below for descriptions of the options.
   */
  private case class Options(
    name: Option[String] = None,
    componentType: Option[String] = None,
    connectionType: String = "akka",
    rootPackage: String = "csw",
    logLevel: Option[String] = None,
    lifecycleCommand: Option[String] = None,
    noExit: Boolean = false)

  // Parses the command line options
  private val parser = new scopt.OptionParser[Options]("sysControl") {
    head("sysControl", System.getProperty("CSW_VERSION"))

    opt[String]('n', "name") valueName "<name>" action { (x, c) ⇒
      c.copy(name = Some(x))
    } text "Required: The name of the target component (as registered with the location service)"

    opt[String]("component-type") valueName "<type>" action { (x, c) ⇒
      c.copy(componentType = Some(x))
    } text s"Required: component type of the target component: One of ${componentTypes.mkString(", ")}"

    opt[String]("connection-type") valueName "<type>" action { (x, c) ⇒
      c.copy(connectionType = x)
    } text s"optional connection type to access the component: One of ${connectionTypes.mkString(", ")} (default: akka)"

    opt[String]('p', "package") valueName "<name>" action { (x, c) ⇒
      c.copy(rootPackage = x)
    } text "Root package name for setting the log level (default: csw)"

    opt[String]("log-level") valueName "<level>" action { (x, c) ⇒
      c.copy(logLevel = Some(x))
    } text s"The new log level for the given package name: One of ${logLevels.mkString(", ")}"

    opt[String]("lifecycle") valueName "<command>" action { (x, c) ⇒
      c.copy(lifecycleCommand = Some(x))
    } text s"A lifecycle command to send to the target component: One of ${lifecycleCommands.mkString(", ")}"

    opt[Boolean]("no-exit") action { (x, c) ⇒
      c.copy(noExit = x)
    } text "for testing: prevents application from exiting after running command"

    help("help")
    version("version")
  }

  // Parse the command line options
  parser.parse(args, Options()) match {
    case Some(options) ⇒
      try {
        run(options)
      } catch {
        case e: Throwable ⇒
          e.printStackTrace()
          System.exit(1)
      }
    case None ⇒ System.exit(1)
  }

  // Report error and exit
  private def error(msg: String): Unit = {
    println(msg)
    System.exit(1)
  }

  // Run the application
  private def run(options: Options): Unit = {
    // Get the info required to query the location service
    if (options.name.isEmpty) error("Please specify a target component name (---name option)")
    val name = options.name.get
    if (options.componentType.isEmpty) error("Missing required --component-type value")
    val ct = options.componentType.get
    if (!componentTypes.contains(ct)) error(s"Unsupported component type: $ct")
    val componentType = ComponentType(ct).get
    val componentId = ComponentId(name, componentType)
    if (!connectionTypes.contains(options.connectionType)) error(s"Unsupported connection type: ${options.connectionType}")
    val connectionType = ConnectionType(options.connectionType).get
    val connection = Connection(componentId, connectionType)

    // Logging options
    val rootPackage = options.rootPackage

    options.logLevel.foreach {l =>
      if (!logLevels.contains(l)) error(s"Unsupported log level: $l")
    }

    // lifecycle
    options.lifecycleCommand.foreach {l =>
      if (!lifecycleCommands.contains(l)) error(s"Unsupported lifecycle command: $l")
    }

    // Lookup with location service
    val f = LocationService.resolve(Set(connection))
    f.onComplete {
      case Success(locationsReady) =>
        options.logLevel.foreach { logLevel =>
          setLogLevel(locationsReady.locations.head, logLevel, rootPackage)
        }
        options.lifecycleCommand.foreach { lifecycleCommand =>
          sendLifecycleCommand(locationsReady.locations.head, lifecycleCommands(lifecycleCommand))
        }
        case Failure(ex) => error(s"Failed to locate $name ($connection): $ex")
    }

    Await.ready(f, timeout.duration)
    if (!options.noExit) System.exit(0)
  }

  // Sets the log level on the component using the given location
  private def setLogLevel(location: Location, logLevel: String, rootPackage: String): Unit = {
    location match {
      case a: ResolvedAkkaLocation => a.actorRef.foreach(setLogLevel(_, logLevel, rootPackage))
      case h: ResolvedHttpLocation => error("HTTP target components not yet supported") // XXX TODO
      case _ => error(s"Received unexpected location info: $location")
    }
  }

  // Sets the log level on the component using the given actorRef
  private def setLogLevel(actorRef: ActorRef, logLevel: String, rootPackage: String): Unit = {
      // actorRef ! SetLogLevel(rootPackage, logLevel)
  }

  // Sends the given lifecycle command to the component
  private def sendLifecycleCommand(location: Location, lifecycleCommand: LifecycleCommand): Unit = {
    location match {
      case a: ResolvedAkkaLocation => a.actorRef.foreach(sendLifecycleCommand(_, lifecycleCommand))
      case h: ResolvedHttpLocation => error("HTTP target components not yet supported") // XXX TODO
      case _ => error(s"Received unexpected location info: $location")
    }
  }

  // Sends the given lifecycle command to the actorRef
  private def sendLifecycleCommand(actorRef: ActorRef, lifecycleCommand: LifecycleCommand): Unit = {
    actorRef ! lifecycleCommand
  }

}

