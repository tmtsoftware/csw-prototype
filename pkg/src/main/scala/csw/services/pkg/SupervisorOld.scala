package csw.services.pkg

import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.actor._
import csw.services.ccs.AssemblyControllerOld.AssemblyControllerMessage
import csw.services.ccs.HcdController.HcdControllerMessage
import csw.services.loc.ComponentType.{Assembly, HCD}
import csw.services.loc.{ComponentId, LocationService}
import csw.services.pkg.Component.{ComponentInfo, DoNotRegister}
import csw.services.pkg.LifecycleManager._
import csw.util.akka.{PublisherActor, SetLogLevelActor}

import scala.util.{Failure, Success}

/**
 * The Supervisor is an actor that supervises the component actors and deals with
 * component lifecycle messages so components don't have to. There is one Supervisor per component.
 * It registers with the location service and is responsible for starting and stopping the component
 * as well as managing its state.
 * All component messages go through the Supervisor, so it can reject any
 * messages that are not allowed in a given lifecycle.
 *
 * See the TMT document "OSW TN012 - COMPONENT LIFECYCLE DESIGN" for a description of CSW lifecycles.
 */
object SupervisorOld {

  private def makeActorSystem(componentInfo: ComponentInfo): ActorSystem = {
    ActorSystem(s"${componentInfo.componentName}-system")
  }

  /**
   * Returns a new supervisor actor managing the components described in the argument
   * @param componentInfo describes the components to create and manage
   * @return the actorRef for the supervisor (parent actor of the top level component)
   */
  def apply(componentInfo: ComponentInfo): ActorRef = {
    val system = makeActorSystem(componentInfo)
    system.actorOf(props(componentInfo), s"${componentInfo.componentName}-supervisor")
  }

  /**
   * Returns a new actor system and supervisor actor managing the components described in the argument
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
  private def props(componentInfo: ComponentInfo): Props = Props(classOf[SupervisorOld], componentInfo, componentInfo.prefix)

  /**
   * Base trair of supervisor actor messages
   */
  sealed trait SupervisorMessage

  /**
   * Message used to subscribe the sender to changes in lifecycle states
   *
   * @param actorRef the actor subscribing to callbacks
   */
  case class SubscribeLifecycleCallback(actorRef: ActorRef) extends SupervisorMessage

  /**
   * Message to unsubscribe  from lifecycle state changes
   */
  case class UnsubscribeLifecycleCallback(actorRef: ActorRef) extends SupervisorMessage

  /**
   * Message sent to subscribers of lifecycle states
   *
   * @param state the current state
   */
  case class LifecycleStateChanged(state: LifecycleState) extends SupervisorMessage

  /**
   * Message sent to supervisor from component or elsewhere to restart lifecycle for component
   */
  case object RestartComponent extends SupervisorMessage

  /**
   * When this message is received, the component goes through shutdown, uninitialize, and then exits
   */
  case object HaltComponent extends SupervisorMessage

  /**
   * Function called by a component to indicate the lifecycle should be started.
   *
   * @param supervisor the ActorRef of the component's supervisor
   * @param command    the LifecycleCommand to be sent to the supervisor
   */
  def lifecycle(supervisor: ActorRef, command: LifecycleCommand = Startup): Unit = {
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

/**
 * A supervisor actor that manages the component actor given by the arguments
 * (see props() for argument descriptions).
 */
private final class SupervisorOld(val componentInfo: ComponentInfo, val prefix: String)
    extends Actor with ActorLogging with SetLogLevelActor {

  import SupervisorOld._

  log.debug("Starting Supervisor:" + context.self.path)

  private val name = componentInfo.componentName
  private val componentId = ComponentId(name, componentInfo.componentType)
  private val component = startComponent(context, componentInfo)

  // This is set once the component is registered with the location service
  private var registrationOpt: Option[LocationService.RegistrationResult] = None

  private val lifecycleManager = context.actorOf(LifecycleManager.props(component, name), "lifecycleManager")
  lifecycleManager ! SubscribeTransitionCallBack(self)

  private def commonMessageReceive: Receive = logLevelReceive orElse {
    case SubscribeLifecycleCallback(actorRef) =>
      addListener(actorRef)
    case UnsubscribeLifecycleCallback(actorRef) =>
      removeListener(actorRef)
    case Terminated(actorRef) =>
      terminated(actorRef)
    case Heartbeat =>
      // Forward to lifecycle manager - causes it to reply with the current state
      lifecycleManager ! Heartbeat
    // Forward Subscribe/Unsubscribe messages to the component (HCD and Assembly support subscriptions)
    case msg: PublisherActor.PublisherActorMessage =>
      component.tell(msg, sender())
    case x =>
      log.warning(s"$name: Supervisor received an unexpected message: $x")
  }

  private def notRunningReceivePF: Receive = {
    case CurrentState(_, Loaded) =>
      // This message is sent as a side effect of subscribing to the Lifecycle FSM
      log.debug(s"$name: LifecycleManager indicates Loaded")
      notifyListeners(LifecycleStateChanged(Loaded))
    case Initialize =>
      log.debug(s"$name: Handle Initialize message from container or elsewhere")
      lifecycleManager ! Initialize
    case Startup =>
      log.debug(s"$name: Handle Startup message from container or elsewhere")
      lifecycleManager ! Startup
    case Transition(_, PendingInitializedFromLoaded, Initialized) =>
      // LifecycleManager has sent Initialized to component and is waiting for response
      registerWithLocationService()
    case Transition(_, Initialized, PendingLoadedFromInitialized) =>
      unregisterFromLocationService()
    case Transition(_, Loaded, Loaded) =>
      // This transition indicates the component is now firmly in Loaded (only during shutdown/restart
      notifyListeners(LifecycleStateChanged(Loaded))
    case Transition(_, Initialized, Initialized) =>
      // This transition indicates the component is now firmly in Initialized from Loaded or Running
      notifyListeners(LifecycleStateChanged(Initialized))
    case Transition(_, Running, Running) =>
      // This transition indicates the component is now firmly in Running
      log.debug(s"$name: Transition to Running")
      notifyListeners(LifecycleStateChanged(Running))
      context become runningReceive
    case t @ Transition(_, from, to) =>
      log.debug(s"$name: notRunningReceivePF: unhandled transition: $from/$to")
  }

  def notRunningReceive = notRunningReceivePF orElse commonMessageReceive

  private def runningReceivePF: Receive = {
    case t @ Transition(_, from, to) =>
      log.debug(s"$name: supervisorReceive Transition: $from/$to")
    case Uninitialize =>
      log.debug(s"$name: Handle Uninitialize message from container")
      lifecycleManager ! Uninitialize
      context become notRunningReceive
    case Shutdown =>
      log.debug(s"$name: Handle Shutdown message from container")
      lifecycleManager ! Shutdown
      context become notRunningReceive
    case HaltComponent =>
      log.debug(s"$name: Supervisor received 'HaltComponent' in Running state.")
      lifecycleManager ! Uninitialize
      context become haltingReceive
    case RestartComponent =>
    // TODO -- Implement supervisor-based restart

    // Forward configs to the component
    case msg: AssemblyControllerMessage if componentInfo.componentType == Assembly => component.tell(msg, sender())
    case msg: HcdControllerMessage if componentInfo.componentType == HCD => component.tell(msg, sender())
  }

  def runningReceive = runningReceivePF orElse commonMessageReceive

  def haltingReceivePF: Receive = {
    case Transition(_, Initialized, PendingLoadedFromInitialized) =>
      unregisterFromLocationService()
    case Transition(_, Initialized, Initialized) =>
      // This transition indicates the component is now firmly in Initialized from Loaded or Running
      notifyListeners(LifecycleStateChanged(Initialized))
    case Transition(_, Loaded, Loaded) =>
      // This transition indicates the component is now firmly in Loaded from Loaded or Running
      notifyListeners(LifecycleStateChanged(Loaded))
      haltComponent()
    case Transition(_, PendingLoadedFromInitialized, Loaded) =>
      log.debug(s"$name: shutting down")
    case t @ Transition(_, from, to) =>
      log.debug(s"$name: haltingReceive Transition: $from/$to")
  }

  def haltingReceive = haltingReceivePF orElse commonMessageReceive

  def receive = notRunningReceive

  // If the component is configured to register with the location service, do it,
  // and save the result for unregistering later.
  private def registerWithLocationService(): Unit = {
    import context.dispatcher
    if (componentInfo.locationServiceUsage != DoNotRegister) {
      LocationService.registerAkkaConnection(componentId, self, componentInfo.prefix)(context.system).onComplete {
        case Success(reg) =>
          registrationOpt = Some(reg)
          log.debug(s"$name: Registered $componentId with the location service")
        case Failure(ex) =>
          // XXX allan: What to do in case of error?
          log.error(s"$name: Failed to register $componentId with the location service")
      }
    }
  }

  // If the component is registered with the location service, unregister it
  private def unregisterFromLocationService(): Unit = {
    registrationOpt.foreach {
      log.debug(s"Unregistering $componentId from the location service")
      _.unregister()
    }
  }

  // The default supervision behavior will normally restart the component automatically.
  // (XXX allan: check this: needs to be properly configured)
  // The Terminated message should only be received if we manually stop the component, or a
  // system error occurs (Exceptions don't cause termination).
  private def terminated(actorRef: ActorRef): Unit = {
    log.debug(s"$name: $actorRef has terminated")
    unregisterFromLocationService()
  }

  private def haltComponent(): Unit = {
    log.debug(s"Halting component: ${componentInfo.componentName}")
    //    context.stop(self)
    context.system.terminate
  }

  // Starts the component actor
  private def startComponent(context: ActorContext, componentInfo: ComponentInfo): ActorRef = {
    log.debug(s"Starting ${componentInfo.componentName}")
    val actorRef = Component.create(context, componentInfo)
    context.watch(actorRef)
    actorRef
  }

  // The following is listener support for the container
  private var listeners = Set[ActorRef]()

  private def addListener(l: ActorRef) = listeners = listeners + l

  private def removeListener(l: ActorRef) = listeners = listeners - l

  /**
   * Sends the supplied message to all current listeners using the provided sender() as sender.
   *
   * @param msg a message to send to all listeners
   */
  private def notifyListeners(msg: Any): Unit = {
    listeners.foreach(_ ! msg)
  }
}