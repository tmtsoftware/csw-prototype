package csw.services.apps.containerCmd

import xsbti._

/**
 * An sbt command line launcher application for creating containers with components
 * specified in a config file.
 * See http://www.scala-sbt.org/0.13.5/docs/Launcher/GettingStarted.html.
 */
class SbtContainerLauncher extends AppMain with Continue {
  override def run(configuration: AppConfiguration): MainResult = {
    ContainerCmd(configuration.arguments, None)
    this // keep running after main thread exits (because this class extends Continue)
  }
}
