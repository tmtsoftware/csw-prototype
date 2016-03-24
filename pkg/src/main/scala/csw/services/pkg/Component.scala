package csw.services.pkg

import akka.actor._
import csw.services.loc.ComponentId
import Supervisor._

/**
 * Represents a Component, such as an assembly, HCD (Hardware Control Daemon) or SC (Sequence Component).
 *
 * Each component has its own ActorSystem, LifecycleManager and name.
 */
object Component {

  /**
   * Describes a component
   *
   * @param props the props used to create the component actor
   * @param componentId service used to register the component with the location service
   * @param prefix the configuration prefix (part of configs that component should receive)
   * @param services a list of service ids for the services the component depends on
   * @param system the component's private actor system
   * @param supervisor the component's lifecycle manager
   */
  case class ComponentInfo(props: Props, componentId: ComponentId, prefix: String, services: List[ComponentId],
                           system: ActorSystem, supervisor: ActorRef)

  /**
   * Creates a component actor with a new ActorSystem and LifecycleManager
   * using the given props and name
   *
   * @param props used to create the actor
   * @param componentId service used to register the component with the location service
   * @param prefix the configuration prefix (part of configs that component should receive)
   * @param services a list of service ids for the services the component depends on
   * @return an object describing the component
   */
  def create(props: Props, componentId: ComponentId, prefix: String, services: List[ComponentId]): ComponentInfo = {
    val name = componentId.name
    val system = ActorSystem(s"$name-system")
    val supervisor = system.actorOf(Supervisor.props(props, componentId, prefix, services), s"$name-supervisor")
    supervisor ! Startup
    ComponentInfo(props, componentId, prefix, services, system, supervisor)
  }
}

/**
 * Marker trait for a component (HCD, Assembly, etc.)
 */
trait Component

/**
 * An assembly is a component and may optionally also extend CommandServiceActor (or AssemblyCommandServiceActor)
 */
trait Assembly extends Component {
  this: Actor with ActorLogging ⇒
}

/**
 * An HCD is a component and may optionally also extend CommandServiceActor
 */
trait Hcd extends Component {
  this: Actor with ActorLogging ⇒
}
