package csw.examples.vslice.shared

import csw.services.apps.containerCmd.ContainerCmd

/**
 * Starts the HCD and/or assembly as a standalone application.
 */
object TromboneApp extends App {
  // This defines the names that can be used with the --start option and the config files used ("" is the default entry)
  val m = Map(
    "hcd" -> "tromboneHCD.conf",
    "assembly" -> "tromboneAssembly.conf",
    "both" -> "tromboneContainer.conf",
    "" -> "tromboneContainer.conf" // default value
  )

  // Parse command line args for the application (app name is vslice, like the sbt project)
  ContainerCmd("vslice", args, m)
}
