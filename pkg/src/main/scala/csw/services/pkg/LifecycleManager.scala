package csw.services.pkg

import akka.actor._
import csw.services.ls.LocationService.RegInfo
import csw.services.ls.LocationServiceActor.ServiceId
import csw.services.ls.LocationServiceClientActor.{ Disconnected, Connected }
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
  sealed trait LifecycleError

  case class InitializeFailed(name: String, reason: Throwable) extends LifecycleError

  case class StartupFailed(name: String, reason: Throwable) extends LifecycleError

  case class ShutdownFailed(name: String, reason: Throwable) extends LifecycleError

  case class UninitializeFailed(name: String, reason: Throwable) extends LifecycleError

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

  import LifecycleManager._

  val name = regInfo.serviceId.name
  val component = startComponent()

  // Not used
  override def receive: Receive = {
    case msg ⇒
  }

  // --- Receive states (See OSW TN012 - COMPONENT LIFECYCLE DESIGN) ---

  // Behavior in the Loaded state
  def loaded(connected: Boolean, targetState: LifecycleState): Receive = {
    case Initialize ⇒
      component ! Initialize
      registerWithLocationService()
      context.become(loaded(connected, Initialized(name)))

    case Startup ⇒
      component ! Initialize
      registerWithLocationService()
      context.become(loaded(connected, Running(name)))

    case Shutdown ⇒
      component ! Initialize
      context.become(loaded(connected, Initialized(name)))

    case Uninitialize ⇒
      context.become(loaded(connected, Loaded(name)))

    // Message from component indicating current state
    case s @ Loaded(_) ⇒
      updateState(s, targetState, loaded(connected, targetState))

    case s @ Initialized(_) ⇒
      updateState(s, targetState, initialized(connected, targetState))

    case s @ Running(_) ⇒
      updateState(s, targetState, running(connected, targetState))

    case Terminated(actorRef) ⇒ terminated(actorRef)

    case Connected(servicesReady) ⇒
      component ! servicesReady
      context.become(loaded(connected = true, targetState))

    case Disconnected ⇒
      context.become(loaded(connected = false, targetState))

    case msg ⇒
      unexpectedMessage(msg, "loaded", connected)
  }

  // Behavior in the Initialized state
  def initialized(connected: Boolean, targetState: LifecycleState): Receive = {
    case Initialize ⇒
      context.become(initialized(connected, Initialized(name)))

    case Startup ⇒
      component ! _
      requestServices()
      context.become(initialized(connected, Running(name)))

    case Shutdown ⇒
      context.become(initialized(connected, Initialized(name)))

    case Uninitialize ⇒
      component ! _
      context.become(initialized(connected, Loaded(name)))

    // Message from component indicating current state
    case s @ Loaded(_) ⇒
      updateState(s, targetState, loaded(connected, targetState))

    case s @ Initialized(_) ⇒
      updateState(s, targetState, initialized(connected, targetState))

    case s @ Running(_) ⇒
      updateState(s, targetState, running(connected, targetState))

    case Terminated(actorRef) ⇒
      terminated(actorRef)

    case Connected(servicesReady) ⇒
      component ! servicesReady
      context.become(initialized(connected = true, targetState))

    case Disconnected ⇒
      context.become(initialized(connected = false, targetState))

    case msg ⇒
      unexpectedMessage(msg, "initialized", connected)
  }

  // Behavior in the Running state
  def running(connected: Boolean, targetState: LifecycleState): Receive = {
    case Initialize ⇒
      component ! Shutdown
      context.become(running(connected, Initialized(name)))

    case Startup ⇒
      context.become(running(connected, Running(name)))

    case Shutdown ⇒
      component ! _
      context.become(running(connected, Initialized(name)))

    case Uninitialize ⇒
      component ! Shutdown
      context.become(running(connected, Loaded(name)))

    // Message from component indicating current state
    case s @ Loaded(_) ⇒
      updateState(s, targetState, loaded(connected, targetState))

    case s @ Initialized(_) ⇒
      updateState(s, targetState, initialized(connected, targetState))

    case s @ Running(_) ⇒
      updateState(s, targetState, running(connected, targetState))

    case Terminated(actorRef) ⇒
      terminated(actorRef)

    case Connected(servicesReady) ⇒
      component ! servicesReady
      context.become(running(connected = true, targetState))

    case Disconnected ⇒
      context.become(running(connected = false, targetState))

    case msg if connected ⇒
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
    context.actorOf(LocationServiceClientActor.props(services))
  }

  // Called when a lifecycle state message is received from the component
  private def updateState(currentState: LifecycleState, targetState: LifecycleState, nextState: Receive): Unit = {
    if (currentState != targetState) component ! targetState
    context become nextState
  }
}
