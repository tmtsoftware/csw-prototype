package csw.services.trackLocation

import java.io.File
import java.net.ServerSocket

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import csw.services.cs.akka.{ConfigServiceClient, ConfigServiceSettings}
import csw.services.loc.{ComponentId, ComponentType, LocationService}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A utility application that starts a given external program, registers it with the location service and
 * unregisters it when the program exits.
 */
object TrackLocation extends App {
  LocationService.initInterface()

  // Needed for use with Futures
  implicit val system = ActorSystem("TrackLocation")

  implicit val timeout = Timeout(10.seconds)

  /**
   * Command line options ("trackLocation --help" prints a usage message with descriptions of all the options)
   * See val parser below for descriptions of the options.
   */
  private case class Options(
    name:          Option[String] = None,
    csName:        Option[String] = None,
    command:       Option[String] = None,
    port:          Option[Int]    = None,
    appConfigFile: Option[File]   = None,
    noExit:        Boolean        = false
  )

  // Parses the command line options
  private val parser = new scopt.OptionParser[Options]("trackLocation") {
    head("trackLocation", System.getProperty("CSW_VERSION"))

    opt[String]("name") valueName "<name>" action { (x, c) ⇒
      c.copy(name = Some(x))
    } text "Required: The name used to register the application (also root name in config file)"

    opt[String]("cs-name") action { (x, c) ⇒
      c.copy(csName = Some(x))
    } text "optional name of the config service to use (for fetching the application config file)"

    opt[String]('c', "command") valueName "<name>" action { (x, c) ⇒
      c.copy(command = Some(x))
    } text "The command that starts the target application: use %port to insert the port number (default: use $name.command from config file: Required)"

    opt[Int]('p', "port") valueName "<number>" action { (x, c) ⇒
      c.copy(port = Some(x))
    } text "Optional port number the application listens on (default: use value of $name.port from config file, or use a random, free port.)"

    arg[File]("<app-config>") optional () maxOccurs 1 action { (x, c) ⇒
      c.copy(appConfigFile = Some(x))
    } text "optional config file in HOCON format (Options specified as: $name.command, $name.port, etc. Fetched from config service if path does not exist)"

    opt[Unit]("no-exit") action { (x, c) ⇒
      c.copy(noExit = true)
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

  // Gets the application config from the file, if it exists, or from the config service, if it exists there.
  // If neither exist, an error is reported.
  private def getAppConfig(file: File, csName: Option[String]): Config = {
    if (file.exists())
      ConfigFactory.parseFileAnySyntax(file).resolve(ConfigResolveOptions.noSystem())
    else
      getFromConfigService(file, csName)
  }

  // Gets the named config file from the config service or reports an error if it does not exist.
  // Uses the given config service name to find the config service, if not empty.
  private def getFromConfigService(file: File, csName: Option[String]): Config = {
    val name = csName match {
      case Some(s) ⇒ s
      case None    ⇒ ConfigServiceSettings(system).name
    }

    val configOpt = Await.result(ConfigServiceClient.getConfigFromConfigService(name, file), timeout.duration)
    if (configOpt.isEmpty)
      error(s"$file not found locally or from the config service")
    configOpt.get
  }

  // Run the application
  private def run(options: Options): Unit = {
    if (options.name.isEmpty) error("Please specify an application name")
    val name = options.name.get

    //. Get the app config file, if given
    val appConfig = options.appConfigFile.map(getAppConfig(_, options.csName))

    // Gets the String value of an option from the command line or the app's config file, or None if not found
    def getStringOpt(opt: String, arg: Option[String] = None, required: Boolean = true): Option[String] = {
      val value = if (arg.isDefined) arg
      else {
        appConfig.flatMap { c ⇒
          val path = s"$name.$opt"
          if (c.hasPath(path)) Some(c.getString(path)) else None
        }
      }
      if (value.isDefined) value
      else {
        if (required) error(s"Missing required '$opt' option or setting")
        None
      }
    }

    // Gets the Int value of an option from the command line or the config file, or None if not found
    def getIntOpt(opt: String, arg: Option[Int] = None, required: Boolean = true): Option[Int] = {
      getStringOpt(opt, arg.map(_.toString), required).map(_.toInt)
    }

    // Find a random, free port to use
    def getFreePort: Int = {
      val sock = new ServerSocket(0)
      val port = sock.getLocalPort
      sock.close()
      port
    }

    // Use the value of the --port option, or use a random, free port
    val port = getIntOpt("port", options.port, required = false).getOrElse(getFreePort)

    // Replace %port in the command
    val command = getStringOpt("command", options.command).get.replace("%port", port.toString)

    startApp(name, command, port, options.noExit)
  }

  // Starts the command and registers it with the given name on the given port
  private def startApp(name: String, command: String, port: Int, noExit: Boolean): Unit = {
    import scala.sys.process._
    val componentId = ComponentId(name, ComponentType.Service)
    val f = LocationService.registerHttpConnection(componentId, port)

    // Run the command and wait for it to exit
    val exitCode = command.!

    println(s"$command exited with exit code $exitCode")

    // Unregister from the location service and exit
    val registration = Await.result(f, timeout.duration)
    registration.unregister()

    if (!noExit) System.exit(exitCode)
  }

}

