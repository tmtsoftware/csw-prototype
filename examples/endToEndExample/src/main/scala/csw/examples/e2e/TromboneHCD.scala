package csw.examples.e2e

import csw.services.ccs.HcdController
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.{ComponentId, ComponentType, LocationService}
import csw.services.pkg.Component.{HcdInfo, RegisterOnly}
import csw.services.pkg.{Hcd, LifecycleHandler, Supervisor}
import csw.util.config.Configurations.SetupConfig

import scala.concurrent.duration._

/**
  * TMT Source Code: 6/20/16.
  */
class TromboneHCD(info: HcdInfo) extends Hcd with HcdController with LifecycleHandler {


  def process(sc: SetupConfig): Unit = {

  }

  // Receive actor methods
  def receive = controllerReceive orElse lifecycleHandlerReceive

}

object TromboneHCD {
  val prefix = "nfiraos.ncc"
  val hcdName = "tromboneHCD"
  val className = "csw.examples.TromboneHCD"

  LocationService.initInterface()

  import TromboneHCD._

  println("Starting TromboneHCD!")
  val componentId = ComponentId(TromboneHCD.hcdName, ComponentType.HCD)
  val hcdInfo = HcdInfo(hcdName, prefix, className, RegisterOnly, Set(AkkaType), 1.second)
  val supervisor = Supervisor(hcdInfo)
}