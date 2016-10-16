package csw.services.pkg

import akka.actor._
import Supervisor3._
import csw.services.loc.{ComponentId, LocationService}
import csw.services.pkg.Component.{ComponentInfo, DoNotRegister}

import scala.util.{Failure, Success}

class Supervisor3(val componentInfo: ComponentInfo, testComponent: Option[ActorRef]) extends Actor with ActorLogging {

  import scala.concurrent.duration._
  import SupervisorExternal._

  private val name = componentInfo.componentName
  private val componentId = ComponentId(name, componentInfo.componentType)

  // This is present to bypass the component creation code and inject a testprobe
  var lifecycleState: LifecycleState = LifecycleWaitingForInitialized

  // In case we want to add some extra messages
  def receive = lifecycleWaitingForInitialized

  // Initial delay before starting component allows listener to subscribe
  val component: ActorRef = testComponent match {
    case None           => startComponent(context, componentInfo)
    case Some(actorRef) => actorRef
  }

  // Starts the component actor
  def startComponent(context: ActorContext, componentInfo: ComponentInfo): ActorRef = {
    // This is currently needed to give time to process a subscription, need a better way
    Thread.sleep(100)
    log.info(s"Starting ${componentInfo.componentName}")
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
  // (Choice allan: check this: needs to be properly configured)
  // The Terminated message should only be received if we manually stop the component, or a
  // system error occurs (Exceptions don't cause termination).
  private def terminated(actorRef: ActorRef): Unit = {
    log.info(s"$name: $actorRef has terminated")
    unregisterFromLocationService()
  }

  private def haltComponent(): Unit = {
    log.info(s"Halting component: ${componentInfo.componentName}")
    context.stop(self)
    context.system.terminate
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
  def lifecycleWaitingForInitialized = lifecycleWaitingForInitializedPF orElse commonMessagesPF

  // Partial function for the lifecycleWaitingForInitialized state
  def lifecycleWaitingForInitializedPF: Receive = {
    case Initialized =>
      logState(LifecycleWaitingForInitialized, LifecycleInitialized)
      // Actions for moving to the next state
      log.info("In Initialized, registering with Location Service")
      registerWithLocationService()
      lifecycleState = LifecycleInitialized
      // Transition to the new state
      context.become(lifecycleInitialized)
      // This transition indicates the component is now firmly in Initialized
      notifyListeners(LifecycleStateChanged(LifecycleInitialized))
    case InitializeFailure(reason) =>
      logState(LifecycleWaitingForInitialized, LifecycleFailure)
      handleLifecycleFailure(LifecycleWaitingForInitialized, reason)
      lifecycleState = LifecycleFailure
      context.become(lifecycleFailure)
    case Started | HaltComponent | ExComponentRestart | ExComponentOnline | ExComponentOffline | ExComponentShutdown =>
    // Stay
  }

  /**
   * Receive method for the lifecycleInitialized state
   */
  def lifecycleInitialized = lifecycleInitializedPF orElse commonMessagesPF

  // Partial function for the lifecycleInitialized state
  def lifecycleInitializedPF: Receive = {
    case Started =>
      logState(LifecycleInitialized, LifecycleRunning)
      log.info("lifecycleInitialized: sending Running to component")
      component ! Running
      lifecycleState = LifecycleRunning
      context.become(lifecycleRunning)
      // This transition indicates component is in running state
      notifyListeners(LifecycleStateChanged(LifecycleRunning))
    case StartupFailure(reason) =>
      logState(LifecycleInitialized, LifecycleFailure)
      handleLifecycleFailure(LifecycleInitialized, reason)
      lifecycleState = LifecycleFailure
      context.become(lifecycleFailure)
    case Initialized | HaltComponent | ExComponentRestart | ExComponentOnline | ExComponentOffline | ExComponentShutdown =>
    // Stay
  }

  /**
   * Receive method for the lifecycleRunning state
   * Note that order is important because lifecycleRuninng passes all messages right now
   */
  def lifecycleRunning = commonMessagesPF orElse lifecycleRunningPF

  // Partial function for the lifecycleRunning state
  def lifecycleRunningPF: Receive = {
    case ExComponentOffline =>
      logState(LifecycleRunning, LifecycleRunningOffline)
      log.info("lifecycleRunning: sending RunningOffline to component")
      component ! RunningOffline
      lifecycleState = LifecycleRunningOffline
      context.become(lifecycleRunningOffline)
    case ExComponentOnline =>
    // Stay
    case ExComponentRestart =>
      logState(LifecycleRunning, LifecycleWaitingForInitialized)
      log.info("lifecycleRunning: sending DoRestart to component")
      component ! DoRestart
      log.info("Unregister")
      unregisterFromLocationService()
      lifecycleState = LifecycleWaitingForInitialized
      context.become(lifecycleWaitingForInitialized)
    case ExComponentShutdown =>
      logState(LifecycleRunning, LifecyclePreparingToShutdown)
      log.info("lifecycleRunning: sending DoShutdown to component")
      component ! DoShutdown
      // This sets a timer to send a message if the component does not respond within shutdownTimeout
      shutdownTimer = Some(scheduleTimeout)
      log.info("Unregister")
      unregisterFromLocationService()
      lifecycleState = LifecyclePreparingToShutdown
      // Transition indicates component is shutting down
      notifyListeners(LifecycleStateChanged(LifecyclePreparingToShutdown))
      context.become(lifecyclePreparingToShutdown)
    case m @ _ => component forward m
  }

  // Partial funciton for receiving and passing messages to the component (may need to be less open)
  def supervisorReceivePF: Receive = {
    case m @ _ => component forward m
  }

  /**
   * Receive method for the lifecycleRunningOffline state
   */
  def lifecycleRunningOffline = commonMessagesPF orElse lifecycleRunningOfflinePF

  // Partial function for the lifecycleRunningOffline state
  def lifecycleRunningOfflinePF: Receive = {
    case ExComponentOffline =>
    // Stay
    case ExComponentOnline =>
      logState(LifecycleRunningOffline, LifecycleRunning)
      log.info("lifecycleRunningOffline: sending Running to component")
      component ! Running
      lifecycleState = LifecycleRunning
      context.become(lifecycleRunning)
    case ExComponentRestart =>
      logState(LifecycleRunningOffline, LifecycleWaitingForInitialized)
      log.info("lifecycleRunningOffline: sending DoRestart to component")
      component ! DoRestart
      log.info("Unregister")
      unregisterFromLocationService()
      lifecycleState = LifecycleWaitingForInitialized
      context.become(lifecycleWaitingForInitialized)
    case ExComponentShutdown =>
      logState(LifecycleRunningOffline, LifecyclePreparingToShutdown)
      // This sets a timer to send a message if the component does not respond within shutdownTimeout
      shutdownTimer = Some(scheduleTimeout)
      log.info("lifecycleRunningOffline: sending DoShutdown to component")
      component ! DoShutdown
      log.info("Unregister")
      unregisterFromLocationService()
      lifecycleState = LifecyclePreparingToShutdown
      context.become(lifecyclePreparingToShutdown)
    case m @ _ => component forward m
  }

  /**
   * Receive method for the lifecyclePreparingToShutdown state
   */
  def lifecyclePreparingToShutdown: Receive = {
    case ShutdownComplete =>
      // First cancel the shutdown timer
      shutdownTimer.map(_.cancel())
      logState(LifecyclePreparingToShutdown, LifecycleShutdown)
      log.info("lifecycleWatingForShutdown: shutdown successful")
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
  def lifecycleShutdown = lifecycleShutdownPF orElse commonMessagesPF

  // Partial function for the lifecycleShutdown state
  def lifecycleShutdownPF: Receive = {
    case x ⇒ log.info(s"Supervisor in lifecycleShutdown received an unexpected message: $x ")
  }

  /**
   * Receive method for the lifecycleFailure state
   */
  def lifecycleFailure: Receive = {
    case x ⇒ log.info(s"Supervisor in lifecycleFailure received an unexpected message: $x ")
  }

  /**
   * Recieve method for the lifecycleShutdownFailure state
   */
  def lifecycleShutdownFailure: Receive = {
    case x ⇒ log.info(s"Supervisor in lifecycleShutdownFailure received an unexpected message: $x ")
  }

  // Partial function combined with others to receive common messages
  def commonMessagesPF: Receive = {
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
  def scheduleTimeout: Cancellable = {
    // Needed for the shutdown timer
    import context.dispatcher
    context.system.scheduler.scheduleOnce(shutdownTimeout, self, ShutdownTimeout)
  }

  // Helper method for handling lifecycle failures
  def handleLifecycleFailure(state: LifecycleState, reason: String) = {
    log.info(s"Sending failure to component: $componentId")
    log.info(s"Lifecycle failure event from state: $state")
    component ! LifecycleFailureInfo(state, reason)
  }

  // Used to log messages for state changes
  def logState(thisState: LifecycleState, nextState: LifecycleState) = log.info(s"In $thisState going to $nextState")

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

object Supervisor3 {

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
    Props(classOf[Supervisor3], componentInfo, None)
  }

  //  def props(componentInfo: ComponentInfo, componentActor: Option[ActorRef]) = Props(classOf[Supervisor3], componentInfo, componentActor)
  def props(componentInfo: ComponentInfo, componentActor: Option[ActorRef]): Props = {
    Props(new Supervisor3(componentInfo, componentActor))
  }

  // The following are states used for the Supervisor lifecycle manager
  sealed trait LifecycleState

  /**
   * State of the Supervisor when started and waiting for the first lifecycle message from the component.
   */
  case object LifecycleWaitingForInitialized extends LifecycleState

  /**
   * State of the Supervisor when Initialized after receiving the [[csw.services.pkg.Supervisor3.Initialized]]
   * message (first) from the component
   */
  case object LifecycleInitialized extends LifecycleState

  /**
   * State of the Supervisor after receiving the [[csw.services.pkg.Supervisor3.Started]]
   * message (second) from the component. Component is Running and Online at this point.
   * Component receives a [[csw.services.pkg.Supervisor3.Running]] message indicating this.
   */
  case object LifecycleRunning extends LifecycleState

  /**
   * State of the Supervisor/component after receiving an [[csw.services.pkg.SupervisorExternal.ExComponentOffline]]
   * message to place the component offline. The component receives the [[csw.services.pkg.Supervisor3.RunningOffline]]
   * message indicating this.
   */
  case object LifecycleRunningOffline extends LifecycleState

  /**
   * State of the Supervisor/component after receiving an [[csw.services.pkg.SupervisorExternal.ExComponentShutdown]]
   * message to shutdown the component. The component receives the [[csw.services.pkg.Supervisor3.DoShutdown]]
   * message indicating this.
   */
  case object LifecyclePreparingToShutdown extends LifecycleState

  /**
   * State of the Supervisor/component after the component has indicated it could not initialize or startup
   * successfully.
   */
  case object LifecycleFailure extends LifecycleState

  /**
   * State of the Supervisor/component after the component has indicated it is ready to shutdown after receiving
   * the [[csw.services.pkg.Supervisor3.ShutdownComplete]] message.
   */
  case object LifecycleShutdown extends LifecycleState

  /**
   * State of the Supervisor/component when the component indicated it could not get ready to shutdown or failed
   * to notify the Supervisor with the [[csw.services.pkg.Supervisor3.ShutdownComplete]] message within the
   * timeout.
   */
  case object LifecycleShutdownFailure extends LifecycleState

  /**
   * Messages sent to components to notify of lifecycle changes
   */
  sealed trait ToComponentLifecycleMessage

  // Someone has requested that the component shutdown
  case object DoShutdown extends ToComponentLifecycleMessage

  // Someone has requested that the component restart by going back to uninitialized
  case object DoRestart extends ToComponentLifecycleMessage

  // Supervisor reports that component is in Running and Online
  case object Running extends ToComponentLifecycleMessage

  // Supervisor reports that compoentn is Running but is Offline
  case object RunningOffline extends ToComponentLifecycleMessage

  // Report to component that a lifecycle failure has occurred for logging, etc.
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
   * Component indicates it has started successfully
   */
  case object Started extends FromComponentLifecycleMessage

  /**
   * Component indicates it failed to startup with the given reason
   *
   * @param reason reason for failing to startup as a String
   */
  case class StartupFailure(reason: String) extends FromComponentLifecycleMessage

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

