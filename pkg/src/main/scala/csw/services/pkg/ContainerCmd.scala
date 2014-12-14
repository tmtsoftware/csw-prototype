package csw.services.pkg

import java.io.File

import com.typesafe.config.{ ConfigFactory, ConfigResolveOptions }

/**
 * A command line application for creating containers with components (HCDs, assemblies)
 * specified in a config file. Note that all the dependencies for the components must
 * already be in the classpath.
 */
object ContainerCmd extends App {
  if (args.length != 1) error("Expected a config file argument")
  val configFile = new File(args(0))
  if (!configFile.exists()) {
    error(s"File '$configFile' does not exist")
  }
  val configResolveOptions = ConfigResolveOptions.noSystem()
  val config = ConfigFactory.parseFileAnySyntax(configFile).resolve(configResolveOptions)
  Container.create(config)

  // For startup errors
  private def error(msg: String) {
    println(msg)
    System.exit(1)
  }
}
