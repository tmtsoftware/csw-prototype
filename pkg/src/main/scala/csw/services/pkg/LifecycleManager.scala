package csw.services.pkg

import akka.actor._
import csw.services.ls.LocationService.RegInfo
import csw.services.ls.LocationServiceActor.ServiceId
import csw.services.ls.LocationServiceClientActor.{Disconnected, Connected}
import csw.services.ls.{LocationServiceClientActor, LocationServiceRegisterActor}

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
  sealed trait LifecycleError

  case class InitializeFailed(name: String, reason: Throwable) extends LifecycleError

  case class StartupFailed(name: String, reason: Throwable) extends LifecycleError

  case class ShutdownFailed(name: String, reason: Throwable) extends LifecycleError

  case class UninitializeFailed(name: String, reason: Throwable) extends LifecycleError


  case class LifecycleTransitionError(name: String, currentState: LifecycleState, commandReceived: LifecycleCommand)
    extends Exception(s"$name received $commandReceived while in state $currentState")


  /**
   * Used to create the LifecycleManager actor
   * @param componentProps used to create the component
   * @param regInfo used to register the component with the location service
   * @param dependencies a list of service ids for the services the component depends on
   * @return an object to be used to create the LifecycleManager actor
   */
  def props(componentProps: Props, regInfo: RegInfo, dependencies: List[ServiceId], container: ActorRef): Props =
    Props(classOf[LifecycleManager], componentProps, regInfo, dependencies)
}

// A lifecycle manager actor that manages the component actor given by the arguments
// (see props() for argument descriptions).
case class LifecycleManager(componentProps: Props, regInfo: RegInfo, dependencies: List[ServiceId])
  extends Actor with ActorLogging {

  import LifecycleManager._

  val name = regInfo.serviceId.name
  startComponent()

  // Not used
  override def receive: Receive = {
    case msg =>
  }

  // --- Receive states ---

  // Behavior in the Loaded state
  def loaded(component: ActorRef, connected: Boolean): Receive = {
    case Initialize =>
      component ! _
      registerWithLocationService()

    case Startup =>
      sender() ! StartupFailed(name, LifecycleTransitionError(name, Loaded(name), Startup))

    case Shutdown =>
      sender() ! ShutdownFailed(name, LifecycleTransitionError(name, Loaded(name), Shutdown))

    case Uninitialize =>
      log.warning(s"$name ignoring Unitialize request since already in the Loaded state")

    // Message from component indicating current state
    case Loaded(n) =>
      updateState(n, component, _, loaded(component, connected))
    case Initialized(n) =>
      updateState(n, component, _, initialized(component, connected))
    case Running(n) =>
      log.warning(s"$name reports that it is Running while in Loaded state")
      updateState(n, component, _, running(component, connected)) // XXX shouldn't happen

    case Terminated(actorRef) => terminated(actorRef)

    case Connected(servicesReady) =>
      component ! servicesReady
      context.become(loaded(component, connected = true))

    case Disconnected =>
      context.become(loaded(component, connected = false))

    case msg => unexpectedMessage(msg, "loaded", connected)
  }


  // Behavior in the Initialized state
  def initialized(component: ActorRef, connected: Boolean): Receive = {
    case Initialize =>
      log.warning(s"$name ignoring Itialize request since already in the Itialized state")

    case Startup | Uninitialize =>
      component ! _
      requestServices()

    case Shutdown =>
      sender() ! ShutdownFailed(name, LifecycleTransitionError(name, Initialized(name), Shutdown))

    case Uninitialize =>
      sender() ! UninitializeFailed(name, LifecycleTransitionError(name, Initialized(name), Uninitialize))

    // Message from component indicating current state
    case Loaded(n) => updateState(n, component, _, loaded(component, connected))
    case Initialized(n) => updateState(n, component, _, initialized(component, connected))
    case Running(n) => updateState(n, component, _, running(component, connected))

    case Terminated(actorRef) => terminated(actorRef)

    case Connected(servicesReady) =>
      component ! servicesReady
      context.become(initialized(component, connected = true))

    case Disconnected =>
      context.become(initialized(component, connected = false))

    case msg => unexpectedMessage(msg, "initialized", connected)
  }


  // Behavior in the Running state
  def running(component: ActorRef, connected: Boolean): Receive = {
    case Initialize =>
      sender() ! InitializeFailed(name, LifecycleTransitionError(name, Running(name), Initialize))

    case Startup =>
      log.warning(s"$name ignoring Startup request since already in the Running state")

    case Shutdown =>
      component ! _

    case Uninitialize =>
      sender() ! UninitializeFailed(name, LifecycleTransitionError(name, Running(name), Uninitialize))

    // Message from component indicating current state
    case Loaded(n) =>
      log.warning(s"$name reports that it is Loaded while in Running state")
      updateState(n, component, _, loaded(component, connected)) // XXX shouldn't happen
    case Initialized(n) => updateState(n, component, _, initialized(component, connected))
    case Running(n) => updateState(n, component, _, running(component, connected))

    case Terminated(actorRef) => terminated(actorRef)

    case Connected(servicesReady) =>
      component ! servicesReady
      context.become(running(component, connected = true))

    case Disconnected =>
      context.become(running(component, connected = false))

    case msg if connected => component.tell(msg, sender())

    case msg => log.warning(s"Ignoring message $msg since $name is not connected")
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
  private def startComponent(): Unit = {
    log.info(s"Starting $name")
    val component = context.actorOf(componentProps, name)
    context.watch(component)
    context.become(loaded(component, connected = dependencies.isEmpty))
  }

  // Starts an actor to manage registering this actor with the location service
  // (as a proxy for the component)
  private def registerWithLocationService(): Unit = {
    context.actorOf(LocationServiceRegisterActor.props(regInfo.serviceId, Some(self),
      regInfo.configPath, regInfo.httpUri))
  }

  // Starts an actor to manage getting the services the component depends on.
  // Once all the dependencies are available, it sends a Connected message.
  // If any dependency terminates, a Disconnected message is sent to this actor.
  private def requestServices(): Unit = {
    context.actorOf(LocationServiceClientActor.props(dependencies))
  }

  // Called when a lifecycle state message is received from the component
  private def updateState(name: String, component: ActorRef, state: LifecycleState, actorState: Receive): Unit = {
    if (name == this.name && sender() == component) {
      context become actorState
      context.parent ! state
    } else {
      log.error(s"Received state change message for wrong component: $name, expected ${this.name}")
    }
  }
}
