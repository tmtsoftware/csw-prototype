package csw.examples.vslice.assembly

import akka.actor.{ActorRef, ActorRefFactory, Props}
import akka.util.Timeout

import scala.language.postfixOps
import com.typesafe.config.Config
import csw.examples.vslice.assembly.AssemblyContext.{TromboneCalculationConfig, TromboneControlConfig}
import csw.examples.vslice.hcd.TromboneHCD
import csw.services.ccs.{AssemblyController2, CommandStatus2, CurrentStateReceiver, Validation}
import csw.services.ccs.SequentialExecution.SequentialExecutor
import csw.services.ccs.SequentialExecution.SequentialExecutor.{StartTheSequence, StopCurrentCommand}
import csw.services.ccs.Validation.{Validation, ValidationList}
import csw.services.events.EventServiceSettings
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.LocationService.{Location, ResolvedAkkaLocation}
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

  // Initialize HCD for testing
  def startHCD: ActorRef = {
    val testInfo = HcdInfo(
      TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      RegisterAndTrackServices, Set(AkkaType), 1.second
    )

    Supervisor3(testInfo)
  }
  val tromboneHCD = startHCD
  //val tromboneHCD = context.system.deadLetters

  def receive = initializingReceive

  // Start tracking the components we command
  log.info("Connections: " + info.connections)
  trackConnections(info.connections)

  override def allResolved(locs: Set[Location]): Unit = {
    log.info(s"RESOLVED: $locs")

  }

  val testEventServiceSettings = EventServiceSettings("localhost", 7777)

  val tracker = context.actorOf(TestLocationService.trackerProps(Some(context.self)))

  val calculationConfig = getCalculationConfig
  val controlConfig = getTromboneControlConfig

  implicit val ac = AssemblyContext(info, calculationConfig, controlConfig)

  // This actor handles all telemetry and system event publishing
  val eventPublisher = context.actorOf(TrombonePublisher.props(ac, Some(testEventServiceSettings)))
  // This actor makes a single connection to the
  val currentStateReceiver = context.actorOf(CurrentStateReceiver.props)
  log.info("CurrentStateReceiver: " + currentStateReceiver)

  // Setup command handler for assembly - note that CommandHandler connects directly to tromboneHCD here, not state receiver
  val commandHandler = context.actorOf(TromboneCommandHandler.props(ac, tromboneHCD, Some(eventPublisher)))

  // This sets up the diagnostic data publisher
  val diagPublisher = context.actorOf(DiagPublisher.props(tromboneHCD, Some(tromboneHCD), Some(eventPublisher)))

  /**
   * This contains only commands that can be received during intialization
   * @return Receive is a partial function
   */
  def initializingReceive: Receive = trackerClientReceive orElse {
    case Running =>
      // When Running is received, transition to running Receive
      log.info("becoming runningReceive")
      // Set the operational cmd state to "ready" according to spec-this is propagated to other actors
      state(cmd = cmdReady)
      context.become(runningReceive)
    case x => log.error(s"Unexpected message in TromboneAssembly:initializingReceive: $x")
  }

  supervisor ! Initialized
  supervisor ! Started

  implicit val timeout = Timeout(10.seconds)
  val xx = locateHCD()

  // Lookup the alarm service redis instance with the location service
  private def locateHCD(asName: String = "")(implicit system: ActorRefFactory, timeout: Timeout): Future[ResolvedAkkaLocation] = {
    import context.dispatcher
    val connection = AkkaConnection(ComponentId("lgsTromboneHCD", ComponentType.HCD))
    LocationService.resolve(Set(connection)).map { locationsReady =>
      val loc = locationsReady.locations.head.asInstanceOf[ResolvedAkkaLocation]
      loc
    }
  }

  log.info("Locations: " + getLocations)

  // Idea syntax checking makes orElse orElse a syntax error though it isn't, but this makes it go away
  def runningReceive: Receive = trackerClientReceive orElse stateReceive orElse controllerReceive orElse lifecycleReceivePF orElse unhandledPF

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
}

/**
 * All assembly messages are indicated here
 */
object TromboneAssembly {
  // Should get this from the config file?
  val componentPrefix = "nfiraos.ncc.trombone"

  def props(assemblyInfo: AssemblyInfo, supervisor: ActorRef) = Props(classOf[TromboneAssembly], assemblyInfo, supervisor)

  // --------- Keys/Messages used by Multiple Components
  /**
   * The message is used within the Assembly to update actors when the Trombone HCD goes up and down and up again
   * @param tromboneHCD the ActorRef of the tromboneHCD or None
   */
  case class UpdateTromboneHCD(tromboneHCD: Option[ActorRef])

}
