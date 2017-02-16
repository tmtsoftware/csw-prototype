package csw.services.pkg

import akka.actor._
import Supervisor._
import csw.services.loc.{ComponentId, LocationService}
import csw.services.pkg.Component.{ComponentInfo, DoNotRegister}

import scala.util.{Failure, Success}

object Supervisor {

  private def makeActorSystem(componentInfo: ComponentInfo): ActorSystem = {
    ActorSystem(s"${componentInfo.componentName}-system")
  }

  /**
   * Returns a new supervisor actor managing the components described in the argument
   *
   * @param componentInfo describes the components to create and manage
   * @return the ActorRef for the supervisor (parent actor of the top level component)
   *         and the component's new ActorSystem as a tuple.
   */
  def apply(componentInfo: ComponentInfo): ActorRef = {
    val system = makeActorSystem(componentInfo)
    system.actorOf(props(componentInfo), s"${componentInfo.componentName}-supervisor")
  }

  /**
   * Returns a new actor system and supervisor actor managing the components described in the argument
   *
   * @param componentInfo describes the components to create and manage
   * @return the actorRef for the supervisor (parent actor of the top level component)
   */
  def create(componentInfo: ComponentInfo): (ActorSystem, ActorRef) = {
    val system = makeActorSystem(componentInfo)
    (system, system.actorOf(props(componentInfo), s"${componentInfo.componentName}-supervisor"))
  }

  /**
   * Used to create the Supervisor actor
   *
   * @param componentInfo used to create the component
   * @return an object to be used to create the Supervisor actor
   */
  def props(componentInfo: ComponentInfo): Props = {
    Props(classOf[Supervisor], componentInfo, None)
  }

  //  def props(componentInfo: ComponentInfo, componentActor: Option[ActorRef]) = Props(classOf[Supervisor], componentInfo, componentActor)
  def props(componentInfo: ComponentInfo, componentActor: Option[ActorRef]): Props = {
    Props(new Supervisor(componentInfo, componentActor))
  }

  /**
   * The [[LifecycleState]] is the lifecycle state of the compoennt managed by the Supervisor instance
   */
  sealed trait LifecycleState

  /**
   * State of the supervisor/component when started and Supervisor is waiting for the [[Initialized]] lifecycle
   * message from the component
   */
  case object LifecycleWaitingForInitialized extends LifecycleState

  /**
   * State of the Supervisor/component after the component has indicated it could not initialize successfully.
   * Component receives the [[LifecycleFailureInfo]] message and can take a failure action.
   */
  case object LifecycleInitializeFailure extends LifecycleState

  /**
   * State of the supervisor/component after Supervisor receives the [[csw.services.pkg.Supervisor.Initialized]]
   * message from the component.
   * Component receives a [[csw.services.pkg.Supervisor.Running]] message indicating this.
   * Component is Running and Online at this point.
   */
  case object LifecycleRunning extends LifecycleState

  /**
   * State of the supervisor/component after Supervisor receives an [[csw.services.pkg.SupervisorExternal.ExComponentOffline]]
   * message from an external actor to place the component offline.
   * The component receives the [[csw.services.pkg.Supervisor.RunningOffline]] message indicating this change.
   */
  case object LifecycleRunningOffline extends LifecycleState

  /**
   * State of the supervisor/component after Supervisor receives an [[csw.services.pkg.SupervisorExternal.ExComponentShutdown]]
   * message from an external actor to shutdown the component.
   * The component receives the [[csw.services.pkg.Supervisor.DoShutdown]] message indicating this and should then
   * take actions to prepare itself for shutdown.
   * The Supervisor waits for either the [[csw.services.pkg.Supervisor.ShutdownComplete]] or [[csw.services.pkg.Supervisor.ShutdownFailure]]
   * message from the component.
   */
  case object LifecyclePreparingToShutdown extends LifecycleState

  /**
   * State of the Supervisor/component after the component has indicated it is ready to shutdown by sending the
   * the [[csw.services.pkg.Supervisor.ShutdownComplete]] message to the Supervisor.
   */
  case object LifecycleShutdown extends LifecycleState

  /**
   * State of the Supervisor/component when the component indicated it could not get ready to shutdown or failed
   * to notify the Supervisor with the [[csw.services.pkg.Supervisor.ShutdownComplete]] message within the
   * timeout period.
   */
  case object LifecycleShutdownFailure extends LifecycleState

  /**
   * Messages sent to components to notify of lifecycle changes
   */
  sealed trait ToComponentLifecycleMessage

  /**
   * Component has been requested to prepare itself to be shutdown
   */
  case object DoShutdown extends ToComponentLifecycleMessage

  /**
   * The component has been requested to restart by re-executing its initialization process.
   */
  case object DoRestart extends ToComponentLifecycleMessage

  /**
   * Supervisor reports to the component that it is Running and Online
   */
  case object Running extends ToComponentLifecycleMessage

  /**
   * Supervsior reports that the component is Running but is Offline
   */
  case object RunningOffline extends ToComponentLifecycleMessage

  // Report to component that a lifecycle failure has occurred for logging, etc.
  /**
   * Message sent by the Supervisor to the component when it has entered a lifecycle failure state
   * The component can take action when receiving this message such as logging
   *
   * @param state the state that has failed
   * @param reason a string describing the reason for the failure
   */
  case class LifecycleFailureInfo(state: LifecycleState, reason: String) extends ToComponentLifecycleMessage

  /**
   * Messages from component indicating events
   */
  sealed trait FromComponentLifecycleMessage

  // Component indicates it has Initialized successfully
  case object Initialized extends FromComponentLifecycleMessage

  /**
   * Component indicates it failed to initialize with the given reason
   *
   * @param reason the reason for failing to initialize as a String
   */
  case class InitializeFailure(reason: String) extends FromComponentLifecycleMessage

  /**
   * Component indicates it has completed shutting down successfully
   */
  case object ShutdownComplete extends FromComponentLifecycleMessage

  /**
   * Component indicates it has failed to shutdown properly with the given reason
   *
   * @param reason reason for failing to shutdown as a String
   */
  case class ShutdownFailure(reason: String) extends FromComponentLifecycleMessage

  /**
   * Diagnostic message to shutdown and then exit supervisor/component
   */
  case object HaltComponent extends FromComponentLifecycleMessage

  /**
   * Function called by a component to indicate the lifecycle should be started.
   *
   * @param supervisor the ActorRef of the component's supervisor
   * @param command    the [[FromComponentLifecycleMessage]] to be sent to the supervisor
   */
  def lifecycle(supervisor: ActorRef, command: FromComponentLifecycleMessage): Unit = {
    supervisor ! command
  }

  /**
   * Function called by a component to exit in a controlled way.
   *
   * @param supervisor the ActorRef of the component's supervisor
   */
  def haltComponent(supervisor: ActorRef): Unit = {
    supervisor ! HaltComponent
  }

}

object SupervisorExternal {

  /**
   * Base trait of supervisor actor messages
   */
  sealed trait SupervisorExternalMessage

  /**
   * Message used to subscribe the sender to changes in lifecycle states
   *
   * @param actorRef the actor subscribing to callbacks
   */
  case class SubscribeLifecycleCallback(actorRef: ActorRef) extends SupervisorExternalMessage

  /**
   * Message to unsubscribe  from lifecycle state changes
   */
  case class UnsubscribeLifecycleCallback(actorRef: ActorRef) extends SupervisorExternalMessage

  /**
   * Message sent to subscribers of lifecycle states
   *
   * @param state the current state as a String
   */
  case class LifecycleStateChanged(state: LifecycleState) extends SupervisorExternalMessage

  /**
   * External request to restart component initialization -- only possible in LifecycleRunning and LifecycleRunningOffline
   */
  case object ExComponentRestart extends SupervisorExternalMessage

  /**
   * External request to shutdown component -- only possible in in LifecycleRunning and LifecycleRunningOffline
   */
  case object ExComponentShutdown extends SupervisorExternalMessage

  /**
   * External request to put component onlne -- only possible in LifecycleRunningOffline
   */
  case object ExComponentOnline extends SupervisorExternalMessage

  /**
   * External request to put component offline -- only possible in LifecycleRunning
   */
  case object ExComponentOffline extends SupervisorExternalMessage

}

/**
 * Supervisor provides the interface between CSW and the component top level actor.
 *
 * Supervisor manages the lifecycle for a component and can receive lifecycle messages from outside the component
 * from a container or other admin-related application. It can also receive lifecycle-related requests from the
 * component.
 *
 * Supervisor is created with the component's top level actor:
 *  1. It handles registering and unregistering the component using information in component info and is triggered
 *     by the lifecycle changes.
 *  2. Supervisor provides the connection for commands to other components.
 *
 * @param componentInfo information describing the top level actor for the component
 * @param testComponent a testing component (or test probe) that can be passed to the Supervisor for testing rather than
 *                      Supervisor creating the component's top level actor
 */
class Supervisor(val componentInfo: ComponentInfo, testComponent: Option[ActorRef]) extends Actor with ActorLogging {

  import scala.concurrent.duration._
  import SupervisorExternal._

  private val name = componentInfo.componentName
  private val componentId = ComponentId(name, componentInfo.componentType)

  // This is present to bypass the component creation code and inject a testprobe
  var lifecycleState: LifecycleState = LifecycleWaitingForInitialized

  // In case we want to add some extra messages
  def receive: Receive = lifecycleWaitingForInitialized

  // Initial delay before starting component allows listener to subscribe
  val component: ActorRef = testComponent match {
    case None           => startComponent(context, componentInfo)
    case Some(actorRef) => actorRef
  }

  // Starts the component actor
  def startComponent(context: ActorContext, componentInfo: ComponentInfo): ActorRef = {
    // This is currently needed to give time to process a subscription, need a better way
    Thread.sleep(100)
    log.debug(s"Starting ${componentInfo.componentName}")
    val actorRef = Component.create(context, componentInfo)
    context.watch(actorRef)
    actorRef
  }

  // This is set once the component is registered with the location service
  var registrationOpt: Option[LocationService.RegistrationResult] = None

  // If the component is configured to register with the location service, do it,
  // and save the result for unregistering later.
  private def registerWithLocationService(): Unit = {
    import context.dispatcher
    if (componentInfo.locationServiceUsage != DoNotRegister) {
      LocationService.registerAkkaConnection(componentId, self, componentInfo.prefix)(context.system).onComplete {
        case Success(reg) ⇒
          registrationOpt = Some(reg)
          log.info(s"$name: Registered $componentId with the location service")
        case Failure(ex) ⇒
          // Choice allan: What to do in case of error?
          log.error(s"$name: Failed to register $componentId with the location service")
      }
    } else log.info(s"$name: Not registering $componentId")
  }

  // If the component is registered with the location service, unregister it
  private def unregisterFromLocationService(): Unit = {
    registrationOpt.foreach {
      log.info(s"Unregistering $componentId from the location service")
      _.unregister()
    }
  }

  // The default supervision behavior will normally restart the component automatically.
  // (allan: check this: needs to be properly configured)
  // The Terminated message should only be received if we manually stop the component, or a
  // system error occurs (Exceptions don't cause termination).
  private def terminated(actorRef: ActorRef): Unit = {
    log.info(s"$name: $actorRef has terminated")
    unregisterFromLocationService()

    // XXX allan: Temp: For now, make sure the actor system is not left over between test cases...
    context.stop(self)
    context.system.terminate
  }

  private def haltComponent(): Unit = {
    log.info(s"Halting component: ${componentInfo.componentName}")
    context.stop(self)
    // Kim commented this out since it keeps the tests from running entirely
    //context.system.terminate
    //System.exit(-1)
  }

  private val shutdownTimeout = 5.seconds
  private var shutdownTimer: Option[Cancellable] = None

  // Used to timeout when waiting for shutdown
  private case object ShutdownTimeout

  // This flag is set to indicate Halt has been issued and after Shtudown, component will be halted
  private var haltingFlag = false

  /**
   * The component triggers the changes in the lifecycle.
   * When the component starts up, nothing happens until the component sends Initialized or InitializedFailure
   * There is no timeout waiting for Initialized
   */

  /**
   * Receive method for the lifecycleWaitingForInitialized state
   */
  private def lifecycleWaitingForInitialized = lifecycleWaitingForInitializedPF orElse commonMessagesPF

  // Partial function for the lifecycleInitialized state
  private def lifecycleWaitingForInitializedPF: Receive = {
    case Initialized =>
      logState(LifecycleWaitingForInitialized, LifecycleRunning)
      // Actions for moving to the next state
      log.debug("In Initialized, registering with Location Service")
      registerWithLocationService()
      log.debug(s"lifecycleInitialized: sending Running to component $component")
      component ! Running
      lifecycleState = LifecycleRunning
      context.become(lifecycleRunning)
      // This transition indicates component is in running state
      notifyListeners(LifecycleStateChanged(LifecycleRunning))
    case InitializeFailure(reason) =>
      logState(LifecycleWaitingForInitialized, LifecycleInitializeFailure)
      handleLifecycleFailure(LifecycleWaitingForInitialized, reason)
      lifecycleState = LifecycleInitializeFailure
      context.become(lifecycleFailure)
    case ExComponentRestart | ExComponentOnline | ExComponentOffline | ExComponentShutdown =>
    // Stay
  }

  /**
   * Receive method for the lifecycleRunning state
   * Note that order is important because lifecycleRuninng passes all messages right now
   */
  private def lifecycleRunning = commonMessagesPF orElse lifecycleRunningPF

  // Partial function for the lifecycleRunning state
  private def lifecycleRunningPF: Receive = {
    case ExComponentOffline =>
      logState(LifecycleRunning, LifecycleRunningOffline)
      log.debug("lifecycleRunning: sending RunningOffline to component")
      component ! RunningOffline
      lifecycleState = LifecycleRunningOffline
      context.become(lifecycleRunningOffline)
    case ExComponentOnline =>
    // Stay
    case ExComponentRestart =>
      logState(LifecycleRunning, LifecycleWaitingForInitialized)
      log.debug("lifecycleRunning: sending DoRestart to component")
      component ! DoRestart
      unregisterFromLocationService()
      lifecycleState = LifecycleWaitingForInitialized
      context.become(lifecycleWaitingForInitialized)
    case ExComponentShutdown =>
      logState(LifecycleRunning, LifecyclePreparingToShutdown)
      log.debug("lifecycleRunning: sending DoShutdown to component")
      component ! DoShutdown
      // This sets a timer to send a message if the component does not respond within shutdownTimeout
      shutdownTimer = Some(scheduleTimeout)
      unregisterFromLocationService()
      lifecycleState = LifecyclePreparingToShutdown
      // Transition indicates component is shutting down
      notifyListeners(LifecycleStateChanged(LifecyclePreparingToShutdown))
      context.become(lifecyclePreparingToShutdown)
    case m @ _ => component forward m
  }

  /**
   * Receive method for the lifecycleRunningOffline state
   */
  private def lifecycleRunningOffline = commonMessagesPF orElse lifecycleRunningOfflinePF

  // Partial function for the lifecycleRunningOffline state
  private def lifecycleRunningOfflinePF: Receive = {
    case ExComponentOffline =>
    // Stay
    case ExComponentOnline =>
      logState(LifecycleRunningOffline, LifecycleRunning)
      log.debug("lifecycleRunningOffline: sending Running to component")
      component ! Running
      lifecycleState = LifecycleRunning
      context.become(lifecycleRunning)
    case ExComponentRestart =>
      logState(LifecycleRunningOffline, LifecycleWaitingForInitialized)
      log.debug("lifecycleRunningOffline: sending DoRestart to component")
      component ! DoRestart
      unregisterFromLocationService()
      lifecycleState = LifecycleWaitingForInitialized
      context.become(lifecycleWaitingForInitialized)
    case ExComponentShutdown =>
      logState(LifecycleRunningOffline, LifecyclePreparingToShutdown)
      // This sets a timer to send a message if the component does not respond within shutdownTimeout
      shutdownTimer = Some(scheduleTimeout)
      log.debug("lifecycleRunningOffline: sending DoShutdown to component")
      component ! DoShutdown
      unregisterFromLocationService()
      lifecycleState = LifecyclePreparingToShutdown
      context.become(lifecyclePreparingToShutdown)
    case m @ _ => component forward m
  }

  /**
   * Receive method for the lifecyclePreparingToShutdown state
   */
  private def lifecyclePreparingToShutdown: Receive = {
    case ShutdownComplete =>
      // First cancel the shutdown timer
      shutdownTimer.map(_.cancel())
      logState(LifecyclePreparingToShutdown, LifecycleShutdown)
      log.debug("lifecycleWatingForShutdown: shutdown successful")
      lifecycleState = LifecycleShutdown
      // Transition means component has shutdown
      notifyListeners(LifecycleStateChanged(LifecycleShutdown))
      // Special case for halting
      if (haltingFlag) haltComponent()
      context.become(lifecycleShutdown)
    case ShutdownFailure(reason) =>
      handleLifecycleFailure(LifecyclePreparingToShutdown, reason)
      lifecycleState = LifecycleShutdownFailure
      // Transition means component has shutdown
      notifyListeners(LifecycleStateChanged(LifecycleShutdownFailure))
      // Special case for halting
      if (haltingFlag) haltComponent()
      context.become(lifecycleShutdownFailure)
    case ShutdownTimeout =>
      handleLifecycleFailure(LifecyclePreparingToShutdown, "Component did now shutdown within alloted time.")
      lifecycleState = LifecycleShutdownFailure
      // Transition means component has shutdown
      notifyListeners(LifecycleStateChanged(LifecycleShutdownFailure))
      // Special case for halting
      if (haltingFlag) haltComponent()
      context.become(lifecycleShutdownFailure)
  }

  /**
   * Receive method for the lifecycleShutdown state
   */
  private def lifecycleShutdown = lifecycleShutdownPF orElse commonMessagesPF

  // Partial function for the lifecycleShutdown state
  private def lifecycleShutdownPF: Receive = {
    case x ⇒ log.debug(s"Supervisor in lifecycleShutdown received an unexpected message: $x ")
  }

  /**
   * Receive method for the lifecycleFailure state
   */
  private def lifecycleFailure: Receive = {
    case x ⇒ log.debug(s"Supervisor in lifecycleFailure received an unexpected message: $x ")
  }

  /**
   * Recieve method for the lifecycleShutdownFailure state
   */
  private def lifecycleShutdownFailure: Receive = {
    case x ⇒ log.debug(s"Supervisor in lifecycleShutdownFailure received an unexpected message: $x ")
  }

  // Partial function combined with others to receive common messages
  private def commonMessagesPF: Receive = {
    case SubscribeLifecycleCallback(actorRef) =>
      log.debug("Adding listener: " + actorRef)
      addListener(actorRef)
    case UnsubscribeLifecycleCallback(actorRef) ⇒
      removeListener(actorRef)
    case Terminated(actorRef) ⇒
      terminated(actorRef)
    case HaltComponent =>
      haltingFlag = true
      self ! ExComponentShutdown
  }

  // Helper method to create the shutdown timer
  private def scheduleTimeout: Cancellable = {
    // Needed for the shutdown timer
    import context.dispatcher
    context.system.scheduler.scheduleOnce(shutdownTimeout, self, ShutdownTimeout)
  }

  // Helper method for handling lifecycle failures
  private def handleLifecycleFailure(state: LifecycleState, reason: String) = {
    log.info(s"Sending failure to component: $componentId")
    log.info(s"Lifecycle failure event from state: $state")
    component ! LifecycleFailureInfo(state, reason)
  }

  // Used to log messages for state changes
  private def logState(thisState: LifecycleState, nextState: LifecycleState) = log.debug(s"$name: In $thisState going to $nextState")

  // The following is listener support for the container or other interested component
  private var listeners = Set[ActorRef]()

  private def addListener(l: ActorRef) = listeners = listeners + l

  private def removeListener(l: ActorRef) = listeners = listeners - l

  /**
   * Sends the supplied message to all current listeners using the provided sender() as sender.
   *
   * @param msg a message to send to all listeners
   */
  private def notifyListeners(msg: Any): Unit = {
    //log.info("Listeners: " + listeners)
    listeners.foreach(_ ! msg)
  }
}

