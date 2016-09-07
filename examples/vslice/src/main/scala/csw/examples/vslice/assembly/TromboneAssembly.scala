package csw.examples.vslice.assembly

import akka.actor.{ActorRef, Props}

import scala.language.postfixOps
import com.typesafe.config.Config
import csw.examples.vslice.assembly.FollowActor.CalculationConfig
import csw.examples.vslice.assembly.TromboneControl.TromboneControlConfig
import csw.services.ccs.Validation.{Invalid, OtherIssue, Valid, Validation}
import csw.services.ccs.{AssemblyController2, CommandStatus, CurrentStateReceiver}
import csw.services.events.EventServiceSettings
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.{ComponentType, Connection, LocationService, TestLocationService}
import csw.services.pkg.Component.{AssemblyInfo, DoNotRegister}
import csw.services.pkg.ContainerComponent._
import csw.services.pkg.{Assembly, ContainerComponent, Supervisor3}
import csw.util.config.Configurations.{ConfigKey, SetupConfig, SetupConfigArg}
import csw.util.config.Events.EventTime
import csw.util.config.UnitsOfMeasure.{degrees, kilometers, micrometers, millimeters}
import csw.util.config._

/**
  * TMT Source Code: 6/10/16.
  */
class TromboneAssembly(override val info: AssemblyInfo, supervisor: ActorRef) extends Assembly with AssemblyController2 with TromboneStateHandler {

  import TromboneAssembly._
  import TromboneStateHandler._
  import Supervisor3._

  log.info(s"Assembly: ${info.componentName}\nComponent Type:${info.componentType}\nClassName: ${info.componentClassName}\nPrefix: ${info.prefix}")

  def receive = initializingReceive

  // Start tracking the components we command
  trackConnections(info.connections)

  val testEventServiceSettings = EventServiceSettings("localhost", 7777)

  val tracker = context.actorOf(TestLocationService.trackerProps(Some(context.self)))

  val calculatorConfig = getCalculatorConfig
  println("Calc: " + calculatorConfig)
  //val g = context.system.settings.config.getDouble("csw.examples.Trombone.gain-default")
  //log.info("G value: " + g)
  val controlConfig = getTromboneControlConfig
  println("Control: " + controlConfig)

  val tromboneControl: ActorRef = context.actorOf(TromboneControl.props(controlConfig, None))
  val eventPublisher = context.actorOf(TrombonePublisher.props(Some(testEventServiceSettings)))
  val calculatorActor:ActorRef = context.actorOf(FollowActor.props(calculatorConfig, Some(tromboneControl), Some(eventPublisher)))

  val currentStateReceiver = context.actorOf(CurrentStateReceiver.props)

  println("CurrentStateReceiver: " + currentStateReceiver)

  /**
    * This contains only commands that can be received during intialization
    * @return Receive is a partial function
    */
  def initializingReceive: Receive = {
    case Running =>
      // When Running is received, transition to running Receive
      log.info("becoming runningReceive")
      context.become(runningReceive)
    case x => log.error(s"Unexpected message in TromboneAssembly:initializingReceive: $x")
  }

  supervisor ! Initialized

  supervisor ! Started

  // Set the operational cmd state to "ready" according to spec-this is propagated to other actors
  state(cmd = cmdReady)

  // Idea syntax checking makes orElse orElse a syntax error though it isn't, but this makes it go away
  def runningReceive = trackerClientReceive orElse runningReceive1

  def runningReceive1:Receive = stateReceive orElse runningReceive2

  def runningReceive2:Receive = lifecycleReceivePF orElse unhandledPF

  def lifecycleReceivePF: Receive = {
    case Running =>
      log.info("Received running")
      context.become(runningReceive)
    case RunningOffline =>
      log.info("Received running offline")
    case DoRestart =>
      log.info("Received dorestart")
    case DoShutdown =>
      log.info("Received doshutdown")
      // Just say complete for now
      supervisor ! ShutdownComplete
    case LifecycleFailureInfo(state: LifecycleState, reason: String) =>
      log.info(s"Received failed state: $state for reason: $reason")
  }

  def unhandledPF: Receive = {
    case x => log.error(s"Unexpected message in TromboneAssembly:unhandledPF: $x")
  }
  /**
    * Validates a received config arg
    */
  private def validate(sca: SetupConfigArg): Validation = {
    val issues = validateTromboneSetupConfigArg(sca)
    if (issues.nonEmpty) issues.head else Valid
  }


  override protected def setup(configArg: SetupConfigArg, replyTo: Option[ActorRef]): Validation = {
    val valid = validate(configArg)

    def doSetup(configArg: SetupConfigArg) = {
      // Call a convenience method that will forward the config to the HCD based on the prefix
      //distributeSetupConfigs(locationsResolved, configArg, None)

      // If a replyTo actor was given, reply with the command status
      if (replyTo.isDefined) {
        replyTo.get ! CommandStatus.Completed(configArg.info.runId)
      }
    }

    val validationResult:Validation = valid match {
      case Valid =>
        doSetup(configArg)
        Valid
      case iv:Invalid => iv
    }
    validationResult
  }

  // The configuration for the calculator that provides reasonable values
  def getCalculatorConfig:CalculationConfig = {
    val defaultInitialElevation = getConfigDouble("calculation-config.defaultInitialElevation")
    val focusGainError = getConfigDouble("calculation-config.focusErrorGain")
    val upperFocusLimit = getConfigDouble("calculation-config.upperFocusLimit")
    val lowerFocusLimit = getConfigDouble("calculation-config.lowerFocusLimit")
    val zenithFactor = getConfigDouble("calculation-config.zenithFactor")
    CalculationConfig(defaultInitialElevation, focusGainError, upperFocusLimit, lowerFocusLimit, zenithFactor)
  }

    // The configuration for the trombone position mm to encoder
  def getTromboneControlConfig:TromboneControlConfig = {
    val positionScale = getConfigDouble("control-config.positionScale")
    val minElevation = getConfigDouble("control-config.minElevation")
    val minElevationEncoder = getConfigInt("control-config.minElevationEncoder")
    val minEncoderLimit = getConfigInt("control-config.minEncoderLimit")
    val maxEncoderLimit = getConfigInt("control-config.maxEncoderLimit")
    TromboneControlConfig(positionScale, minElevation, minElevationEncoder, minEncoderLimit, maxEncoderLimit)
  }

  def getConfigDouble(name: String):Double = context.system.settings.config.getDouble(s"csw.examples.Trombone.assembly.$name")
  def getConfigInt(name: String):Int = context.system.settings.config.getInt(s"csw.examples.Trombone.assembly.$name")
}

/**
  * All assembly messages are indicated here
  */
object TromboneAssembly {

  def props(assemblyInfo: AssemblyInfo, supervisor: ActorRef) = Props(classOf[TromboneAssembly], assemblyInfo, supervisor)

  // Assembly Info
  val componentName = "lgsTrombone"
  val componentType = ComponentType.Assembly
  val componentClassName = "csw.examples.vslice.assembly.TromboneAssembly"
  val componentPrefix = "nfiraos.ncc.trombone"
  val fullName = s"$componentPrefix.$componentName"

  // Public command configurations
  // Init submit command
  val initPrefix = s"$componentPrefix.init"
  val initCK:ConfigKey = initPrefix

  // Dataum submit command
  val datumPrefix = s"$componentPrefix.datum"
  val datumCK:ConfigKey = datumPrefix

  // Stop submit command
  val stopPrefix = s"$componentPrefix.stop"
  val stopCK:ConfigKey = stopPrefix

  // Move submit command
  val movePrefix = s"$componentPrefix.move"
  val moveCK:ConfigKey = movePrefix
  def moveSC(position: Int):SetupConfig = SetupConfig(moveCK).add(stagePositionKey -> position withUnits stagePositionUnits)

    // Position submit command
  val positionPrefix = s"$componentPrefix.position"
  val positionCK:ConfigKey = positionPrefix

  // setElevation submit command
  val setElevationPrefix = s"$componentPrefix.setElevation"
  val setElevationCK:ConfigKey = setElevationPrefix

  // setAngle submit command
  val setAnglePrefx = s"$componentPrefix.setAngle"
  val setAngleCK:ConfigKey = setAnglePrefx

  // Follow submit command
  val followPrefix = s"$componentPrefix.follow"
  val followCK: ConfigKey = followPrefix
  val nssInUseKey = BooleanKey("nssInUse")

  // Shared key values --
  // Used by setElevation, setAngle
  val configurationNameKey = StringKey("initConfigurationName")
  val configurationVersionKey = StringKey("initConfigurationVersion")

  val focusErrorKey = DoubleKey("focus")
  val focusErrorUnits = micrometers

  val zenithAngleKey = DoubleKey("zenithAngle")
  val zenithAngleUnits = degrees

  val naLayerRangeDistanceKey = DoubleKey("rangeDistance")
  val naLayerRangeDistanceUnits = kilometers

  val naLayerElevationKey = DoubleKey("elevation")
  val naLayerElevationUnits = kilometers

  val initialElevationKey = DoubleKey("initialElevation")
  val initialElevationUnits = kilometers

  val stagePositionKey = DoubleKey("stagePosition")
  val stagePositionUnits = millimeters



  // ---------- Keys used by TromboneEventSubscriber
  // This is the zenith angle from TCS
  val zenithAnglePrefix = "TCS.tcsPk.zenithAngle"
  val zConfigKey:ConfigKey = zenithAnglePrefix
  // This is the focus error from RTC
  val focusErrorPrefix = "RTC.focusError"
  val focusConfigKey:ConfigKey = focusErrorPrefix


  // --------- Keys/Messages used by TromboneControl
  val hcdTrombonePositionKey = DoubleKey("hcdTromboneAxisPosition")

  // Used to send a position that requries transformaton from range distance
  case class RangeDistance(position: DoubleItem)

  // Used to send a raw position in mm
  case class RawPosition(position: IntItem)

  // --------- Keys/Messages used by CalculatorActor


  case class AOESWUpdate(naElevation: DoubleItem, naRange: DoubleItem)

  case class EngrUpdate(rtcFocusError: DoubleItem, stagePosition: DoubleItem, zenithAngle: DoubleItem)

  // This is used to send data for the system event for AOESW to publisher
 // case class NALayerInfo(naLayerRangeDistance: DoubleItem, naLayerElevation: DoubleItem)

  // ----------- Keys, etc. used by trombonePublisher, calculator, comamnds

  val aoSystemEventPrefix = s"$componentPrefix.sodiumLayer"
  val telStatusEventPrefix = s"$componentPrefix.engr"
  val stateStatusEventPrefix = s"$componentPrefix.state"


  // --------  Keys/Messages for CalculationActor ---------



  // Testable functions
  // Validates a SetupConfigArg for Trombone Assembly
  def validateTromboneSetupConfigArg(sca: SetupConfigArg):Seq[Invalid] = {
    import ConfigValidation._
    def validateConfig(sc: SetupConfig): Validation = {
      sc.configKey match {
        case `initCK` => initValidation(sc)
        case `datumCK` => datumValidation(sc)
        case `stopCK` => stopValidation(sc)
        case `moveCK` => moveValidation(sc)
        case `positionCK` => positionValidation(sc)
        case `setElevationCK` => setElevationValidation(sc)
        case `setAngleCK` => setAngleValidation(sc)
        case `followCK` => followValidation(sc)
        case x => Invalid(OtherIssue("SetupConfig with prefix $x is not support for $componentName"))
      }
    }
    // Returns the first failure of all in config arg
    sca.configs.map(validateConfig).collect { case a: Invalid => a }
  }

}

/**
  * Starts Assembly as a standalone application.
  */
object TromboneAssemblyApp extends App {

  import TromboneAssembly._
  import csw.examples.vslice.shared.TromboneData._

  private def setup: Config = ContainerComponent.parseStringConfig(testConf)

  val assemblyConf = setup.getConfig(s"container.components.$componentName")
  val defaultInfo = AssemblyInfo(TromboneAssembly.componentName,
    TromboneAssembly.componentPrefix, TromboneAssembly.componentClassName, DoNotRegister, Set(AkkaType), Set.empty[Connection])
  val assemblyInfo = parseAssembly(s"$componentName", assemblyConf).getOrElse(defaultInfo)

  LocationService.initInterface()

  println("Starting TromboneAssembly: " + assemblyInfo)

  val supervisor = Supervisor3(assemblyInfo)
}