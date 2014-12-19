package csw.services.apps.containerCmd

import java.io.File

import com.typesafe.config.{ ConfigResolveOptions, ConfigFactory }
import csw.services.pkg.Container

/**
 * Can be used by a command line application to create a container with components
 * (HCDs, assemblies) specified in a config file, which can be either a resource,
 * a local file, or a file checked out from the config service.
 * Note that all the dependencies for the created components must already be in the classpath.
 *
 * @param args the command line arguments
 * @param resource optional name of default config file (under src/main/resources)
 */
case class ContainerCmd(args: Array[String], resource: Option[String]) {
  if (args.length > 1) {
    println("Error: Expected a single file argument (or no args for the default)")
    System.exit(1)
  }
  if (args.length == 0) {
    if (resource.isDefined) {
      println(s" Using default resource: $resource")
      Container.create(ConfigFactory.load(resource.get))
    } else {
      println("Error: No config file was specified")
      System.exit(1)
    }
  } else if (args.length == 1) {
    val file = new File(args(0))
    if (file.exists) {
      println(s" Using file: $file")
      val configResolveOptions = ConfigResolveOptions.noSystem()
      val config = ConfigFactory.parseFileAnySyntax(file).resolve(configResolveOptions)
      Container.create(config)
    } else {
      // XXX TODO: Get from config service
    }
  }
}
