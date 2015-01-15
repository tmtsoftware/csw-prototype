package csw.services.apps.containerCmd

import java.io.File

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.{ ConfigFactory, ConfigResolveOptions }
import csw.services.cs.akka.ConfigServiceClient
import csw.services.pkg.Container

import scala.concurrent.duration._

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
  if (args.length == 0) {
    if (resource.isDefined) {
      println(s" Using default resource: $resource")
      Container.create(ConfigFactory.load(resource.get))
    } else {
      println("Error: No config file or resource was specified")
      System.exit(1)
    }
  } else if (args.length == 1) {
    val file = new File(args(0))
    if (file.exists) {
      println(s" Using file: $file")
      Container.create(ConfigFactory.parseFileAnySyntax(file).resolve(ConfigResolveOptions.noSystem()))
    } else {
      println(s" Attempting to get '$file'from the config service")
      initFromConfigService(file)
    }
  } else {
    println("Error: Expected either a single config file argument, or no arguments for the default resource)")
    System.exit(1)
  }

  // Gets the named config file from the config service and uses it to initialize the container
  private def initFromConfigService(file: File): Unit = {
    implicit val system = ActorSystem()
    implicit val timeout: Timeout = 30.seconds
    import system.dispatcher
    for {
      config ← ConfigServiceClient.getConfigFromConfigService(file)
    } {
      Container.create(config)
    }
  }
}
