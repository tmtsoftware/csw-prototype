package csw.services.pkg

import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.actor._
import csw.services.pkg.Component.ComponentInfo
import csw.services.pkg.LifecycleManager._

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
object Supervisor {

  def apply(componentInfo: ComponentInfo): ActorRef = {
    val system = ActorSystem(s"${componentInfo.componentName}-system")
    system.actorOf(props(componentInfo), s"${componentInfo.componentName}-supervisor")
  }

  /**
   * Used to create the Supervisor actor
   *
   * @param componentInfo used to create the component
   * @return an object to be used to create the Supervisor actor
   */
  private def props(componentInfo: ComponentInfo): Props = Props(classOf[Supervisor], componentInfo)

  /**
   * Message used to subscribe the sender to changes in lifecycle states
   *
   * @param actorRef the actor subscribing to callbacks
   */
  case class SubscribeLifecycleCallback(actorRef: ActorRef)

  /**
   * Message to unsubscribe  from lifecycle state changes
   */
  final case class UnsubscribeLifecycleCallback(actorRef: ActorRef)

  /**
   * Message sent to subscribers of lifecycle states
   *
   * @param state the current state
   */
  case class LifecycleStateChanged(state: LifecycleState)

  /**
   * Message sent to supervisor from component or elsewhere to restart lifecycle for component
   */
  case object RestartComponent

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
   * When this message is received, the component goes through shutdown, uninitialize, and then exits
   */
  case object HaltComponent

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
final class Supervisor(val componentInfo: ComponentInfo)
    extends Actor with ActorLogging {

  import Supervisor._

  log.info("Starting Supervisor:" + context.self.path)

  val name = componentInfo.componentName
  val component = startComponent(context, componentInfo)

  val lifecycleManager = context.actorOf(LifecycleManager.props(component), "lifecycleManager")
  lifecycleManager ! SubscribeTransitionCallBack(self)

  private def commonMessageReceive: Receive = {
    case SubscribeLifecycleCallback(actorRef) ⇒
      addListener(actorRef)
    case UnsubscribeLifecycleCallback(actorRef) ⇒
      removeListener(actorRef)
    case Terminated(actorRef) ⇒
      terminated(actorRef)
    case x ⇒
      log.warning(s"Supervisor received an unexpected message: $x")
  }

  private def notRunningReceivePF: Receive = {
    case CurrentState(_, Loaded) ⇒
      // This message is sent as a side effect of subscribing to the Lifecycle FSM
      log.debug("LifecycleManager indicates Loaded")
      notifyListeners(LifecycleStateChanged(Loaded))
    case Initialize ⇒
      log.info("Handle Initialize message from container or elsewhere")
      lifecycleManager ! Initialize
    case Startup ⇒
      log.info("Handle Startup message from container or elsewhere")
      lifecycleManager ! Startup
    case Transition(_, PendingInitializedFromLoaded, Initialized) ⇒
      // LifecycleManager has sent Initialized to component and is waiting for response
      log.info(">>>>>>>RegisterWithLocationService")
    case Transition(_, Initialized, PendingLoadedFromInitialized) ⇒
      log.info(">>>>>>>>>UnregisterWithLocationService")
    case Transition(_, Loaded, Loaded) ⇒
      // This transition indicates the component is now firmly in Loaded (only during shutdown/restart
      notifyListeners(LifecycleStateChanged(Loaded))
    case Transition(_, Initialized, Initialized) ⇒
      // This transition indicates the component is now firmly in Initialized from Loaded or Running
      notifyListeners(LifecycleStateChanged(Initialized))
    case Transition(_, Running, Running) ⇒
      // This transition indicates the component is now firmly in Running
      log.info(">>>>>>> Transition to Running <<<<<<<<<<<<")
      notifyListeners(LifecycleStateChanged(Running))
      context become runningReceive
    case t @ Transition(_, from, to) ⇒
      log.debug(s"notRunningReceivePF: unhandled transition: $from/$to")
  }

  def notRunningReceive = notRunningReceivePF orElse commonMessageReceive

  private def runningReceivePF: Receive = {
    case t @ Transition(_, from, to) ⇒
      log.info(s"supervisorReceive Transition: $from/$to")
    case Uninitialize ⇒
      log.info("Handle Uninitialize message from container")
      lifecycleManager ! Uninitialize
      context become notRunningReceive
    case Shutdown ⇒
      log.info("Handle Shutdown message from container")
      lifecycleManager ! Shutdown
      context become notRunningReceive
    case HaltComponent ⇒
      log.info("Supervisor received \"HaltComponent\" in Running state.")
      lifecycleManager ! Uninitialize
      context become haltingReceive
    case RestartComponent ⇒
    // TODO -- Implement supervisor-based restart
  }

  def runningReceive = runningReceivePF orElse commonMessageReceive

  def haltingReceivePF: Receive = {
    case Transition(_, Initialized, PendingLoadedFromInitialized) ⇒
      log.info(">>>>>>>>>UnregisterWithLocationService")
    case Transition(_, Initialized, Initialized) ⇒
      // This transition indicates the component is now firmly in Initialized from Loaded or Running
      notifyListeners(LifecycleStateChanged(Initialized))
    case Transition(_, Loaded, Loaded) ⇒
      // This transition indicates the component is now firmly in Loaded from Loaded or Running
      notifyListeners(LifecycleStateChanged(Loaded))
      haltComponent()
    case Transition(_, PendingLoadedFromInitialized, Loaded) ⇒
      log.info(">>>>>>>>Shut this bitch down!")
    case t @ Transition(_, from, to) ⇒
      log.debug(s"haltingReceive Transition: $from/$to")
  }

  def haltingReceive = haltingReceivePF orElse commonMessageReceive

  def receive = notRunningReceive

  // The default supervision behavior will normally restart the component automatically.
  // The Terminated message should only be received if we manually stop the component, or a
  // system error occurs (Exceptions don't cause termination).
  private def terminated(actorRef: ActorRef): Unit = {
    log.info(s"$name: $actorRef has terminated")
  }

  private def haltComponent(): Unit = {
    log.info(s"Halting component: ${componentInfo.componentName}")
    context.stop(self)
    context.system.terminate // XXX allan: Why ignoring return value future?
    context.system.whenTerminated // XXX allan: Why call this unless you want to wait for the future it returns?
  }

  // Starts the component actor
  private def startComponent(context: ActorContext, componentInfo: ComponentInfo): ActorRef = {
    log.info(s"Starting ${componentInfo.componentName}")
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