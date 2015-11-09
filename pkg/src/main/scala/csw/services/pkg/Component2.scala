package csw.services.pkg

import akka.actor._
import csw.services.loc.ServiceId
import csw.services.pkg.Component2.{LocationServiceUsage, HcdInfo}

import scala.concurrent.duration.FiniteDuration

/**
* Represents a Component, such as an assembly, HCD (Hardware Control Daemon) or SC (Sequence Component).
*
* Each component has its own ActorSystem, LifecycleManager and name.
*/
object Component2 {

  sealed trait LocationServiceUsage
  case object DoNotUse extends LocationServiceUsage
  case object RegisterOnly extends LocationServiceUsage
  case object RegisterAndTrackServices extends LocationServiceUsage

  sealed trait ComponentInfo {
    val name: String = serviceId.toString
    def serviceId:ServiceId
    def prefix: String
    def locationServiceUsage: LocationServiceUsage
  }

  /**
    * Describes a component
    * @param serviceId service used to register the component with the location service
    * @param prefix the configuration prefix (part of configs that component should receive)
    * @param locationServiceUsage how the component plans to use the location service
    * @param rate the HCD's refresh rate
    */
  case class HcdInfo(serviceId: ServiceId,
                     prefix: String,
                     locationServiceUsage: LocationServiceUsage,
                     rate: FiniteDuration) extends ComponentInfo

  /**
    * Describes an Assembly
    * @param serviceId service used to register the component with the location service
    * @param prefix the configuration prefix (part of configs that component should receive)
    * @param locationServiceUsage how the component plans to use the location service
    * @param services a list of service ids for the services the component depends on
    */
  case class AssemblyInfo(serviceId: ServiceId,
                          prefix: String,
                          locationServiceUsage: LocationServiceUsage,
                          services: List[ServiceId]) extends ComponentInfo

}

/**
  * Marker trait for a component (HCD, Assembly, etc.)
  */
trait Component2 extends Actor with ActorLogging {

  def supervisor = context.parent

}

/**
  * An assembly is a component and may optionally also extend CommandServiceActor (or AssemblyCommandServiceActor)
  */
trait Assembly2 extends Component2

object Assembly2 {
  import csw.services.pkg.Component2.AssemblyInfo

  /**
    * Creates a component actor with a new ActorSystem and LifecycleManager
    * using the given props and name
    * @param componentProps used to create the actor
    * @param serviceId service used to register the component with the location service
    * @param prefix the configuration prefix (part of configs that component should receive)
    * @param services a list of service ids for the services the component depends on
    * @return an object describing the component
    */
  def create(componentProps: Props, serviceId: ServiceId, locationServiceUsage: LocationServiceUsage,
             prefix: String, services: List[ServiceId]): AssemblyInfo = {
    // Creates names for actor system and supervisor of form name-system, name-supervisor to go with name-HCD
    val name = serviceId.name
    val system = ActorSystem(s"$name-system")
    val supervisor = system.actorOf(Supervisor.props(componentProps, serviceId, prefix, services), s"$name-supervisor")
    ///supervisor ! Startup
    AssemblyInfo(serviceId, prefix, locationServiceUsage, services)
  }
}

/**
  * An HCD is a component and may optionally also extend CommandServiceActor
  */
trait Hcd2 extends Component2

object Hcd2 {
  import csw.services.pkg.Component2.HcdInfo

  /**
    * Creates a component actor with a new ActorSystem and LifecycleManager
    * using the given props and name
    * @param componentClassName full path of class used to create the actor
    * @param serviceId service used to register the component with the location service
    * @param prefix the configuration prefix (part of configs that component should receive)
    * @return a tuple with supervisor and an HcdInfo object describing the component
    */
  def create(componentClassName:String, serviceId:ServiceId, prefix: String, locationServiceUsage: LocationServiceUsage, rate: FiniteDuration): (ActorRef, HcdInfo) = {
    val name = serviceId.name
    val system = ActorSystem(s"$name-system")

    println("Service: " + serviceId)

    val hcdInfo = HcdInfo(serviceId, prefix, locationServiceUsage, rate)

    // Form props for component
    //val xx = Class.forName(componentClassName)
    val props = Props(Class.forName(componentClassName), serviceId.toString, hcdInfo)

    val supervisor = system.actorOf(Supervisor2.props(props, hcdInfo), s"$name-supervisor")
    (supervisor, hcdInfo)
  }
}