package csw.examples.vslice.shared

import csw.services.apps.containerCmd.ContainerCmd

/**
 * Starts the HCD and/or assembly as a standalone application.
 * The first argument can be -s or --start with the value "hcd" or "assembly" to indicate that the HCD or
 * assembly should be started alone. The default is to run both in the same JVM.
 */
object TromboneApp extends App {
  private val a = args // Required to avoid null args below

  if (a.length >= 2) {
    val opt = a(0)
    val arg = a(1).toLowerCase
    if (opt == "-s" || opt == "--start") {
      arg match {
        case "hcd" =>
          ContainerCmd("lgsTromboneHCD", a.drop(2), Some("lgsTromboneHCD.conf"))
        case "assembly" =>
          ContainerCmd("lgsTromboneAssembly", a.drop(2), Some("lgsTromboneAssembly.conf"))
        case _ =>
          println("Error: value of -s or --start option should be 'hcd' or 'assembly'")
          System.exit(1)
      }
    } else {
      ContainerCmd("lgsTromboneContainer", a, Some("lgsTromboneContainer.conf"))
    }
  }
}
