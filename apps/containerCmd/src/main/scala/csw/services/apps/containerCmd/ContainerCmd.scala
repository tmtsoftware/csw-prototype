package csw.services.apps.containerCmd

import java.io.File

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigResolveOptions
import csw.services.pkg.Container
import xsbti._

/**
 * A command line application for creating containers with components specified in a config file.
 */
class ContainerCmd extends AppMain with Continue {
  override def run(configuration: AppConfiguration): MainResult = {
    val args = configuration.arguments
    if (args.length != 1) error("Expected a config file argument")
    val configFile = new File(args(0))
    if (!configFile.exists()) {
      error(s"File '$configFile' does not exist")
    }
    val configResolveOptions = ConfigResolveOptions.noSystem()
    val config = ConfigFactory.parseFileAnySyntax(configFile).resolve(configResolveOptions)
    Container.create(config)
    this // keep running after main thread exits
  }

  // For startup errors
  private def error(msg: String) {
    println(msg)
    System.exit(1)
  }
}
