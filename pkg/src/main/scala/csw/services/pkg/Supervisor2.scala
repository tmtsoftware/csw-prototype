package csw.services.pkg

import akka.actor._
import csw.services.ccs.PeriodicHcdController2.Process
import csw.services.loc.LocationService
import csw.services.pkg.Component2.{ComponentInfo, RegisterAndTrackServices, RegisterOnly}


import csw.services.pkg.Supervisor2.EndProcessing

import scala.concurrent.duration.FiniteDuration

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
object Supervisor2 {
  /**
    * Used to create the Supervisor actor
    * @param componentInfo used to create the component
    * @return an object to be used to create the Supervisor actor
    */
  def props(componentProps: Props, componentInfo: ComponentInfo): Props = Props(classOf[Supervisor2], componentProps, componentInfo)

  /**
    * When this message is received, the component starts the processing loop
    */
  case class StartProcessing(period: FiniteDuration)

  /**
    * When this message is received, the component does any operations needed to shut things down
    * This includes unregistered from the location service if in use
    */
  case object EndProcessing

  def endProcessing(supervisor: ActorRef): Unit = {
    supervisor ! EndProcessing
  }

  def startProcessing(supervisor: ActorRef, rate: FiniteDuration): Unit = {
    supervisor ! Process(rate)
  }
}

/**
  * A supervisor actor that manages the component actor given by the arguments
  * (see props() for argument descriptions).
  */
case class Supervisor2(componentProps: Props, componentInfo: ComponentInfo)
  extends Actor with ActorLogging {
  import LocationManager._

  val locationManager = context.system.actorOf(LocationManager.props(componentInfo.name), s"${componentInfo.name}")

  override def preStart(): Unit = {
    componentInfo.locationServiceUsage match {
      case RegisterOnly | RegisterAndTrackServices =>
        log.info("registering component")
        locationManager ! Register(componentInfo.serviceId, componentInfo.prefix)
      case _ =>
    }
  }


  // Result of last location service registration, can be used to unregister (by calling close())
  var registration: Option[LocationService.Registration] = None

  val name = componentInfo.serviceId.toString
  //val serviceRefs = services.map(ServiceRef(_, AkkaType)).toSet
  val component = startComponent()


  def receive: Receive = {

    case EndProcessing ⇒
      locationManager ! UnregisterWithLocationService
      endComponent()

    case Terminated(actorRef) ⇒ terminated(actorRef)

    case x              ⇒ log.warning(s"Received unexpected xmessage: $x")
  }



  // The default supervision behavior will normally restart the component automatically.
  // The Terminated message should only be received if we manually stop the component, or a
  // system error occurs (Exceptions don't cause termination).
  private def terminated(actorRef: ActorRef): Unit = {
    log.info(s"$name: $actorRef has terminated")

  }

  def endComponent():Unit = {
    log.info(s"Shutting down component: ${componentInfo.name}")
    context.system.terminate()
    context.system.whenTerminated
  }

  // Starts the component actor
  private def startComponent(): ActorRef = {
    log.info(s"Starting $name")
    val actorRef = context.actorOf(componentProps, name)
    context.watch(actorRef)
    actorRef
  }



}