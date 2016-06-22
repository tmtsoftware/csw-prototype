package csw.examples

import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.{ComponentId, ComponentType, Connection, LocationService}
import csw.services.pkg.{Assembly, Supervisor}
import csw.services.pkg.Component.{AssemblyInfo, RegisterOnly}
import csw.util.config.FloatKey


/**
  * TMT Source Code: 6/10/16.
  */
class TromboneAssembly(info: AssemblyInfo) extends Assembly {

  val g = context.system.settings.config.getDouble("csw.examples.TromboneAssembly.gain-default")
  log.info("G value: " + g)

  def receive: Receive = {
    case x => log.error(s"Unexpected message: $x")
  }
}

object TromboneAssembly {

}


/**
  * Starts Assembly as a standalone application.
  */
object TromboneAssemblyApp extends App {
  println("Starting TromboneAssembly")
  LocationService.initInterface()
  val assemblyName = "TromboneAssembly"
  val className = "csw.examples.TromboneAssembly"
  val componentId = ComponentId(assemblyName, ComponentType.Assembly)
  //val targetHcdConnection = AkkaConnection(ComponentId(.hcdName, ComponentType.HCD))
  //val hcdConnections: Set[Connection] = Set(targetHcdConnection)
  val assemblyInfo = AssemblyInfo(assemblyName, "", className, RegisterOnly, Set(AkkaType), Set.empty[Connection])
  val supervisor = Supervisor(assemblyInfo)
}