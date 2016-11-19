package csw.services.apps.containerCmd

import java.io.File

import akka.actor.ActorRef
import com.typesafe.config.{ConfigFactory, ConfigResolveOptions}
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.loc.LocationService
import csw.services.pkg.ContainerComponent
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._

object ContainerCmd {
  LocationService.initInterface()

}

/**
 * Can be used by a command line application to create a container with components
 * (HCDs, assemblies) specified in a config file, which can be either a resource or
 * a local file.
 * Note that all the dependencies for the created components must already be in the classpath.
 *
 * The optional resources argument lets you use the --start option to pass a name (such as "hcd" or "assembly")
 * that maps to a resource file to use to start that component.
 * For example:
 * {{{
 *     val m = Map("hcd" -> "tromboneHCD.conf", "assembly" -> "tromboneAssembly.conf", "" -> "tromboneContainer.conf")
 *     ContainerCmd("vslice", args, m)
 * }}}
 *
 * If no --start option is given, the default resource at the empty key ("") is used ("tromboneContainer.conf" here).
 *
 * @param name      the name of the application
 * @param args      the command line arguments
 * @param resources optional map of name to config file (under src/main/resources, "" maps to the default resource)
 */
case class ContainerCmd(name: String, args: Array[String], resources: Map[String, String] = Map.empty) {

  /**
   * Java API:
   * Can be used by a command line application to create a container with components
   * (HCDs, assemblies) specified in a config file, which can be either a resource or
   * a local file.
   * Note that all the dependencies for the created components must already be in the classpath.
   *
   * The optional resources argument lets you use the --start option to pass a name (such as "hcd" or "assembly")
   * that maps to a resource file to use to start that component.
   * For example:
   * {{{
   *     val m = Map("hcd" -> "tromboneHCD.conf", "assembly" -> "tromboneAssembly.conf", "" -> "tromboneContainer.conf")
   *     ContainerCmd("vslice", args, m)
   * }}}
   *
   * If no --start option is given, the default resource at the empty key ("") is used ("tromboneContainer.conf" here).
   *
   * @param name      the name of the application
   * @param args      the command line arguments
   * @param resources map of name to config file (under src/main/resources, "" maps to the default resource)
   */
  def this(name: String, args: Array[String], resources: java.util.Map[String, String]) =
    this(name, args, resources.asScala.toMap)

  val logger: Logger = Logger(LoggerFactory.getLogger(ContainerCmd.getClass))
  val choices: String = resources.keys.toList.filter(_.nonEmpty).mkString(", ")

  /**
   * For use in tests that need to kill the created actors: The list of actors created.
   */
  var actors: List[ActorRef] = Nil

  /**
   * Java API: For use in tests that need to kill the created actors: The list of actors created.
   */
  def getActors: java.util.List[ActorRef] = actors.asJava

  /**
   * Command line options: file
   *
   * @param start      if defined, start using the config file corresponding to the value
   * @param standalone if true, run component(s) without a container
   * @param file       optional container config file to override the default
   */
  private case class Config(start: Option[String] = None, standalone: Boolean = false, file: Option[File] = None)

  private def parser(name: String): scopt.OptionParser[Config] = new scopt.OptionParser[Config](name) {
    val v = Option(System.getProperty("CSW_VERSION")).getOrElse("")
    head(name, v)

    opt[String]("start") valueName "<name>" action { (x, c) =>
      c.copy(start = Some(x))
    } text s"run using the named configuration (one of $choices)"

    opt[Unit]("standalone") action { (_, c) =>
      c.copy(standalone = true)
    } text "run component(s) standalone, without a container"

    arg[File]("<file>") optional () maxOccurs 1 action { (x, c) =>
      c.copy(file = Some(x))
    } text "optional container config file to override the default"

    help("help")
    version("version")
  }

  private def parse(name: String, args: Seq[String]): Option[Config] = parser(name).parse(args, Config())

  parse(name, args) match {
    case Some(options) => run(options)
    case None          => System.exit(1)
  }

  private def createComponent(options: Config, c: com.typesafe.config.Config): List[ActorRef] = {
    if (options.standalone) {
      ContainerComponent.createStandalone(c).getOrElse(Nil)
    } else {
      val t = ContainerComponent.create(c)
      if (t.isFailure) Nil else List(t.get)
    }
  }

  private def run(options: Config): Unit = {
    val mode = if (options.standalone) "standalone mode" else "container"
    options.file match {
      case Some(file) =>
        if (!file.exists) {
          logger.error(s"Error: $file does not exist")
          System.exit(1)
        }
        logger.debug(s" Using file: $file in $mode")
        val config = ConfigFactory.parseFileAnySyntax(file).resolve(ConfigResolveOptions.noSystem())
        actors = createComponent(options, config)

      case None =>
        if (resources.isEmpty) {
          logger.error("Error: No config file or resource was specified")
          System.exit(1)
        }
        resources.get(options.start.getOrElse("")) match {
          case Some(resource) =>
            logger.info(s" Using configuration: $resource in $mode")
            val config = ConfigFactory.load(resource)
            actors = createComponent(options, config)
          case None =>
            logger.error("Error: No default configuration was specified")
            System.exit(1)
        }
    }
  }
}

