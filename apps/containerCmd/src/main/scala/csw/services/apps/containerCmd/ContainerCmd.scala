package csw.services.apps.containerCmd

import java.io.File

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.{ ConfigFactory, ConfigResolveOptions }
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.cs.akka.ConfigServiceClient
import csw.services.pkg.Container
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.{ Success, Failure }

/**
 * Can be used by a command line application to create a container with components
 * (HCDs, assemblies) specified in a config file, which can be either a resource,
 * a local file, or a file checked out from the config service.
 * Note that all the dependencies for the created components must already be in the classpath.
 *
 * @param args the command line arguments
 * @param resource optional name of default config file (under src/main/resources)
 */
case class ContainerCmd(args: Array[String], resource: Option[String] = None) {
  val logger = Logger(LoggerFactory.getLogger("ContainerCmd"))

  if (args.length == 0) {
    if (resource.isDefined) {
      logger.info(s" Using default resource: $resource")
      Container.create(ConfigFactory.load(resource.get))
    } else {
      logger.error("Error: No config file or resource was specified")
      System.exit(1)
    }
  } else if (args.length == 1) {
    val file = new File(args(0))
    if (file.exists) {
      logger.info(s" Using file: $file")
      Container.create(ConfigFactory.parseFileAnySyntax(file).resolve(ConfigResolveOptions.noSystem()))
    } else {
      logger.info(s" Attempting to get '$file' from the config service")
      initFromConfigService(file)
    }
  } else {
    logger.error("Error: Expected either a single config file argument, or no arguments for the default resource)")
    System.exit(1)
  }

  // Gets the named config file from the config service and uses it to initialize the container
  private def initFromConfigService(file: File): Unit = {
    implicit val system = ActorSystem()
    implicit val timeout: Timeout = 30.seconds
    import system.dispatcher
    val f = for {
      config ← ConfigServiceClient.getConfigFromConfigService(file)
    } yield {
      Container.create(config)
    }
    f.onComplete {
      case Success(_)  ⇒ logger.info(s"XXX Created container based on $file")
      case Failure(ex) ⇒ logger.error(s"Error getting $file from config service", ex)
    }
  }
}
