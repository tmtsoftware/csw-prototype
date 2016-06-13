package csw.services.apps.containerCmd

import java.io.File

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.{ConfigFactory, ConfigResolveOptions}
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.cs.akka.{ConfigServiceClient, ConfigServiceSettings}
import csw.services.loc.LocationService
import csw.services.pkg.ContainerComponent
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object ContainerCmd {
  LocationService.initInterface()

  /**
   * Command line options: [--config config] file
   *
   * @param csConfig optional config file to use for config service settings, if needed
   * @param file optional container config file to override the default
   */
  private case class Config(csConfig: Option[File] = None, file: Option[File] = None)

  private def parser(name: String): scopt.OptionParser[Config] = new scopt.OptionParser[Config](name) {
    head(name, System.getProperty("CSW_VERSION"))

    opt[File]('c', "config") action { (x, c) ⇒
      c.copy(csConfig = Some(x))
    } text "optional config file to use for config service settings"

    arg[File]("<file>") optional () maxOccurs 1 action { (x, c) ⇒
      c.copy(file = Some(x))
    } text "optional container config file to override the default"
  }

  private def parse(name: String, args: Seq[String]): Option[Config] = parser(name).parse(args, Config())
}

/**
 * Can be used by a command line application to create a container with components
 * (HCDs, assemblies) specified in a config file, which can be either a resource,
 * a local file, or a file checked out from the config service.
 * Note that all the dependencies for the created components must already be in the classpath.
 *
 * @param name the name of the application
 * @param args the command line arguments
 * @param resource optional name of default config file (under src/main/resources)
 */
case class ContainerCmd(name: String, args: Array[String], resource: Option[String] = None) {
  val logger = Logger(LoggerFactory.getLogger("ContainerCmd"))

  ContainerCmd.parse(name, args) match {
    case Some(options) ⇒ run(options)
    case None          ⇒ System.exit(1)
  }

  private def run(options: ContainerCmd.Config): Unit = {
    options.file match {
      case Some(file) ⇒
        if (file.exists) {
          logger.info(s" Using file: $file")
          ContainerComponent.create(ConfigFactory.parseFileAnySyntax(file).resolve(ConfigResolveOptions.noSystem()))
        } else {
          logger.info(s" Attempting to get '$file' from the config service")
          initFromConfigService(file, options.csConfig)
        }

      case None ⇒
        if (resource.isDefined) {
          logger.info(s" Using default resource: $resource")
          ContainerComponent.create(ConfigFactory.load(resource.get))
        } else {
          logger.error("Error: No config file or resource was specified")
          System.exit(1)
        }
    }
  }

  // Gets the named config file from the config service and uses it to initialize the container
  private def initFromConfigService(file: File, csConfig: Option[File]): Unit = {
    implicit val system = ActorSystem()
    import system.dispatcher

    implicit val timeout: Timeout = 30.seconds
    val settings = csConfig match {
      case Some(csConfigFile) ⇒ ConfigServiceSettings(ConfigFactory.parseFile(csConfigFile))
      case None               ⇒ ConfigServiceSettings(system)
    }

    val f = for {
      config ← ConfigServiceClient.getConfigFromConfigService(settings, file)
    } yield {
      ContainerComponent.create(config)
    }
    f.onComplete {
      case Success(_)  ⇒ logger.info(s"Created container based on $file")
      case Failure(ex) ⇒ logger.error(s"Error getting $file from config service", ex)
    }
  }
}

