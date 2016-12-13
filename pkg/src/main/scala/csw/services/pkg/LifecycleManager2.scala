//package csw.services.pkg
//
//import akka.actor._
//import csw.services.pkg.LifecycleManager2._
//
//object LifecycleManager2 {
//
//  sealed trait LifecycleState2
//
//  case object LifecycleWaitingForInitialized extends LifecycleState2
//
//  case object LifecycleInitialized extends LifecycleState2
//
//  case object LifecycleRunning extends LifecycleState2
//
//  case object LifecycleRunningOffline extends LifecycleState2
//
//  case object LifecycleWaitingForShutdown extends LifecycleState2
//
//  case object LifecycleShutdown extends LifecycleState2
//
//  case object LifecycleFailure extends LifecycleState2
//
//  // Final state when component does not shutdown properly
//  case object LifecycleShutdownFailure extends LifecycleState2
//
//  //
//  private[pkg] sealed trait ComponentState
//
//  private[pkg] case object ComponentInitializing extends ComponentState
//
//  private[pkg] case object ComponentInitialized extends ComponentState
//
//  private[pkg] case object ComponentStartingUp extends ComponentState
//
//  private[pkg] case object ComponentStarted extends ComponentState
//
//  private[pkg] case object ComponentRunningOnline extends ComponentState
//
//  private[pkg] case object ComponentRunningOffline extends ComponentState
//
//  private[pkg] case object ComponentShuttingDown extends ComponentState
//
//  private[pkg] case object ComponentFailedToShutdown extends ComponentState
//
//  private[pkg] case object ComponentShutDown extends ComponentState
//
//  private[pkg] case class FailureInfo(state: LifecycleState2, reason: String) extends ComponentState
//
//  // Internal LifecycleManager events
//  sealed trait LifecycleInternalEvents
//
//  case object Heartbeat extends LifecycleInternalEvents
//
//  /**
//   * Messages sent to components to notify of lifecycle changes
//   */
//  sealed trait LifecycleComponentEvents
//
//  // Someone has requested that the component shutdown
//  case object DoShutdown extends LifecycleComponentEvents
//
//  // Someone has requested that the component restart by going back to uninitialized
//  case object DoRestart extends LifecycleComponentEvents
//
//  // Supervisor reports that component is in Running and Online
//  case object Running extends LifecycleComponentEvents
//
//  // Supervisor reports that compoentn is Running but is Offline
//  case object RunningOffline extends LifecycleComponentEvents
//
//  // Report to component that a lifecycle failure has occurred for logging, etc.
//  case class LifecycleFailure(state: LifecycleState2, reason: String) extends LifecycleComponentEvents
//
//  // Events from outside that influence component lifecycle
//  sealed trait ExternalLifecycleEvents
//
//  // External request to restart component initialization -- only possible in LifecycleRunning and LifecycleRunningOffline
//  case object ExComponentRestart extends ExternalLifecycleEvents
//
//  // External request to shutdown component -- only possible in in LifecycleRunning and LifecycleRunningOffline
//  case object ExComponentShutdown extends ExternalLifecycleEvents
//
//  // External request to put component onlne -- only possible in LifecycleRunningOffline
//  case object ExComponentOnline extends ExternalLifecycleEvents
//
//  // External request to put component offline -- only possible in LifecycleRunning
//  case object ExComponentOffline extends ExternalLifecycleEvents
//
//  /**
//   * Messages from component indicating events
//   */
//  sealed trait ComponentEvents
//
//  // Component indicates it has Initialized successfully
//  case object Initialized extends ComponentEvents
//
//  // Component indicates it failed to initialize
//  case class InitializeFailure(reason: String) extends ComponentEvents
//
//  // Component indicates it has Started successfully
//  case object Started extends ComponentEvents
//
//  // Component indicates it failed to startup
//  case class StartupFailure(reason: String) extends ComponentEvents
//
//  // Component indicates it has completed shutting down successfully
//  case object ShutdownComplete extends ComponentEvents
//
//  // Component indicates it has failed to shutdown properly
//  case class ShutdownFailure(reason: String) extends ComponentEvents
//
//  /**
//   * Used to create the LifecycleManager actor
//   *
//   * @param component the component being managed
//   * @param name      the name of the managed component
//   * @return Props needed to create the actor
//   */
//  def props(component: ActorRef, name: String): Props = Props(classOf[LifecycleManager2], component, name)
//}
//
//// format: OFF    <-- this directive disables scalariform formatting from this point
//// (due to problem where -> in Akka FSM state machines is not equivalent to =>)
//class LifecycleManager2(component: ActorRef, name: String) extends FSM[LifecycleState2, ComponentState] {
//
//  import LifecycleManager2._
//
//  import scala.concurrent.duration._
//
//  private val shutdownTimeout = 5.seconds
//  private val timeoutErrorMsg = "timeout out while waiting for component response"
//
//  startWith(LifecycleWaitingForInitialized, ComponentInitializing)
//
//  // No timeout waiting for Initialized
//  /**
//    * The component triggers the changes in the lifecycle.
//    * When the component starts up, nothing happens until the component sends Initialized or InitializedFailure
//    * There is no timeout waiting for Initialized
//    */
//  when(LifecycleWaitingForInitialized) {
//    case Event(Initialized, ComponentInitializing) =>
//      logState(LifecycleWaitingForInitialized, LifecycleInitialized)
//      self ! Heartbeat // Not sure I like this, but it's here to tell supervisor when Initialized is attained
//      goto(LifecycleInitialized) using ComponentInitialized
//    case Event(InitializeFailure(reason), ComponentInitializing) =>
//      goto(LifecycleFailure) using FailureInfo(LifecycleWaitingForInitialized, reason)
//    case Event(ExComponentRestart, ComponentInitializing) =>
//      // No change if component is intializing
//      stay
//    case Event(ExComponentOnline, ComponentInitializing) =>
//      // No change if component is initializing
//      stay
//    case Event(ExComponentOffline, ComponentInitializing) =>
//      // No change if component is initializing
//      stay
//    case Event(ExComponentShutdown, ComponentInitializing) =>
//      // No change if component is initializing
//      stay
//  }
//
//  when(LifecycleInitialized) {
//    case Event(Started, ComponentInitialized) =>
//      logState(LifecycleInitialized, LifecycleRunning)
//      self ! Heartbeat // Not sure I like this, but it's here to tell supervisor when Initialized is attained
//      goto(LifecycleRunning) using ComponentStarted
//    case Event(StartupFailure(reason), ComponentInitialized) =>
//      goto(LifecycleFailure) using FailureInfo(LifecycleInitialized, reason)
//    case Event(ExComponentRestart, ComponentInitialized) =>
//      // No change if component is initialized
//      stay
//    case Event(ExComponentOnline, ComponentInitialized) =>
//      // No change if component is initialized
//      stay
//    case Event(ExComponentOffline, ComponentInitialized) =>
//      // No change if component is initialized
//      stay
//    case Event(ExComponentShutdown, ComponentInitialized) =>
//      // No change if component is initialized
//      stay
//    case Event(Heartbeat, _) =>
//      // This is present to notify Supervisor that component is now officially in Initialized state
//      // Use of goto ensures a transition notification, stay does not make a transition
//      logState(LifecycleInitialized, LifecycleInitialized)
//      goto(LifecycleInitialized) using ComponentInitialized
//  }
//
//  when(LifecycleRunning) {
//    case Event(ExComponentRestart, ComponentRunningOnline) =>
//      // No change if component is intializing
//      goto(LifecycleWaitingForInitialized) using ComponentInitializing
//    case Event(ExComponentOnline, ComponentRunningOnline) =>
//      // No change if component is Running Online
//      stay
//    case Event(ExComponentOffline, ComponentRunningOnline) =>
//      logState(LifecycleRunning, LifecycleRunningOffline)
//      goto(LifecycleRunningOffline) using ComponentRunningOffline
//    case Event(ExComponentShutdown, ComponentRunningOnline) =>
//      logState(LifecycleRunning, LifecycleWaitingForShutdown)
//      goto(LifecycleWaitingForShutdown) using ComponentShuttingDown
//    case Event(Heartbeat, _) =>
//      // This is present to notify Supervisor that component is now officially in Running state
//      // Use of goto ensures a transition notification, stay does not make a transition
//      logState(LifecycleRunning, LifecycleRunning)
//      goto(LifecycleRunning) using ComponentRunningOnline
//  }
//
//  when(LifecycleRunningOffline) {
//    case Event(ExComponentRestart, ComponentRunningOffline) =>
//      // No change if component is intializing
//      goto(LifecycleWaitingForInitialized) using ComponentInitializing
//    case Event(ExComponentOnline, ComponentRunningOffline) =>
//      // No change if component is initializing
//      goto(LifecycleRunning) using ComponentRunningOnline
//    case Event(ExComponentOffline, ComponentRunningOffline) =>
//      // No change if component is initializing
//      stay
//    case Event(ExComponentShutdown, ComponentRunningOffline) =>
//      logState(LifecycleRunningOffline, LifecycleWaitingForShutdown)
//      goto(LifecycleWaitingForShutdown) using ComponentShuttingDown
//    case Event(Heartbeat, _) =>
//      // This is present to notify Supervisor that component is now officially in Running state
//      // Use of goto ensures a transition notification, stay does not make a transition
//      logState(LifecycleRunning, LifecycleRunning)
//      goto(LifecycleRunningOffline) using ComponentRunningOffline
//  }
//
//  when(LifecycleWaitingForShutdown, stateTimeout = shutdownTimeout) {
//    case Event(ShutdownComplete, ComponentShuttingDown) =>
//      logState(LifecycleWaitingForShutdown, LifecycleShutdown)
//      goto(LifecycleShutdown) using ComponentShutDown
//    case Event(ShutdownFailure(reason), ComponentShuttingDown) =>
//      goto(LifecycleFailure) using FailureInfo(LifecycleWaitingForShutdown, reason)
//    case (Event(StateTimeout, ComponentShuttingDown)) =>
//      goto(LifecycleShutdownFailure) using ComponentFailedToShutdown
//  }
//
//  // Do nothing in shutdown
//  when(LifecycleShutdown) {
//    case Event(_, _) => stay
//  }
//
//  // Do nothing in shutdown failure
//  when(LifecycleShutdownFailure) {
//    case Event(_, _) => stay
//  }
//
//  when(LifecycleFailure) {
//    case Event(state@_, data@_) =>
//      log.info(s"Lifecycle failed event/data: $state/$data in state: $stateName/$stateData")
//      stop
//  }
//
//  onTransition {
//    case LifecycleInitialized -> LifecycleRunning =>
//      logTransition("sending Running to component")
//      component ! Running
//    case LifecycleRunningOffline -> LifecycleRunning =>
//      logTransition("sending Running to component")
//      component ! Running
//    case LifecycleRunning -> LifecycleRunningOffline =>
//      logTransition("sending RunningOffline to component")
//      component ! RunningOffline
//    case LifecycleRunning -> LifecycleWaitingForInitialized =>
//      logTransition("sending DoRestart to component")
//      component ! DoRestart
//    case LifecycleRunning -> LifecycleWaitingForShutdown =>
//      logTransition("sending DoShutdown to component")
//      component ! DoShutdown
//    case LifecycleRunningOffline -> LifecycleWaitingForShutdown =>
//      logTransition("sending DoShutdown to component")
//      component ! DoShutdown
//    case LifecycleWaitingForShutdown -> LifecycleShutdown =>
//      logTransition("shutdown successful")
//    case LifecycleWaitingForShutdown -> LifecycleShutdownFailure =>
//      component ! LifecycleFailure(LifecycleWaitingForShutdown, "Component shutdown timed out, shutting down")
//  }
//
//  onTransition {
//    case _ -> LifecycleFailure =>
//      log.info(s"Sending failure to component: $nextStateData")
//      nextStateData match {
//        case FailureInfo(nextState, reason) =>
//          component ! LifecycleFailure(nextState, reason)
//        case _@msg =>
//          log.error(s"While entering LifecycleFailure state from state: $stateName, received unknown data: $msg")
//      }
//  }
//
//  whenUnhandled {
//    case Event(state@_, data@__) =>
//      log.debug(s"Unhandled lifecycle event/data: $state/$data in state: $stateName/$stateData")
//      stay
//  }
//
//  def logState(nextState: LifecycleState2, s: LifecycleState2) = log.info(s"In $stateName/$stateData going to $nextState/$s")
//
//  def logTransition(message: String = "") = log.debug(s"On transition going from $stateName/$stateData to $nextStateData - $message")
//
//  def logSameStateError(stateName: LifecycleState2) = log.debug(s"Received: $stateName while in $stateName")
//
//  initialize()
//}
//
//// format: ON    <-- this directive enables scalariform formatting from this point
//
