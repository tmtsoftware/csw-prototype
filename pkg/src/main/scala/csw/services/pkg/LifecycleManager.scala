package csw.services.pkg

import akka.actor._



import csw.services.loc. ServiceId
//import csw.util.cfg.Configurations.ControlConfigArg
//import csw.util.cfg.RunId

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
    * @param onlyRunning true if only interested in receiving a message when the component is in the Running state
    */
  case class SubscribeToLifecycleStates(onlyRunning: Boolean = false)

  /**
    * Message sent to subscribers of lifecycle states
    * @param state the current state
    * @param error set if there was an error preventing the lifecycle state change
    */
  case class LifecycleStateChanged(state: LifecycleState, error: Option[LifecycleError])

  /**
    * When this message is received, the component is unregistered from the location service
    */
  //case object UnregisterWithLocationService

  /**
    * Used to create the Lifecycle actor
*/
  def props(supervisor: ActorRef, serviceId: ServiceId, component: ActorRef): Props =
    Props(classOf[LifecycleManager], supervisor, serviceId, component)
}

/**
  * A supervisor actor that manages the component actor given by the arguments
  * (see props() for argument descriptions).
  */
case class LifecycleManager(supervisor: ActorRef, serviceId: ServiceId, component: ActorRef)
  extends Actor with ActorLogging {
  import LifecycleManager._

  // Used to notify subscribers of a change in the lifecycle
  var lifecycleStateListeners = Map[ActorRef, Boolean]()


  // Result of last location service registration, can be used to unregister (by calling close())
//  var registration: Option[LocationService.Registration] = None

  val name = serviceId.name
  //val serviceRefs = services.map(ServiceRef(_, AkkaType)).toSet


  context.become(loaded(Loaded(name)))

  override def receive: Receive = {
    case Terminated(actorRef) ⇒
      terminated(actorRef)

  }

  // --- Receive states (See OSW TN012 - COMPONENT LIFECYCLE DESIGN) ---
  // XXX TODO: Maybe combine to one receive methods with a state parameter?

  // Behavior in the Loaded state
  def loaded(targetState: LifecycleState): Receive = receive orElse {
    case Initialize ⇒
      log.debug(s"$name received Initialize")
      component ! Initialize
      //registerWithLocationService()
      context.become(loaded(Initialized(name)))

    case Startup ⇒
      log.debug(s" $name received Startup")
      component ! Initialize
      //registerWithLocationService()
      context.become(loaded(Running(name)))

    case Shutdown ⇒
      log.debug(s" $name received Shutdown")
      component ! Initialize
      context.become(loaded(Initialized(name)))

    case Uninitialize ⇒
      log.debug(s" $name received Uninitialize")
      context.become(loaded(Loaded(name)))

    // Message from component indicating current state
    case s@Loaded(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, targetState, loaded(targetState))

    case s@Initialized(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, targetState, initialized(targetState))

    case s@Running(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, targetState, running(targetState))

    case e: LifecycleError ⇒
      log.error(e.reason, s"${e.name}: lifecycle error: ${e.getClass.getSimpleName}")
      notifyLifecycleListeners(LifecycleStateChanged(Loaded(name), Some(e)))

    case SubscribeToLifecycleStates(onlyRunning) ⇒
      subscribeToLifecycleStates(Loaded(name), onlyRunning)
/*
    case configArg: ControlConfigArg ⇒
      cmdStatusError(configArg.info.runId, "loaded", "running")
*/
    case msg ⇒
      unexpectedMessage(msg, "loaded")
  }

  // Behavior in the Initialized state
  def initialized(targetState: LifecycleState): Receive = receive orElse {
    case Initialize ⇒
      context.become(initialized(Initialized(name)))

    case Startup ⇒
      log.debug(s" $name received Startup")
      component ! _
      //requestServices()
      context.become(initialized(Running(name)))

    case Shutdown ⇒
      log.debug(s" $name received Shutdown")
      context.become(initialized(Initialized(name)))

    case Uninitialize ⇒
      log.debug(s" $name received Uninitialize")
      component ! _
      context.become(initialized(Loaded(name)))

    // Message from component indicating current state
    case s@Loaded(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, targetState, loaded(targetState))

    case s@Initialized(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, targetState, initialized(targetState))

    case s@Running(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, targetState, running(targetState))

    case e: LifecycleError ⇒
      log.error(e.reason, s"${e.name}: lifecycle error: ${e.getClass.getSimpleName}")
      notifyLifecycleListeners(LifecycleStateChanged(Initialized(name), Some(e)))

    case SubscribeToLifecycleStates(onlyRunning) ⇒
      subscribeToLifecycleStates(Initialized(name), onlyRunning)

      /*
    case configArg: ControlConfigArg ⇒
      cmdStatusError(configArg.info.runId, "initialized", "running")
*/
    case msg ⇒
      unexpectedMessage(msg, "initialized")
  }

  // Behavior in the Running state
  def running(targetState: LifecycleState): Receive = receive orElse {
    case Initialize ⇒
      log.debug(s" $name received Initialize")
      component ! Shutdown
      context.become(running(Initialized(name)))

    case Startup ⇒
      log.debug(s" $name received Startup")
      context.become(running(Running(name)))

    case Shutdown ⇒
      log.debug(s" $name received Shutdown")
      component ! _
      context.become(running(Initialized(name)))

    case Uninitialize ⇒
      log.debug(s" $name received Uninitialize")
      component ! Shutdown
      context.become(running(Loaded(name)))

    // Message from component indicating current state
    case s@Loaded(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, targetState, loaded(targetState))

    case s@Initialized(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, targetState, initialized(targetState))

    case s@Running(_) ⇒
      log.debug(s" $name received $s")
      updateState(s, targetState, running(targetState))

    case e: LifecycleError ⇒
      log.error(e.reason, s"${e.name}: lifecycle error: ${e.getClass.getSimpleName}")
      notifyLifecycleListeners(LifecycleStateChanged(Running(name), Some(e)))

    case SubscribeToLifecycleStates(onlyRunning) ⇒
      subscribeToLifecycleStates(Running(name), onlyRunning)

    case msg ⇒
      log.debug(s" forwarding $msg from ${sender()} to $component")
      component.tell(msg, sender())
  }

  // ---

  private def unexpectedMessage(msg: Any, state: String): Unit = {
    log.error(s"$name: Unexpected message: $msg in $state state")
  }

  // The default supervision behavior will normally restart the component automatically.
  // The Terminated message should only be received if we manually stop the component, or a
  // system error occurs (Exceptions don't cause termination).
  private def terminated(actorRef: ActorRef): Unit = {
    log.info(s"$name: $actorRef has terminated")

  }





  // Called when a lifecycle state message is received from the component.
  // If not yet in the target state, sends a command to the component to go
  // there (without skipping any states).
  private def updateState(currentState: LifecycleState,
                          targetState: LifecycleState, nextState: Receive): Unit = {

    log.debug(s" $name update state: current: $currentState, target: $targetState")

    notifyLifecycleListeners(LifecycleStateChanged(currentState, None))

    targetState match {
      case `currentState` ⇒ // same state, ignore

      case Loaded(_) ⇒
        currentState match {
          case Loaded(_) ⇒
          case Initialized(_) ⇒ component ! Uninitialize
          case Running(_) ⇒ component ! Shutdown
        }

      case Initialized(_) ⇒
        currentState match {
          case Loaded(_) ⇒ component ! Initialize
          case Initialized(_) ⇒
          case Running(_) ⇒ component ! Shutdown
        }

      case Running(_) ⇒
        currentState match {
          case Loaded(_) ⇒ component ! Initialize
          case Initialized(_) ⇒
            component ! Startup;
            //requestServices()
          case Running(_) ⇒
        }
    }
    context become nextState
  }

  // Sends a command status error message indicating that the component is not in the required state or condition.
  // Note that we send it to the component, which forwards it to its commandStatusActor, so it is handled like
  // other status messages.
  /*
  private def cmdStatusError(runId: RunId, currentCond: String, requiredCond: String): Unit = {
    val msg = s"$name is $currentCond, but not $requiredCond"
    log.warning(msg)
    // XXX FIXME
    sender() ! CommandStatus.Error(runId, msg)
  }
  */

  // Subscribes the sender to lifecycle changes matching the filter and starts by sending the current state
  // XXX TODO: Cleanup old subscribers?
  private def subscribeToLifecycleStates(state: LifecycleState, onlyRunning: Boolean): Unit = {
    lifecycleStateListeners += (sender() -> onlyRunning)
    if (!onlyRunning || state.isRunning)
      sender() ! LifecycleStateChanged(state, None)
  }

  // Notifies any listeners of the new state, if the filter matches
  private def notifyLifecycleListeners(msg: LifecycleStateChanged) = {
    for ((actorRef, onlyRunning) ← lifecycleStateListeners) {
      if (!onlyRunning || msg.state.isRunning)
        actorRef ! msg
    }
  }
}
