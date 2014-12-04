package csw.services.pkg

import akka.actor._

/**
 * Represents a Component, such as an assembly, HCD (Hardware Control Daemon) or SC (Sequence Component).
 *
 * Each component has its own ActorSystem, LifecycleManager and name.
 */
object Component {

  /**
   * Describes a component
   * @param props the props used to create the component actor
   * @param name the component name
   * @param system the component's private actor system
   * @param lifecycleManager the component's lifecycle manager
   */
  case class ComponentInfo(props: Props, name: String, system: ActorSystem, lifecycleManager: ActorRef)

  /**
   * Creates a component actor with a new ActorSystem and LifecycleManager
   * using the given props and name
   * @param props used to create the actor
   * @param name the name of the component
   * @return an object describing the component
   */
  def create(props: Props, name: String): ComponentInfo = {
    val system = ActorSystem(s"$name-system")
    val actorRef = system.actorOf(LifecycleManager.props(props, name), s"$name-lifecycle-manager")
    ComponentInfo(props, name, system, actorRef)
  }
}

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
