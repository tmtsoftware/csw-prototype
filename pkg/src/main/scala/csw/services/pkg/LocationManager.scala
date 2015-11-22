package csw.services.pkg

import akka.actor._
import csw.services.loc.AccessType.AkkaType
import csw.services.loc.LocationService.{ResolvedService, ServicesReady}
import csw.services.loc.{LocationService, ServiceId, ServiceRef}


import scala.util.{Failure, Success}

object LocationManager {

  case class Register(serviceId: ServiceId, prefix: String)

  case class RequestServices(services: List[ServiceId])

  /**
    * When this message is received, the component is unregistered from the location service
    */
  case object UnregisterWithLocationService

  /**
    * Used to create the LocationManager actor
//    * @param serviceId service used to register the component with the location service
    * @return an object to be managed by Supervisor
    */
  def props(name: String): Props = Props(classOf[LocationManager], s"$name-LocationManager")
}

/**
  * A supervisor actor that manages the component actor given by the arguments
  * (see props() for argument descriptions).
  */
case class LocationManager(name: String) extends Actor with ActorLogging {
  import LocationManager._

  // Result of last location service registration, can be used to unregister (by calling close())
  var registration: Option[LocationService.Registration] = None

  //val serviceRefs = services.map(ServiceRef(_, AkkaType)).toSet

  override def receive: Receive = {
    case Register(serviceId, prefix) => registerWithLocationService(serviceId, prefix)

    case RequestServices(services) =>
      val component:ActorRef = sender()
      requestServices(services, component)

    case UnregisterWithLocationService =>
      //registration.foreach(x => log.info(s"unreg: $x"))
      registration.foreach(_.close())

    case Terminated(actorRef) ⇒
      terminated(actorRef)

  }

  // The default supervision behavior will normally restart the component automatically.
  // The Terminated message should only be received if we manually stop the component, or a
  // system error occurs (Exceptions don't cause termination).
  private def terminated(actorRef: ActorRef): Unit = {
    log.info(s"$name: $actorRef has terminated")

  }

  // Registers this actor with the location service.
  // The value returned from registerAkkaService can be used to call a close()
  // method that unregisters the component again. This also ends the jmdns thread
  // that renews the lease on the DNS registration.
  // If this method is called multiple times, the previous registration is closed,
  // so that there are no hanging jmdns threads from previous registrations.
  private def registerWithLocationService(serviceId: ServiceId, prefix: String): Unit = {
    import context.dispatcher
    LocationService.registerAkkaService(serviceId, self, prefix)(context.system).onComplete {
      case Success(reg) ⇒
        registration.foreach(_.close())
        registration = Some(reg)
      case Failure(ex) ⇒
        log.error(s"$name: registration failed", ex)
    }
  }

  // If not already started, start an actor to manage getting the services the
  // component depends on.
  // Once all the services are available, it sends a ServicesReady message to the component.
  // If any service terminates, a Disconnected message is sent to this actor.
  private def requestServices(services: List[ServiceId], component: ActorRef): Unit = {
    val serviceRefs = services.map(ServiceRef(_, AkkaType)).toSet

      // HCDs don't need services(?) (Who needs them besides assemblies?)
      if (serviceRefs.nonEmpty) {
        // Services required: start a local location service actor to monitor them
        log.debug(s" requestServices $services")
        val actorName = s"$name-loc-client"
        if (context.child(actorName).isEmpty)
          context.actorOf(LocationService.props(serviceRefs, Some(component)), actorName)
      } else {
        // No services required: tell the component
        component ! ServicesReady(Map[ServiceRef, ResolvedService]())
      }

  }

}
