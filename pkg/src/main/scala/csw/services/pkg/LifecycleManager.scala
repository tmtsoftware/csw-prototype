package csw.services.pkg

import akka.actor._
import csw.services.ls.LocationService.RegInfo
import csw.services.ls.LocationServiceActor.ServiceId
import csw.services.ls.LocationServiceClientActor.{ Connected, Disconnected }
import csw.services.ls.{ LocationServiceClientActor, LocationServiceRegisterActor }

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
  sealed trait LifecycleState

  case class Loaded(name: String) extends LifecycleState

  case class Initialized(name: String) extends LifecycleState

  case class Running(name: String) extends LifecycleState

  /**
   * Reply from component for failed lifecycle changes
   */
  sealed trait LifecycleError {
    val name: String
    val reason: Throwable
  }

  case class InitializeFailed(name: String, reason: Throwable) extends LifecycleError

  case class StartupFailed(name: String, reason: Throwable) extends LifecycleError

  case class ShutdownFailed(name: String, reason: Throwable) extends LifecycleError

  case class UninitializeFailed(name: String, reason: Throwable) extends LifecycleError

  /**
   * Filter for lifecycle states (The second argument is true if connected)
   */
  type LifecycleFilter = (LifecycleState, Boolean) ⇒ Boolean

  /**
   * The sender is sent any LifecycleState messages matching the given filter
   */
  case class SubscribeToLifecycleStates(filter: LifecycleFilter)

  /**
   * Used to create the LifecycleManager actor
   * @param componentProps used to create the component
   * @param regInfo used to register the component with the location service
   * @param services a list of service ids for the services the component depends on
   * @return an object to be used to create the LifecycleManager actor
   */
  def props(componentProps: Props, regInfo: RegInfo, services: List[ServiceId]): Props =
    Props(classOf[LifecycleManager], componentProps, regInfo, services)
}

// A lifecycle manager actor that manages the component actor given by the arguments
// (see props() for argument descriptions).
case class LifecycleManager(componentProps: Props, regInfo: RegInfo, services: List[ServiceId])
    extends Actor with ActorLogging {

  import csw.services.pkg.LifecycleManager._

  // Used to notify registered listeners of a change in the lifecycle, if it matches the filter
  var lifecycleStateListeners = Map[ActorRef, LifecycleFilter]()

  val name = regInfo.serviceId.name
  val component = startComponent()

  // Not used
  override def receive: Receive = {
    case e: LifecycleError ⇒ // XXX TODO: Add listener
      log.error(e.reason, s"${e.name}: lifecycle error: ${e.getClass.getSimpleName}")

    case Terminated(actorRef) ⇒
      terminated(actorRef)
  }

  // --- Receive states (See OSW TN012 - COMPONENT LIFECYCLE DESIGN) ---

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

    case Connected(servicesReady) ⇒
      log.debug(s" $name received Connected")
      component ! servicesReady
      context.become(loaded(connected = true, targetState))

    case Disconnected ⇒
      log.debug(s" $name received Disconnected")
      context.become(loaded(connected = false, targetState))

    case SubscribeToLifecycleStates(filter) ⇒
      subscribeToLifecycleStates(Loaded(name), connected, filter)

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

    case Connected(servicesReady) ⇒
      log.debug(s" $name received Connected")
      component ! servicesReady
      context.become(initialized(connected = true, targetState))

    case Disconnected ⇒
      log.debug(s" $name received Disconnected")
      context.become(initialized(connected = false, targetState))

    case SubscribeToLifecycleStates(filter) ⇒
      subscribeToLifecycleStates(Initialized(name), connected, filter)

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

    case Connected(servicesReady) ⇒
      log.debug(s" $name received Connected")
      component ! servicesReady
      updateState(Running(name), connected = true, targetState, running(connected = true, targetState))

    case Disconnected ⇒
      log.debug(s" $name received Disconnected")
      updateState(Running(name), connected = false, targetState, running(connected = false, targetState))

    case SubscribeToLifecycleStates(filter) ⇒
      subscribeToLifecycleStates(Running(name), connected, filter)

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
    context.become(loaded(connected = services.isEmpty, Loaded(name)))
    actorRef
  }

  // Starts an actor to manage registering this actor with the location service
  // (as a proxy for the component)
  private def registerWithLocationService(): Unit = {
    context.actorOf(LocationServiceRegisterActor.props(regInfo.serviceId, Some(self),
      regInfo.configPath, regInfo.httpUri))
  }

  // Starts an actor to manage getting the services the component depends on.
  // Once all the services are available, it sends a Connected message.
  // If any service terminates, a Disconnected message is sent to this actor.
  private def requestServices(): Unit = {
    log.debug(s" requestServices $services")
    context.actorOf(LocationServiceClientActor.props(services))
  }

  // Called when a lifecycle state message is received from the component.
  // If not yet in the target state, sends a command to the component to go
  // there (without skipping any states).
  private def updateState(currentState: LifecycleState, connected: Boolean,
                          targetState: LifecycleState, nextState: Receive): Unit = {
    log.debug(s" $name update state: current: $currentState, target: $targetState, connected: $connected")

    notifyLifecycleListeners(currentState, connected)

    targetState match {
      case `currentState` ⇒
      case Loaded(_) ⇒
        currentState match {
          case Loaded(_) ⇒
          case _         ⇒ component ! Initialize
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

  // Subscribes the sender to lifecycle changes matching the filter and starts by sending the current state
  private def subscribeToLifecycleStates(state: LifecycleState, connected: Boolean,
                                         filter: LifecycleFilter): Unit = {
    lifecycleStateListeners += (sender() -> filter)
    if (filter(state, connected)) sender() ! state

  }

  // Notifies any listeners of the new state, if the filter matches
  private def notifyLifecycleListeners(state: LifecycleState, connected: Boolean) = {
    for ((actorRef, filter) ← lifecycleStateListeners) {
      if (filter(state, connected)) actorRef ! state
    }
  }
}
