package csw.services.pkg

import akka.actor._
import csw.services.pkg.LifecycleManager._

object LifecycleManager {

  sealed trait LifecycleState

  case object Loaded extends LifecycleState

  case object PendingInitializedFromLoaded extends LifecycleState

  case object PendingLoadedFromInitialized extends LifecycleState

  case object Initialized extends LifecycleState

  case object PendingRunningFromInitialized extends LifecycleState

  case object PendingInitializedFromRunning extends LifecycleState

  case object Running extends LifecycleState

  case object LifecycleFailure extends LifecycleState

  /**
   * Commands sent to components to change the lifecycle
   */
  sealed trait LifecycleCommand

  case object Load extends LifecycleCommand

  case object Initialize extends LifecycleCommand

  case object Startup extends LifecycleCommand

  case object Shutdown extends LifecycleCommand

  case object Uninitialize extends LifecycleCommand

  case object Remove extends LifecycleCommand

  case object Heartbeat extends LifecycleCommand

  case class LifecycleFailure(state: LifecycleState, reason: String) extends LifecycleCommand

  //
  private[pkg] sealed trait FSMData

  private[pkg] case object TargetLoaded extends FSMData

  private[pkg] case object TargetPendingInitializedFromLoaded extends FSMData

  private[pkg] case object TargetPendingLoadedFromInitialized extends FSMData

  private[pkg] case object TargetInitialized extends FSMData

  private[pkg] case object TargetPendingRunningFromInitialized extends FSMData

  private[pkg] case object TargetPendingInitializedFromRunning extends FSMData

  private[pkg] case object TargetRunning extends FSMData

  private[pkg] case class FailureInfo(state: LifecycleState, reason: String) extends FSMData

  /**
   * Reply from component for failed lifecycle changes
   */
  sealed trait LifecycleResponse

  case class InitializeFailure(reason: String) extends LifecycleResponse

  case object InitializeSuccess extends LifecycleResponse

  case class StartupFailure(reason: String) extends LifecycleResponse

  case object StartupSuccess extends LifecycleResponse

  case class ShutdownFailure(reason: String) extends LifecycleResponse

  case object ShutdownSuccess extends LifecycleResponse

  case class UninitializeFailure(reason: String) extends LifecycleResponse

  case object UninitializeSuccess extends LifecycleResponse

  /**
   * Used to create the LifecycleManager actor
   * @param component the component being managed
   * @param name the name of the managed component
   * @return Props needed to create the actor
   */
  def props(component: ActorRef, name: String): Props =
    Props(classOf[LifecycleManager], component, name)
}

// format: OFF    <-- this directive disables scalariform formatting from this point
// (due to problem where -> in Akka FSM state machines is not equivalent to =>)
class LifecycleManager(component: ActorRef, name: String) extends FSM[LifecycleState, FSMData] {

  import LifecycleManager._

  import scala.concurrent.duration._

  private val pendingTimeout = 4.seconds
  private val timeoutErrorMsg = "timeout out while waiting for component response"

  startWith(Loaded, TargetLoaded)

  when(Loaded) {
    case Event(Initialize, TargetLoaded) =>
      logState(PendingInitializedFromLoaded, Initialized)
      goto(PendingInitializedFromLoaded) using TargetInitialized
    case Event(Startup, TargetLoaded) =>
      logState(PendingInitializedFromLoaded, Running)
      goto(PendingInitializedFromLoaded) using TargetRunning
    case Event(Heartbeat, _) =>
      // This is present to notify Supervisor that component is now officially in Loaded state
      // Use of goto ensures a transition notification, stay does not make a transition
      logState(Loaded, Loaded)
      goto(Loaded) using TargetLoaded
  }

  onTransition {
    case Loaded -> PendingInitializedFromLoaded =>
      // Send initialize to component
      logTransition("sending Initialize to component")
      component ! Initialize
    // registerWithLocationService
  }

  // Only wait for pendingTimeout seconds for response
  when(PendingLoadedFromInitialized, stateTimeout = pendingTimeout) {
    case Event(UninitializeFailure(reason), _) =>
      goto(LifecycleFailure) using FailureInfo(Loaded, reason)
    case Event(UninitializeSuccess, TargetLoaded) =>
      logState(Loaded, Loaded)
      self ! Heartbeat // Not sure I like this, but it's here to tell supervisor when loaded is attained
      goto(Loaded) using TargetLoaded
    // unregisterFromLocationService
    case (Event(StateTimeout, _)) =>
      goto(LifecycleFailure) using FailureInfo(Loaded, timeoutErrorMsg)
  }

  // Only wait for pendingTimeout seconds for response
  when(PendingInitializedFromLoaded, stateTimeout = pendingTimeout) {
    case Event(InitializeFailure(reason), _) =>
      goto(LifecycleFailure) using FailureInfo(Initialized, reason)
    case Event(InitializeSuccess, TargetInitialized) =>
      logState(Initialized, Initialized)
      self ! Heartbeat // Not sure I like this, but it's here to tell supervisor when Initialized is attained
      goto(Initialized) using TargetInitialized
    case Event(InitializeSuccess, TargetRunning) =>
      logState(Initialized, Running)
      self ! Heartbeat // Not sure I like this, but it's here to tell supervisor when Initialized is attained
      self ! Startup
      goto(Initialized) using TargetRunning
    case (Event(StateTimeout, _)) =>
      goto(LifecycleFailure) using FailureInfo(Initialized, timeoutErrorMsg)
  }

  when(Initialized) {
    case Event(Uninitialize, _) =>
      logState(Loaded, Loaded)
      goto(PendingLoadedFromInitialized) using TargetLoaded
    case Event(Startup, _) =>
      logState(PendingRunningFromInitialized, Running)
      goto(PendingRunningFromInitialized) using TargetRunning
    case Event(Heartbeat, _) =>
      // This is present to notify Supervisor that component is now officially in Initialized state
      // Use of goto ensures a transition notification, stay does not make a transition
      logState(Initialized, Initialized)

      goto(Initialized) using TargetInitialized
  }

  onTransition {
    case Initialized -> PendingRunningFromInitialized =>
      logTransition("sending Startup to component")
      // Send startup to component
      component ! Startup
    // requestServices
    case Initialized -> PendingLoadedFromInitialized =>
      // Send uninitialize to component
      logTransition("sending Uninitialize to component")
      component ! Uninitialize
    case PendingInitializedFromLoaded -> Initialized =>
      logTransition()
  }

  // Only wait for pendingTimeout seconds for response
  when(PendingRunningFromInitialized, stateTimeout = pendingTimeout) {
    case Event(StartupFailure(reason), _) =>
      goto(LifecycleFailure) using FailureInfo(Running, reason)
    case Event(StartupSuccess, TargetRunning) =>
      logState(Running, Running)
      self ! Heartbeat // Not sure I like this, it's not needed, but it's here to tell supervisor when running is attained
      goto(Running) using TargetRunning
    case (Event(StateTimeout, _)) =>
      goto(LifecycleFailure) using FailureInfo(Running, timeoutErrorMsg)
  }

  // Only wait for pendingTimeout seconds for response
  when(PendingInitializedFromRunning, stateTimeout = pendingTimeout) {
    case Event(ShutdownFailure(reason), _) =>
      logState(LifecycleFailure, Initialized)
      goto(LifecycleFailure) using FailureInfo(Initialized, reason)
    case Event(ShutdownSuccess, TargetInitialized) =>
      logState(Initialized, Initialized)
      self ! Heartbeat // Not sure I like this, it's not needed, but it's here to tell supervisor when running is attained
      goto(Initialized) using TargetInitialized
    case Event(ShutdownSuccess, TargetLoaded) =>
      logState(Initialized, Loaded)
      self ! Heartbeat // Not sure I like this, it's not needed, but it's here to tell supervisor when running is attained
      self ! Uninitialize
      goto(Initialized) using TargetLoaded
    case (Event(StateTimeout, _)) =>
      goto(LifecycleFailure) using FailureInfo(Initialized, timeoutErrorMsg)
  }

  when(Running) {
    case Event(Shutdown, TargetRunning) =>
      logState(PendingInitializedFromRunning, Initialized)
      goto(PendingInitializedFromRunning) using TargetInitialized
    case Event(Uninitialize, TargetRunning) =>
      goto(PendingInitializedFromRunning) using TargetLoaded
    case Event(Heartbeat, _) =>
      // This is present to notify Supervisor that component is now officially in Running state
      // Use of goto ensures a transition notification, stay does not make a transition
      logState(Running, Running)
      goto(Running) using TargetRunning
  }

  onTransition {
    case Running -> PendingInitializedFromRunning =>
      logTransition("sending Shutdown to component")
      component ! Shutdown
  }

  when(LifecycleFailure) {
    case Event(state @ _, data @ _) =>
      log.debug(s"Lifecycle failed event/data: $state/$data in state: $stateName/$stateData")
      stop
  }

  onTransition {
    case _ -> LifecycleFailure =>
      log.debug(s"Sending failure to component: $nextStateData")
      nextStateData match {
        case FailureInfo(nextState, reason) =>
          component ! LifecycleFailure(nextState, reason)
        case _@ msg =>
          log.error(s"While entering LifecycleFailure state from state: $stateName, received unknown data: $msg")
      }
  }

  whenUnhandled {
    case Event(state @ _, data @ __) =>
      log.debug(s"Unhandled lifecycle event/data: $state/$data in state: $stateName/$stateData")
      stay
  }

  def logState(nextState: LifecycleState, s: LifecycleState) = log.debug(s"In $stateName/$stateData going to $nextState/$s")

  def logTransition(message: String = "") = log.debug(s"On transition going from $stateName/$stateData to $nextStateData - $message")

  def logSameStateError(stateName: LifecycleState) = log.debug(s"Received: $stateName while in $stateName")

  initialize()
}
// format: ON    <-- this directive enables scalariform formatting from this point

