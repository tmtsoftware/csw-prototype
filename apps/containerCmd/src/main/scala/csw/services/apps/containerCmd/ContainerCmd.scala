package csw.services.apps.containerCmd

import java.io.File

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.{ConfigFactory, ConfigResolveOptions}
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.cs.akka.ConfigServiceClient
import csw.services.loc.LocationService
import csw.services.pkg.ContainerComponent
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration._

object ContainerCmd {
  LocationService.initInterface()

  /**
   * Command line options: file
   *
   * @param file optional container config file to override the default
   */
  private case class Config(file: Option[File] = None)

  private def parser(name: String): scopt.OptionParser[Config] = new scopt.OptionParser[Config](name) {
    head(name, System.getProperty("CSW_VERSION"))

    arg[File]("<file>") optional () maxOccurs 1 action { (x, c) =>
      c.copy(file = Some(x))
    } text "optional container config file to override the default"

    help("help")
    version("version")
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
  val logger = Logger(LoggerFactory.getLogger(ContainerCmd.getClass))

  ContainerCmd.parse(name, args) match {
    case Some(options) => run(options)
    case None          => System.exit(1)
  }

  private def run(options: ContainerCmd.Config): Unit = {
    options.file match {
      case Some(file) =>
        if (file.exists) {
          logger.debug(s" Using file: $file")
          ContainerComponent.create(ConfigFactory.parseFileAnySyntax(file).resolve(ConfigResolveOptions.noSystem()))
        } else {
          logger.debug(s" Attempting to get '$file' from the config service")
          initFromConfigService(file)
        }

      case None =>
        if (resource.isDefined) {
          logger.debug(s" Using default resource: $resource")
          ContainerComponent.create(ConfigFactory.load(resource.get))
        } else {
          logger.error("Error: No config file or resource was specified")
          System.exit(1)
        }
    }
  }

  // Gets the named config file from the config service and uses it to initialize the container
  private def initFromConfigService(file: File): Future[Unit] = {
    // XXX TODO: Should we try to reuse this ActorSystem instead of creating a new one for the component?
    implicit val system = ActorSystem()
    import system.dispatcher

    implicit val timeout: Timeout = 30.seconds
    val f = for {
      configOpt <- ConfigServiceClient.getConfigFromConfigService(file)
    } yield {
      if (configOpt.isEmpty) logger.error(s"$file not found in config service")
      configOpt.map(ContainerComponent.create)
    }
    f.onSuccess {
      case _ => logger.debug(s"Created container based on $file")
    }
    f.map(_ => ())
  }

}

