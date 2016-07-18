package csw.services.pkg

import akka.actor._
import csw.services.loc.{Connection, ComponentType, ConnectionType}
import csw.services.loc.ComponentType._

import scala.concurrent.duration.{FiniteDuration, _}

/**
 * Represents a Component, such as an assembly, HCD (Hardware Control Daemon) or SC (Sequence Component).
 */
object Component {

  /**
   * Describes how a component uses the location service
   */
  sealed trait LocationServiceUsage

  case object DoNotRegister extends LocationServiceUsage

  case object RegisterOnly extends LocationServiceUsage

  case object RegisterAndTrackServices extends LocationServiceUsage

  /**
   * The information needed to create a component
   */
  sealed trait ComponentInfo {
    /**
     * A unique name for the component
     */
    val componentName: String

    /**
     * The component type (HCD, Assembly, etc.)
     */
    val componentType: ComponentType

    /**
     * The name of the class that implements the component (used to create the class via reflection)
     */
    val componentClassName: String

    /**
     * Indicates if the component needs to be registered with the location service or lookup other services
     */
    val locationServiceUsage: LocationServiceUsage

    /**
     * An optional, dot separated prefix (for example tcs.ao.mycomp) that applies to this component
     */
    val prefix: String
  }

  /**
   * Describes an HCD component
   *
   * @param componentName        name used to register the component with the location service
   * @param prefix               the configuration prefix (part of configs that component should receive)
   * @param componentClassName   The name of the class that implements the component (used to create the class via reflection)
   * @param locationServiceUsage how the component plans to use the location service
   * @param registerAs           register as an akka or http component or both
   * @param rate                 the HCD's refresh rate
   */
  final case class HcdInfo(
      componentName:        String,
      prefix:               String,
      componentClassName:   String,
      locationServiceUsage: LocationServiceUsage,
      registerAs:           Set[ConnectionType],
      rate:                 FiniteDuration
  ) extends ComponentInfo {
    val componentType = HCD
  }

  /**
   * Describes an Assembly component
   *
   * @param componentName        name used to register the component with the location service
   * @param prefix               the configuration prefix (part of configs that component should receive)
   * @param componentClassName   The name of the class that implements the component (used to create the class via reflection)
   * @param locationServiceUsage how the component plans to use the location service
   * @param registerAs           register as an akka or http component or both
   * @param connections          a list of connections that includes componentIds and connection Types
   */
  final case class AssemblyInfo(
      componentName:        String,
      prefix:               String,
      componentClassName:   String,
      locationServiceUsage: LocationServiceUsage,
      registerAs:           Set[ConnectionType],
      connections:          Set[Connection]
  ) extends ComponentInfo {
    val componentType = Assembly
  }

  /**
   * Describes a container component.
   *
   * @param componentName        name used to register the component with the location service
   * @param locationServiceUsage how the component plans to use the location service
   * @param registerAs           register as an akka or http component or both
   * @param componentInfos       information about the components contained in the container
   * @param initialDelay         only for testing
   * @param creationDelay        only for testing
   * @param lifecycleDelay       only for testing
   */
  final case class ContainerInfo(
      componentName:        String,
      locationServiceUsage: LocationServiceUsage,
      registerAs:           Set[ConnectionType],
      componentInfos:       Set[ComponentInfo],
      initialDelay:         FiniteDuration       = 0.seconds,
      creationDelay:        FiniteDuration       = 0.seconds,
      lifecycleDelay:       FiniteDuration       = 0.seconds
  ) extends ComponentInfo {
    val componentType = Container
    val componentClassName = "csw.services.pkg.ContainerComponent"
    val prefix = ""
  }

  private def createHCD(context: ActorContext, cinfo: ComponentInfo): ActorRef = {
    // Form props for component
    val props = Props(Class.forName(cinfo.componentClassName), cinfo)

    context.actorOf(props, s"${cinfo.componentName}-${cinfo.componentType}")
  }

  private def createAssembly(context: ActorContext, cinfo: AssemblyInfo): ActorRef = {
    val props = Props(Class.forName(cinfo.componentClassName), cinfo)

    context.actorOf(props, s"${cinfo.componentName}-${cinfo.componentType}")
  }

  def create(context: ActorContext, componentInfo: ComponentInfo): ActorRef = componentInfo match {
    case hcd: HcdInfo =>
      createHCD(context, hcd)
    case ass: AssemblyInfo =>
      createAssembly(context, ass)
    case cont: ContainerInfo =>
      ContainerComponent.create(cont)
  }

}

trait Component extends Actor with ActorLogging {
  def supervisor = context.parent

  override def postStop: Unit = {
    log.info(s"Post Stop: !!")
  }
}

trait Assembly extends Component

trait Hcd extends Component

trait Container extends Component
