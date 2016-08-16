package csw.examples.vslice.assembly

import akka.actor.ActorRef

import scala.language.postfixOps
import scala.concurrent.duration._
import com.typesafe.config.Config
import csw.services.ccs.AssemblyController.{Valid, Validation}
import csw.services.ccs.{AssemblyController, AssemblyController2, CommandStatus}
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.{ComponentType, Connection, LocationService, TestLocationService}
import csw.services.pkg.Component.{AssemblyInfo, DoNotRegister}
import csw.services.pkg.ContainerComponent._
import csw.services.pkg.LifecycleManager.{Shutdown, Startup}
import csw.services.pkg.Supervisor.{apply => _, _}
import csw.services.pkg.{Assembly, ContainerComponent, LifecycleHandler, Supervisor}
import csw.util.config.ConfigDSL._
import csw.util.config.Configurations.{ConfigKey, SetupConfig, SetupConfigArg}
import csw.util.config.Events.EventTime
import csw.util.config.Subsystem.{RTC, TCS}
import csw.util.config.UnitsOfMeasure.millimeters
import csw.util.config.{BooleanKey, DoubleItem, DoubleKey, StringKey}

/**
  * TMT Source Code: 6/10/16.
  */
class TromboneAssembly(override val info: AssemblyInfo, supervisor: ActorRef) extends Assembly with AssemblyController2 {

  import LifecycleHandler._
  import TromboneAssembly._

  println(s"Assembly: ${info.componentName}\nComponent Type:${info.componentType}\nClassName: ${info.componentClassName}\nPrefix: ${info.prefix}")

  val tracker = context.actorOf(TestLocationService.trackerProps(Some(context.self)))

  val g = context.system.settings.config.getDouble("csw.examples.Trombone.gain-default")
  log.info("G value: " + g)

  lifecycle(supervisor, Startup)

  implicit val ec = context.dispatcher
  log.info("Starting a 10 second timer to kill process")
  context.system.scheduler.scheduleOnce(10 seconds, this.self, Quit)

  // Get the connection to the HCD this assembly uses and track it
  //trackConnections(info.connections)

  override def receive: Receive = controllerReceive orElse {
    case Quit =>
      println("Got it already")
      haltComponent(supervisor)
    case x ⇒ log.error(s"Unexpected message: $x")
  }

  /**
    * Validates a received config arg
    */
  private def validate(config: SetupConfigArg): Validation = {

    // Checks a single setup config
    def validateConfig(sc: SetupConfig): Validation = {
      /*
      if (sc.configKey.prefix != HCDExample.prefix) {
        Invalid("Wrong prefix")
      } else {
        val missing = sc.missingKeys(HCDExample.rateKey)
        if (missing.nonEmpty)
          Invalid(s"Missing keys: ${missing.mkString(", ")}")
        else Valid
      }
      */
      Valid
    }

    val list = config.configs.map(validateConfig).filter(!_.isValid)
    if (list.nonEmpty) list.head else Valid
  }

  override protected def setup(configArg: SetupConfigArg, replyTo: Option[ActorRef]): Validation = {
    val valid = validate(configArg)
    if (valid.isValid) {
      // Call a convenience method that will forward the config to the HCD based on the prefix
      //distributeSetupConfigs(locationsResolved, configArg, None)

      // If a replyTo actor was given, reply with the command status
      if (replyTo.isDefined) {
        replyTo.get ! CommandStatus.Completed(configArg.info.runId)
      }
    }
    valid
  }

}

/**
  * All assembly messages are indicated here
  */
object TromboneAssembly {
  // Assembly Info
  val componentName = "lgsTrombone"
  val componentType = ComponentType.Assembly
  val componentClassName = "csw.examples.e2e.TromboneAssembly"
  val componentPrefix = "nfiraos.ncc.trombone"

  // Temporary for testing
  case object Quit

  // Public command configurations
  // Init submit command
  val initPrefix = s"$componentPrefix.init"
  val configurationNameKey = StringKey("initConfigurationName")
  val configurationVersionKey = StringKey("initConfigurationVersion")
  val defaultInitSC = SetupConfig(initPrefix)

  // Dataum submit command
  val datumPrefix = s"$componentPrefix.datum"
  val defaultDatumSC = SetupConfig(datumPrefix)

  // Stop submit command
  val stopPrefix = s"$componentPrefix.stop"
  val defaultStopSC = SetupConfig(stopPrefix)

  // Move submit command
  val movePrefix = s"$componentPrefix.move"
  val moveCK: ConfigKey = movePrefix
  val trombonePositionKey = DoubleKey("trombonePosition")
  // Not sure why -> doesn't work ere
  val defaultMoveSC = SetupConfig(movePrefix).add(set(trombonePositionKey, 0.0).withUnits(millimeters))

  // Follow submit command
  val followPrefix = s"$componentPrefix.follow"
  val followCK: ConfigKey = followPrefix
  val nssInUseKey = BooleanKey("nssInUse")
  val defaultFollowSC = SetupConfig(followCK).add(nssInUseKey.set(false))

  // Set initial elevation
  val initialElevationPrefix = s"$componentPrefix.initialEl"
  val initialElevationCK: ConfigKey = initialElevationPrefix
  val initialElevationKey = DoubleKey("elevation1")

  // ---------- Keys used by tromboneEventSubscriber
  // This is the zenith angle from TCS
  val zConfigKey = ConfigKey(TCS, "tcsPk.zenithAngle")
  // This is the focus error from RTC
  val focusConfigKey = ConfigKey(RTC, "rtc.focusError")

  // Key values
  val focusErrorKey = DoubleKey("focus")
  val zenithAngleKey = DoubleKey("zenithAngle")

  // --------- Keys/Messages used by TromboneControl
  val hcdTrombonePositionKey = DoubleKey("hcdTrombonePosition")

  case class HCDTrombonePosition(position: DoubleItem)

  case class AOESWUpdate(naElevation: DoubleItem, naRange: DoubleItem)

  // Messages received by the TromboneAssembly and TromboneSubscriber
  case class UsingNSS(inUse: Boolean)

  // ----------- Keys, etc. used by trombonePublisher
  // Key values
  val naLayerRangeDistanceKey = DoubleKey("naLayerRangeDistance")
  val naLayerElevationKey = DoubleKey("naLayerElevation")

  val aoSystemEventPrefix = s"$componentPrefix.sodiumLayer"

  // --------  Keys/Messages for CalculationActor ---------

  // External message to set an initial elevation
  // Messages received by csw.examples.e2e.CalculationActor
  // Update from subscribers
  trait CalculatorMessages

  case class UpdatedEventData(zenithAngle: DoubleItem, focusError: DoubleItem, time: EventTime) extends CalculatorMessages

  // Messages to Calculation Actor
  case class SetElevation(elevation: DoubleItem) extends CalculatorMessages

  // This is used to send data for the system event for AOESW to publisher
  case class NALayerInfo(naLayerRangeDistance: DoubleItem, naLayerElevation: DoubleItem)

}

/**
  * Starts Assembly as a standalone application.
  */
object TromboneAssemblyApp extends App {

  import TromboneAssembly._
  import csw.examples.vslice.shared.TromboneData._

  private def setup: Config = ContainerComponent.parseStringConfig(testConf)

  val assemblyConf = setup.getConfig(s"container.components.$componentName")
  val defaultInfo = AssemblyInfo("lgsTrombone", "nfiraos.ncc.trombone", "csw.examples.e2e.TromboneAssembly", DoNotRegister, Set(AkkaType), Set.empty[Connection])
  val assemblyInfo = parseAssembly(s"$componentName", assemblyConf).getOrElse(defaultInfo)

  LocationService.initInterface()

  println("Starting TromboneAssembly: " + assemblyInfo)

  val supervisor = Supervisor(assemblyInfo)
}