package csw.examples.vslice.assembly

import java.io.File

import akka.actor.{ActorRef, ActorRefFactory, Props}
import akka.util.Timeout

import scala.language.postfixOps
import com.typesafe.config.{Config, ConfigFactory}
import csw.examples.vslice.assembly.AssemblyContext.{TromboneCalculationConfig, TromboneControlConfig}
import csw.examples.vslice.hcd.TromboneHCD
import csw.services.ccs.{AssemblyController2, CommandStatus2, CurrentStateReceiver, Validation}
import csw.services.ccs.SequentialExecution.SequentialExecutor
import csw.services.ccs.SequentialExecution.SequentialExecutor.{StartTheSequence, StopCurrentCommand}
import csw.services.ccs.Validation.{Validation, ValidationList}
import csw.services.events.EventServiceSettings
import csw.services.loc.Connection.{AkkaConnection, HttpConnection}
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.LocationService._
import csw.services.loc._
import csw.services.pkg.Component.{AssemblyInfo, DoNotRegister, HcdInfo, RegisterAndTrackServices}
import csw.services.pkg.ContainerComponent._
import csw.services.pkg.{Assembly, ContainerComponent, Supervisor3}
import csw.util.config.Configurations.SetupConfigArg

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * TMT Source Code: 6/10/16.
 */
class TromboneAssembly(val info: AssemblyInfo, supervisor: ActorRef) extends Assembly with TromboneStateHandler with AssemblyController2 {

  import Supervisor3._
  import TromboneStateHandler._

  println("INFO: " + info)
  var tromboneHCD = context.system.deadLetters

  log.info("Connections: " + info.connections)

  val calculationConfig = getCalculationConfig
  log.info("Calc: " + calculationConfig)
  val controlConfig = getTromboneControlConfig
  log.info("Control: " + controlConfig)
  // Get the assembly configuration from the config service or resource file (XXX TODO: Change to be non-blocking like the HCD version)
  //val (calculationConfig, controlConfig) = getAssemblyConfigs
  implicit val ac = AssemblyContext(info, calculationConfig, controlConfig)


  def receive = initializingReceive

  // Start tracking the components we command
  log.info("Connections: " + info.connections)

  val trackerSubscriber = context.actorOf(TrackerSubscriberActor.props)
  trackerSubscriber ! TrackerSubscriberActor.Subscribe
  TrackerSubscriberActor.trackConnections(info.connections, trackerSubscriber)

  val c1 = HttpConnection(ComponentId("Alarm Service", ComponentType.Service))
  TrackerSubscriberActor.trackConnection(c1, trackerSubscriber)



  val testEventServiceSettings = EventServiceSettings("localhost", 7777)



  // This actor handles all telemetry and system event publishing
  val eventPublisher = context.actorOf(TrombonePublisher.props(ac, Some(testEventServiceSettings)))
  // This actor makes a single connection to the
  val currentStateReceiver = context.actorOf(CurrentStateReceiver.props)
  //log.info("CurrentStateReceiver: " + currentStateReceiver)

  // Setup command handler for assembly - note that CommandHandler connects directly to tromboneHCD here, not state receiver
  val commandHandler = context.actorOf(TromboneCommandHandler.props(ac, Some(tromboneHCD), Some(eventPublisher)))

  // This sets up the diagnostic data publisher
  val diagPublisher = context.actorOf(DiagPublisher.props(tromboneHCD, Some(tromboneHCD), Some(eventPublisher)))

  supervisor ! Initialized

  /**
   * This contains only commands that can be received during intialization
   * @return Receive is a partial function
   */
  def initializingReceive: Receive = {

    case location:Location => //lookatLocations(l)
      location match {
        case l: ResolvedAkkaLocation =>
          log.info(s"Got actorRef: ${l.actorRef}")
          tromboneHCD = l.actorRef.getOrElse(context.system.deadLetters)
          supervisor ! Started
        case h: ResolvedHttpLocation =>
          log.info(s"HTTP Service Damn it: ${h.connection}")
        case u: Unresolved =>
          log.info(s"Unresolved: ${u.connection}")
        case ut: UnTrackedLocation =>
          log.info(s"UnTracked: ${ut.connection}")
      }

    case Running =>
      // When Running is received, transition to running Receive
      log.info("becoming runningReceive")
      // Set the operational cmd state to "ready" according to spec-this is propagated to other actors
      state(cmd = cmdReady)
      context.become(runningReceive)

    case x => log.error(s"Unexpected message in TromboneAssembly:initializingReceive: $x")
  }

  def lookatLocations(location:Location):Unit = {
    location match {
      case l:ResolvedAkkaLocation =>
        log.info(s"Got actorRef: ${l.actorRef}")
        tromboneHCD = l.actorRef.getOrElse(context.system.deadLetters)
      case h:ResolvedHttpLocation =>
        log.info(s"HTTP: ${h.connection}")
      case u:Unresolved =>
        log.info(s"Unresolved: ${u.connection}")
      case ut:UnTrackedLocation =>
        log.info(s"UnTracked: ${ut.connection}")
    }
  }

  def locationReceive:Receive = {
    case l:Location =>
      lookatLocations(l)
  }

  // Idea syntax checking makes orElse orElse a syntax error though it isn't, but this makes it go away
  def runningReceive:Receive = locationReceive orElse stateReceive orElse controllerReceive orElse lifecycleReceivePF orElse unhandledPF

  def lifecycleReceivePF: Receive = {
    case Running =>
    // Already running so ignore
    case RunningOffline =>
      // Here we do anything that we need to do be an offline, which means running and ready but not currently in use
      log.info("Received running offline")
    case DoRestart =>
      log.info("Received dorestart")
    case DoShutdown =>
      log.info("Received doshutdown")
      // Ask our HCD to shutdown, then return complete
      tromboneHCD ! DoShutdown
      supervisor ! ShutdownComplete
    case LifecycleFailureInfo(state: LifecycleState, reason: String) =>
      // This is an error conditin so log it
      log.error(s"TromboneAssembly received failed lifecycle state: $state for reason: $reason")
  }

  def unhandledPF: Receive = {
    case x => log.error(s"Unexpected message in TromboneAssembly:unhandledPF: $x")
  }
  /**
   * Validates a received config arg and returns the first
   */
  private def validateSequenceConfigArg(sca: SetupConfigArg): ValidationList = {
    // Are all of the configs really for us and correctly formatted, etc?
    ConfigValidation.validateTromboneSetupConfigArg(sca)
  }

  override def setup(sca: SetupConfigArg, commandOriginator: Option[ActorRef]): ValidationList = {
    // Returns validations for all
    val validations: ValidationList = validateSequenceConfigArg(sca)
    if (Validation.isAllValid(validations)) {
      if (sca.configs.size == 1 && sca.configs.head.configKey == ac.stopCK) {
        // Special handling for stop which needs to interrupt the currently executing sequence
        commandHandler ! sca.configs.head
      } else {
        val executor = newExecutor(sca, commandOriginator)
        executor ! StartTheSequence(commandHandler)
      }
    }
    validations
  }


  private def newExecutor(sca: SetupConfigArg, commandOriginator: Option[ActorRef]): ActorRef =
    context.actorOf(SequentialExecutor.props(sca, commandOriginator))

  // The configuration for the calculator that provides reasonable values
  def getCalculationConfig: TromboneCalculationConfig = {
    val defaultInitialElevation = getConfigDouble("calculation-config.defaultInitialElevation")
    val focusGainError = getConfigDouble("calculation-config.focusErrorGain")
    val upperFocusLimit = getConfigDouble("calculation-config.upperFocusLimit")
    val lowerFocusLimit = getConfigDouble("calculation-config.lowerFocusLimit")
    val zenithFactor = getConfigDouble("calculation-config.zenithFactor")
    TromboneCalculationConfig(defaultInitialElevation, focusGainError, upperFocusLimit, lowerFocusLimit, zenithFactor)
  }

  // The configuration for the trombone position mm to encoder
  def getTromboneControlConfig: TromboneControlConfig = {
    val positionScale = getConfigDouble("control-config.positionScale")
    val stageZero = getConfigDouble("control-config.stageZero")
    val minStageEncoder = getConfigInt("control-config.minStageEncoder")
    val minEncoderLimit = getConfigInt("control-config.minEncoderLimit")
    val maxEncoderLimit = getConfigInt("control-config.maxEncoderLimit")
    TromboneControlConfig(positionScale, stageZero, minStageEncoder, minEncoderLimit, maxEncoderLimit)
  }

  def getConfigDouble(name: String): Double = context.system.settings.config.getDouble(s"csw.examples.Trombone.assembly.$name")
  def getConfigInt(name: String): Int = context.system.settings.config.getInt(s"csw.examples.Trombone.assembly.$name")

  // Gets the assembly configurations from the config service, or a resource file, if not found and
  // returns the two parsed objects.
  private def getAssemblyConfigs: (TromboneCalculationConfig, TromboneControlConfig) = {
    import system.dispatcher
    // This is required by the ConfigServiceClient
    implicit val system = context.system

    // Get the trombone config file from the config service, or use the given resource file if that doesn't work
    val tromboneConfigFile = new File("trombone/tromboneAssembly.conf")
    val resource = new File("tromboneAssembly.conf")

    // XXX TODO: Use config service (deal with timeout issues, if not running: Note: tests wait for 3 seconds...)
    //    implicit val timeout = Timeout(1.seconds)
    //    val f = ConfigServiceClient.getConfigFromConfigService(tromboneConfigFile, resource = Some(resource))
    //    // parse the future (optional) config (XXX waiting for the result for now, need to wait longer than the timeout: FIXME)
    //    Await.result(f.map(configOpt => (TromboneCalculationConfig(configOpt.get), TromboneControlConfig(configOpt.get))), 2.seconds)

    val config = ConfigFactory.parseResources(resource.getPath)
    println("Config: " + config)
    (TromboneCalculationConfig(config), TromboneControlConfig(config))
  }
}

/**
 * All assembly messages are indicated here
 */
object TromboneAssembly {

  def props(assemblyInfo: AssemblyInfo, supervisor: ActorRef) = Props(classOf[TromboneAssembly], assemblyInfo, supervisor)

  // --------- Keys/Messages used by Multiple Components
  /**
   * The message is used within the Assembly to update actors when the Trombone HCD goes up and down and up again
   * @param tromboneHCD the ActorRef of the tromboneHCD or None
   */
  case class UpdateTromboneHCD(tromboneHCD: Option[ActorRef])

}

/**
 * Starts Assembly as a standalone application.
 */
object TromboneAssemblyApp extends App {
  import csw.examples.vslice.shared.TromboneData._

  LocationService.initInterface()

  // Assembly Info
  // These first three are set from the config file
  var componentName: String = "lgsTrombone"
  var componentClassName: String = "csw.examples.vslice.assembly.TromboneAssembly"
  var componentPrefix: String = "nfiraos.ncc.trombone"
  val componentType = ComponentType.Assembly
  val fullName = s"$componentPrefix.$componentName"

  private def setup: Config = ContainerComponent.parseStringConfig(testConf)

  val assemblyConf = setup.getConfig(s"container.components.$componentName")
  val hcdId = ComponentId("lgsTromboneHCD", ComponentType.HCD)
  println("Hcd Component ID: " + hcdId)
  val defaultInfo = AssemblyInfo(
    componentName,
    componentPrefix,
    componentClassName,
    RegisterAndTrackServices,
    Set(AkkaType),
    Set(AkkaConnection(hcdId)))
  val assemblyInfo = parseAssembly(s"$componentName", assemblyConf).getOrElse(defaultInfo)

  println("Starting TromboneAssembly: " + assemblyInfo)

  val supervisor = Supervisor3(assemblyInfo)
}