package csw.examples.e2e

import com.typesafe.config.Config
import csw.services.ccs.HcdController
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.{ComponentType, LocationService}
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
import csw.services.pkg.ContainerComponent._
import csw.services.pkg.LifecycleManager.Startup
import csw.services.pkg.Supervisor.{apply => _, _}
import csw.services.pkg.{ContainerComponent, Hcd, LifecycleHandler, Supervisor}
import csw.util.config.Configurations.SetupConfig

import scala.concurrent.duration._
import scala.language.implicitConversions

/**
  * TMT Source Code: 6/20/16.
  */
class TromboneHCD(info: HcdInfo) extends Hcd with HcdController with LifecycleHandler {

  import LifecycleHandler._


  lifecycle(supervisor, Startup)

  // Receive actor methods
  def receive = controllerReceive orElse lifecycleHandlerReceive

  /**
    * Components can override this method to run code when initializing.
    *
    * @return either the new lifecycle state, or the lifecycle error
    */
  override def initialize(): HandlerResponse = {
    log.info(s"initialize ${info.componentName}")
    Success
  }

  /**
    * Components can override this method to run code when starting up.
    *
    * @return either the new lifecycle state, or the lifecycle error
    */
  override def startup(): HandlerResponse = {
    log.info(s"startup ${info.componentName}")
    Success
  }


  /**
    * A derived class should process the given config and, if oneway is false, either call
    * notifySubscribers() or send a CurrentState message to itself
    * (possibly from a worker actor) to indicate changes in the current HCD state.
    *
    * @param config the config received
    */
  protected def process(config: SetupConfig): Unit = {

  }
}

object TromboneHCD {
  // HCD Info
  val componentName = "lgsTromboneHCD"
  val componentType = ComponentType.HCD
  val componentClassName = "csw.examples.e2e.TromboneHCD"
  val componentPrefix = "nfiraos.ncc.tromboneHCD"

}

/**
  * Starts Assembly as a standalone application.
  */
object TromboneHCDApp extends App {

  import TromboneData._
  import TromboneHCD._

  private def setup: Config = ContainerComponent.parseStringConfig(testConf)

  val componentConf = setup.getConfig(s"container.components.$componentName")
  val defaultInfo = HcdInfo(componentName, componentPrefix, componentClassName, DoNotRegister, Set(AkkaType), 1.second)
  val hcdInfo = parseHcd(s"$componentName", componentConf).getOrElse(defaultInfo)

  LocationService.initInterface()

  println("Starting TromboneHCD: " + hcdInfo)

  val supervisor = Supervisor(hcdInfo)
}