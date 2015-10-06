package csw.services.pkg

import java.util.UUID

import akka.actor._
import csw.services.ccs.PeriodicHcdController.{ ConfigResponse, ConfigGet }
import csw.services.loc.AccessType.AkkaType
import csw.services.loc.LocationService.{ Disconnected, ServicesReady }
import csw.services.loc.{ ServiceRef, LocationService, ServiceId }
import csw.shared.cmd.CommandStatus
import csw.util.config.Configurations.ControlConfigArg

import scala.util.Failure

/**
 * The Lifecycle Manager is an actor that deals with component lifecycle messages
 * so components don't have to. There is one Lifecycle Manager per component.
 * It registers with location service and is responsible for starting and stopping the component
 * as well as managing its state.
 * All component messages go through the Lifecycle Manager, so it can reject any
 * messages that are not allowed in a given lifecycle.
 *
 * See the TMT document "OSW TN012 - COMPONENT LIFECYCLE DESIGN" for a description of CSW lifecycles.
 */
object LifecycleManager {

  /**
   * Commands sent to components to change the lifecycle
   */
  sealed trait LifecycleCommand

  case object Initialize extends LifecycleCommand

  case object Startup extends LifecycleCommand

  case object Shutdown extends LifecycleCommand

  case object Uninitialize extends LifecycleCommand

  /**
   * Values that indicate the current lifecycle state for the named component.
   */
  sealed trait LifecycleState {
    val name: String

    def isLoaded: Boolean = false

    def isInitialized: Boolean = false

    def isRunning: Boolean = false
  }

  case class Loaded(name: String) extends LifecycleState {
    override def isLoaded: Boolean = true
  }

  case class Initialized(name: String) extends LifecycleState {
    override def isInitialized: Boolean = true
  }

  case class Running(name: String) extends LifecycleState {
    override def isRunning: Boolean = true
  }

  /**
   * Reply from component for failed lifecycle changes
   */
  sealed trait LifecycleError {
    val name: String
    val reason: String
  }

  case class InitializeFailed(name: String, reason: String) extends LifecycleError

  case class StartupFailed(name: String, reason: String) extends LifecycleError

  case class ShutdownFailed(name: String, reason: String) extends LifecycleError

  case class UninitializeFailed(name: String, reason: String) extends LifecycleError

  /**
   * Message used to subscribe the sender to changes in lifecycle states
   * @param onlyRunningAndConnected true if only interested in receiving a message when
   *                                the component is in the Running state and is connected to
   *                                all required services
   */
  case class SubscribeToLifecycleStates(onlyRunningAndConnected: Boolean = false)

  /**
   * Message sent to subscribers of lifecycle states
   * @param state the current state
   * @param error set if there was an error preventing the lifecycle state change
   * @param connected true if the component is "connected" (all required services are running)
   */
  case class LifecycleStateChanged(state: LifecycleState, error: Option[LifecycleError], connected: Boolean)

  /**
   * Used to create the LifecycleManager actor
   * @param componentProps used to create the component
   * @param serviceId service used to register the component with the location service
   * @param prefix the configuration prefix (part of configs that component should receive)
   * @param services a list of service ids for the services the component depends on
   * @return an object to be used to create the LifecycleManager actor
   */
  def props(componentProps: Props, serviceId: ServiceId, prefix: String, services: List[ServiceId]): Props =
    Props(classOf[LifecycleManager], componentProps, serviceId, prefix, services)
}

/**
 * A lifecycle manager actor that manages the component actor given by the arguments
 * (see props() for argument descriptions).
 */
case class LifecycleManager(componentProps: Props, serviceId: ServiceId, prefix: String, services: List[ServiceId])
    extends Actor with ActorLogging {

  import LifecycleManager._

  // Used to notify subscribers of a change in the lifecycle
  var lifecycleStateListeners = Map[ActorRef, Boolean]()

  val name = serviceId.name
  val serviceRefs = services.map(ServiceRef(_, AkkaType)).toSet
  val component = startComponent()

  context.become(loaded(connected = services.isEmpty, Loaded(name)))

  override def receive: Receive = {
    case Terminated(actorRef) ⇒
      terminated(actorRef)
  }

  // --- Receive states (See OSW TN012 - COMPONENT LIFECYCLE DESIGN) ---
  // XXX TODO: Maybe combine to one receive methods with a state parameter?

  // Behavior in the Loaded state
  def loaded(connected: Boolean, targetState: LifecycleState): Receive = receive orElse {
    case Initialize ⇒
      log.debug(s"$name received Initialize")
      component ! Initialize
      registerWithLocationService()
      context.become(loaded(connected, Initialized(name)))

    case Startup ⇒
      log.debug(s" $name received Startup")
      component ! Initialize
      registerWithLocationService()
      context.become(loaded(connected, Running(name)))

    case Shutdown ⇒
      log.debug(s" $name received Shutdown")
      component ! Initialize
      context.become(loaded(connected, Initialized(name)))

    case Uninitialize ⇒
      log.debug(s" $name received Uninitialize")
      context.become(loaded(connected, Loaded(name)))

    // Message from component indicating current state
    case s @ Loaded(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, connected, targetState, loaded(connected, targetState))

    case s @ Initialized(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, connected, targetState, initialized(connected, targetState))

    case s @ Running(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, connected, targetState, running(connected, targetState))

    case e: LifecycleError ⇒
      log.error(e.reason, s"${e.name}: lifecycle error: ${e.getClass.getSimpleName}")
      notifyLifecycleListeners(LifecycleStateChanged(Loaded(name), Some(e), connected))

    case s @ ServicesReady(serviceMap) ⇒
      log.debug(s" $name: All services ready")
      component ! s
      context.become(loaded(connected = true, targetState))

    case s @ Disconnected(serviceRef) ⇒
      log.debug(s" $name: Received disconnect message for: $serviceRef ")
      component ! s
      context.become(loaded(connected = false, targetState))

    case SubscribeToLifecycleStates(onlyRunningAndConnected) ⇒
      subscribeToLifecycleStates(Loaded(name), connected, onlyRunningAndConnected)

    case configArg: ControlConfigArg ⇒
      cmdStatusError(configArg.info.runId, "loaded", "running")

    case ConfigGet ⇒
      configGetError("loaded", "running")

    case msg ⇒
      unexpectedMessage(msg, "loaded", connected)
  }

  // Behavior in the Initialized state
  def initialized(connected: Boolean, targetState: LifecycleState): Receive = receive orElse {
    case Initialize ⇒
      context.become(initialized(connected, Initialized(name)))

    case Startup ⇒
      log.debug(s" $name received Startup")
      component ! _
      requestServices()
      context.become(initialized(connected, Running(name)))

    case Shutdown ⇒
      log.debug(s" $name received Shutdown")
      context.become(initialized(connected, Initialized(name)))

    case Uninitialize ⇒
      log.debug(s" $name received Uninitialize")
      component ! _
      context.become(initialized(connected, Loaded(name)))

    // Message from component indicating current state
    case s @ Loaded(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, connected, targetState, loaded(connected = connected, targetState))

    case s @ Initialized(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, connected, targetState, initialized(connected, targetState))

    case s @ Running(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, connected, targetState, running(connected, targetState))

    case e: LifecycleError ⇒
      log.error(e.reason, s"${e.name}: lifecycle error: ${e.getClass.getSimpleName}")
      notifyLifecycleListeners(LifecycleStateChanged(Initialized(name), Some(e), connected))

    case s @ ServicesReady(serviceMap) ⇒
      log.debug(s" $name: All services ready")
      component ! s
      context.become(initialized(connected = true, targetState))

    case s @ Disconnected(serviceRef) ⇒
      log.debug(s" $name: Received disconnect message for: $serviceRef ")
      component ! s
      context.become(initialized(connected = false, targetState))

    case SubscribeToLifecycleStates(onlyRunningAndConnected) ⇒
      subscribeToLifecycleStates(Initialized(name), connected, onlyRunningAndConnected)

    case configArg: ControlConfigArg ⇒
      cmdStatusError(configArg.info.runId, "initialized", "running")

    case ConfigGet ⇒
      configGetError("initialized", "running")

    case msg ⇒
      unexpectedMessage(msg, "initialized", connected)
  }

  // Behavior in the Running state
  def running(connected: Boolean, targetState: LifecycleState): Receive = receive orElse {
    case Initialize ⇒
      log.debug(s" $name received Initialize")
      component ! Shutdown
      context.become(running(connected, Initialized(name)))

    case Startup ⇒
      log.debug(s" $name received Startup")
      context.become(running(connected, Running(name)))

    case Shutdown ⇒
      log.debug(s" $name received Shutdown")
      component ! _
      context.become(running(connected, Initialized(name)))

    case Uninitialize ⇒
      log.debug(s" $name received Uninitialize")
      component ! Shutdown
      context.become(running(connected, Loaded(name)))

    // Message from component indicating current state
    case s @ Loaded(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, connected, targetState, loaded(connected, targetState))

    case s @ Initialized(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, connected, targetState, initialized(connected, targetState))

    case s @ Running(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, connected, targetState, running(connected, targetState))

    case e: LifecycleError ⇒
      log.error(e.reason, s"${e.name}: lifecycle error: ${e.getClass.getSimpleName}")
      notifyLifecycleListeners(LifecycleStateChanged(Running(name), Some(e), connected))

    case s @ ServicesReady(serviceMap) ⇒
      log.debug(s" $name: All services ready")
      component ! s
      updateState(Running(name), connected = true, targetState, running(connected = true, targetState))

    case s @ Disconnected(serviceRef) ⇒
      log.debug(s" $name: Received disconnect message for: $serviceRef ")
      component ! s
      updateState(Running(name), connected = false, targetState, running(connected = false, targetState))

    case SubscribeToLifecycleStates(onlyRunningAndConnected) ⇒
      subscribeToLifecycleStates(Running(name), connected, onlyRunningAndConnected)

    case configArg: ControlConfigArg if !connected ⇒
      cmdStatusError(configArg.info.runId, "running", "connected")

    case ConfigGet if !connected ⇒
      configGetError("running", "connected")

    case msg if connected ⇒
      log.debug(s" forwarding $msg from ${sender()} to $component")
      component.tell(msg, sender())

    case msg ⇒
      log.warning(s"Ignoring message $msg since $name is not connected")
  }

  // ---

  private def unexpectedMessage(msg: Any, state: String, connected: Boolean): Unit = {
    log.error(s"$name: Unexpected message: $msg in $state state (connected: $connected)")
  }

  // The default supervision behavior will normally restart the component automatically.
  // The Terminated message should only be received if we manually stop the component, or a
  // system error occurs (Exceptions don't cause termination).
  private def terminated(actorRef: ActorRef): Unit = {
    log.info(s"$name: $actorRef has terminated")

  }

  // Starts the component actor
  private def startComponent(): ActorRef = {
    log.info(s"Starting $name")
    val actorRef = context.actorOf(componentProps, name)
    context.watch(actorRef)
    actorRef
  }

  // Starts an actor to manage registering this actor with the location service
  // (as a proxy for the component)
  private def registerWithLocationService(): Unit = {
    LocationService.registerAkkaService(serviceId, self, prefix)(context.system)
  }

  // If not already started, start an actor to manage getting the services the
  // component depends on.
  // Once all the services are available, it sends a Connected message.
  // If any service terminates, a Disconnected message is sent to this actor.
  private def requestServices(): Unit = {
    log.debug(s" requestServices $services")
    val actorName = s"$name-loc-client"
    if (context.child(actorName).isEmpty)
      context.actorOf(LocationService.props(serviceRefs), actorName)
  }

  // Called when a lifecycle state message is received from the component.
  // If not yet in the target state, sends a command to the component to go
  // there (without skipping any states).
  private def updateState(currentState: LifecycleState, connected: Boolean,
                          targetState: LifecycleState, nextState: Receive): Unit = {

    // XXX TODO: Try to simplify?

    log.debug(s" $name update state: current: $currentState, target: $targetState, connected: $connected")

    notifyLifecycleListeners(LifecycleStateChanged(currentState, None, connected))

    targetState match {
      case `currentState` ⇒ // same state, ignore

      case Loaded(_) ⇒
        currentState match {
          case Loaded(_)      ⇒
          case Initialized(_) ⇒ component ! Uninitialize
          case Running(_)     ⇒ component ! Shutdown
        }

      case Initialized(_) ⇒
        currentState match {
          case Loaded(_)      ⇒ component ! Initialize
          case Initialized(_) ⇒
          case Running(_)     ⇒ component ! Shutdown
        }

      case Running(_) ⇒
        currentState match {
          case Loaded(_) ⇒ component ! Initialize
          case Initialized(_) ⇒
            component ! Startup; requestServices()
          case Running(_) ⇒
        }
    }
    context become nextState
  }

  // Sends a command status error message indicating that the component is not in the required state or condition.
  // Note that we send it to the component, which forwards it to its commandStatusActor, so it is handled like
  // other status messages.
  private def cmdStatusError(runId: UUID, currentCond: String, requiredCond: String): Unit = {
    val msg = s"$name is $currentCond, but not $requiredCond"
    log.warning(msg)
    // XXX FIXME
    sender() ! CommandStatus.Error(runId, msg)
  }

  // Sends a config response error message to the given actor indicating that the component is not in the required state or condition
  private def configGetError(currentCond: String, requiredCond: String): Unit = {
    val msg = s"$name is $currentCond, but not $requiredCond"
    log.warning(msg)
    sender() ! ConfigResponse(Failure(new Exception(msg)))
  }

  // Subscribes the sender to lifecycle changes matching the filter and starts by sending the current state
  // XXX TODO: Cleanup old subscribers?
  private def subscribeToLifecycleStates(state: LifecycleState, connected: Boolean, onlyRunningAndConnected: Boolean): Unit = {
    lifecycleStateListeners += (sender() -> onlyRunningAndConnected)
    if (!onlyRunningAndConnected || (state.isRunning && connected))
      sender() ! LifecycleStateChanged(state, None, connected)
  }

  // Notifies any listeners of the new state, if the filter matches
  private def notifyLifecycleListeners(msg: LifecycleStateChanged) = {
    for ((actorRef, onlyRunningAndConnected) ← lifecycleStateListeners) {
      if (!onlyRunningAndConnected || (msg.state.isRunning && msg.connected))
        actorRef ! msg
    }
  }
}
