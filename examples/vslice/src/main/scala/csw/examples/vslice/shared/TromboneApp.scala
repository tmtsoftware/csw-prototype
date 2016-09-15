package csw.examples.vslice.shared

import csw.services.apps.containerCmd.ContainerCmd

/**
 * Starts the HCD and/or assembly as a standalone application.
 * The first argument can be "hcd" or "assembly" to indicate that the HCD or
 * assembly should be started alone. The default is to run both in the same JVM.
 */
object TromboneApp extends App {
  private val a = args // Required to avoid null args below

  args.headOption.map(_.toLowerCase) match {
    case Some("hcd") =>
      ContainerCmd("lgsTromboneHCD", a.tail, Some("lgsTromboneHCD.conf"))
    case Some("assembly") =>
      ContainerCmd("lgsTromboneAssembly", a.tail, Some("lgsTromboneAssembly.conf"))
    case _ =>
      ContainerCmd("lgsTromboneContainer", a, Some("lgsTromboneContainer.conf"))
  }
}
