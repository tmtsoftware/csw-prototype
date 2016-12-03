package csw.examples.vslice.assembly

import java.io.File

import akka.actor.{ActorRef, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.examples.vslice.assembly.AssemblyContext.{TromboneCalculationConfig, TromboneControlConfig}
import csw.services.alarms.AlarmService
import csw.services.ccs.SequentialExecutor.StartTheSequence
import csw.services.ccs.Validation.ValidationList
import csw.services.ccs.{AssemblyController, SequentialExecutor, Validation}
import csw.services.events.{EventService, TelemetryService}
import csw.services.loc.Connection.TcpConnection
import csw.services.loc.LocationService._
import csw.services.loc._
import csw.services.pkg.Component.AssemblyInfo
import csw.services.pkg.{Assembly, Supervisor}
import csw.util.config.Configurations.SetupConfigArg

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Top Level Actor for Trombone Assembly
 *
 * TromboneAssembly starts up the component doing the following:
 * creating all needed actors,
 * handling initialization,
 * participating in lifecycle with Supervisor,
 * handles locations for distribution throughout component
 * receives comamnds and forwards them to the CommandHandler by extending the AssemblyController
 */
class TromboneAssembly(val info: AssemblyInfo, supervisor: ActorRef) extends Assembly with AssemblyController {

  import Supervisor._

  // Get the assembly configuration from the config service or resource file (XXX TODO: Change to be non-blocking like the HCD version)
  val (calculationConfig, controlConfig) = getAssemblyConfigs
  implicit val ac = AssemblyContext(info, calculationConfig, controlConfig)

  private val badHCDReference = None
  private var tromboneHCD: Option[ActorRef] = badHCDReference

  //  private def isHCDAvailable: Boolean = tromboneHCD.isDefined

  private val badEventService: Option[EventService] = None
  //  private def isEventServiceAvailable: Boolean = eventService.isDefined
  private var eventService: Option[EventService] = badEventService

  private val badTelemetryService: Option[TelemetryService] = None
  //  private def isTelemetryServiceAvailable: Boolean = telemetryService.isDefined
  private var telemetryService: Option[TelemetryService] = badTelemetryService

  private val badAlarmService: Option[AlarmService] = None
  //  private def isAlarmServiceAvailable: Boolean = alarmService.isDefined
  private var alarmService: Option[AlarmService] = badAlarmService

  def receive: Receive = initializingReceive

  // Start tracking the components we command
  log.info("Connections: " + info.connections)

  private val trackerSubscriber = context.actorOf(LocationSubscriberActor.props)
  trackerSubscriber ! LocationSubscriberActor.Subscribe
  // This tracks the HCD
  LocationSubscriberActor.trackConnections(info.connections, trackerSubscriber)
  // This tracks required services
  LocationSubscriberActor.trackConnection(EventService.eventServiceConnection(), trackerSubscriber)
  LocationSubscriberActor.trackConnection(TelemetryService.telemetryServiceConnection(), trackerSubscriber)
  LocationSubscriberActor.trackConnection(AlarmService.alarmServiceConnection(), trackerSubscriber)

  // This actor handles all telemetry and system event publishing
  private val eventPublisher = context.actorOf(TrombonePublisher.props(ac, None))
  // This actor makes a single connection to the
  //val currentStateReceiver = context.actorOf(CurrentStateReceiver.props)
  //log.info("CurrentStateReceiver: " + currentStateReceiver)

  // Setup command handler for assembly - note that CommandHandler connects directly to tromboneHCD here, not state receiver
  private val commandHandler = context.actorOf(TromboneCommandHandler.props(ac, tromboneHCD, Some(eventPublisher)))

  // This sets up the diagnostic data publisher
  val diagPublisher = context.actorOf(DiagPublisher.props(ac, tromboneHCD, Some(eventPublisher)))

  supervisor ! Initialized

  /**
   * This contains only commands that can be received during intialization
   *
   * @return Receive is a partial function
   */
  def initializingReceive: Receive = locationReceive orElse {

    case Running =>
      // When Running is received, transition to running Receive
      log.info("becoming runningReceive")
      // Set the operational cmd state to "ready" according to spec-this is propagated to other actors
      //state(cmd = cmdReady)
      context.become(runningReceive)
    case x => log.error(s"Unexpected message in TromboneAssembly:initializingReceive: $x")
  }

  /**
   * This Receive partial function processes changes to the services and TromboneHCD
   * Ideally, we would wait for all services before sending Started, but it's not done yet
   */
  def locationReceive: Receive = {
    case location: Location =>
      location match {

        case l: ResolvedAkkaLocation =>
          log.info(s"Got actorRef: ${l.actorRef}")
          tromboneHCD = l.actorRef
          // When the HCD is located, Started is sent to Supervisor
          supervisor ! Started

        case h: ResolvedHttpLocation =>
          log.info(s"HTTP Service Damn it: ${h.connection}")

        case t: ResolvedTcpLocation =>
          log.info(s"Received TCP Location: ${t.connection}")
          // Verify that it is the event service
          if (t.connection == EventService.eventServiceConnection()) {
            log.info(s"Assembly received ES connection: $t")
            // Setting var here!
            eventService = Some(EventService.get(t.host, t.port))
            log.info(s"Event Service at: $eventService")
          }
          if (t.connection == TelemetryService.telemetryServiceConnection()) {
            log.info(s"Assembly received TS connection: $t")
            // Setting var here!
            telemetryService = Some(TelemetryService.get(t.host, t.port))
            log.info(s"Event Service at: $telemetryService")
          }
          if (t.connection == AlarmService.alarmServiceConnection()) {
            implicit val timeout = Timeout(10.seconds)
            log.info(s"Assembly received AS connection: $t")
            // Setting var here!
            alarmService = Some(AlarmService.get(t.host, t.port))
            log.info(s"Alarm Service at: $alarmService")
          }

        case u: Unresolved =>
          log.info(s"Unresolved: ${u.connection}")
          //if (u.connection == EventService.eventServiceConnection()) eventService = badEventService
          if (u.connection.componentId == ac.hcdComponentId) tromboneHCD = badHCDReference

        case ut: UnTrackedLocation =>
          log.info(s"UnTracked: ${ut.connection}")
      }
  }

  // Receive partial function used when in Running state
  def runningReceive: Receive = locationReceive orElse controllerReceive orElse lifecycleReceivePF orElse unhandledPF

  // Receive artial function to handle runtime lifecycle messages
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
      tromboneHCD.foreach(_ ! DoShutdown)
      supervisor ! ShutdownComplete
    case LifecycleFailureInfo(state: LifecycleState, reason: String) =>
      // This is an error conditin so log it
      log.error(s"TromboneAssembly received failed lifecycle state: $state for reason: $reason")
  }

  // Catchall unhandled message receive
  def unhandledPF: Receive = {
    case x => log.error(s"Unexpected message in TromboneAssembly:unhandledPF: $x")
  }

  /**
   * Function that overrides AssemblyController setup processes incoming SetupConfigArg messages
   * @param sca received SetupConfgiArg
   * @param commandOriginator the sender of the command
   * @return a validation object that indicates if the received config is valid
   */
  override def setup(sca: SetupConfigArg, commandOriginator: Option[ActorRef]): ValidationList = {
    // Returns validations for all
    val validations: ValidationList = validateSequenceConfigArg(sca)
    if (Validation.isAllValid(validations)) {
      if (sca.configs.size == 1 && sca.configs.head.configKey == ac.stopCK) {
        // Special handling for stop which needs to interrupt the currently executing sequence
        commandHandler ! sca.configs.head
      } else {
        // Create a SequentialExecutor to process all SetupConfigs
        val executor = newExecutor(sca, commandOriginator)
        executor ! StartTheSequence(commandHandler)
      }
    }
    validations
  }

  /**
   * Performs the initial validation of the incoming SetupConfgiArg
   */
  private def validateSequenceConfigArg(sca: SetupConfigArg): ValidationList = {
    // Are all of the configs really for us and correctly formatted, etc?
    ConfigValidation.validateTromboneSetupConfigArg(sca)
  }

  // Convenience method to create a new SequentialExecutor
  private def newExecutor(sca: SetupConfigArg, commandOriginator: Option[ActorRef]): ActorRef =
    context.actorOf(SequentialExecutor.props(sca, commandOriginator))

  // Gets the assembly configurations from the config service, or a resource file, if not found and
  // returns the two parsed objects.
  private def getAssemblyConfigs: (TromboneCalculationConfig, TromboneControlConfig) = {
    // This is required by the ConfigServiceClient
    implicit val system = context.system

    // Get the trombone config file from the config service, or use the given resource file if that doesn't work
    val tromboneConfigFile = new File("trombone/tromboneAssembly.conf")
    val resource = new File("tromboneAssembly.conf")

    //    implicit val timeout = Timeout(3.seconds)
    //    val f = ConfigServiceClient.getConfigFromConfigService(tromboneConfigFile, resource = Some(resource))
    //    // parse the future (optional) config (XXX waiting for the result for now, need to wait longer than the timeout: FIXME)
    //    Await.result(f.map(configOpt => (TromboneCalculationConfig(configOpt.get), TromboneControlConfig(configOpt.get))), 2.seconds)

    val config = ConfigFactory.parseResources(resource.getPath)
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
   *
   * @param tromboneHCD the ActorRef of the tromboneHCD or None
   */
  case class UpdateTromboneHCD(tromboneHCD: Option[ActorRef])

}
