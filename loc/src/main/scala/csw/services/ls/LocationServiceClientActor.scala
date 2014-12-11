package csw.services.ls

import akka.actor._
import csw.services.ls.LocationServiceActor.{ServiceId, LocationServiceInfo, ServicesReady}

/**
 * An actor that does the job of requesting information about a list of services
 * from the location service and then watching them in case one of them terminates.
 * Once all the services are available, a Connected message is sent to the parent actor.
 * If any of the services terminate, a disconnected message is sent to the parent.
 */
object LocationServiceClientActor {

  sealed trait LocationServiceClientMessage
  case class Connected(msg: ServicesReady)
  case object Disconnected

  def props(serviceIds: List[ServiceId]): Props = Props(classOf[LocationServiceClientActor], serviceIds)
}

class LocationServiceClientActor(serviceIds: List[ServiceId]) extends Actor with ActorLogging {
  import LocationServiceClientActor._

  // Request the services from the location service
  requestServices()

  // Start out in the waiting state
  override def receive: Receive = waitingForServices

  // Initial state until we get a list of running services
  def waitingForServices: Receive = {
    case ServicesReady(services) ⇒
      log.debug(s"All requested services are ready: $services")
      for (actorRef ← services.map(_.actorRefOpt).flatten) context.watch(actorRef)
      context.become(ready(services))
      context.parent ! Connected(_)

    case Terminated(actorRef) ⇒

    case x ⇒ log.error(s"Unexpected message from ${sender()} while waiting for services: $x")
  }

  // Messages received in the ready state.
  def ready(services: List[LocationServiceInfo]): Receive = {

    // If a target actor died, go back and wait for it (and any others that are needed) to restart
    case Terminated(actorRef) ⇒
      log.debug(s"Received terminated message for required service $actorRef: Waiting for it to come back.")
      requestServices()
      context.parent ! Disconnected
      context.become(waitingForServices)

    case x ⇒ log.error(s"Unexpected message from ${sender()}: $x")
  }

  // Request the services from the location service, which should eventually result in a
  // ServicesReady message being sent as a reply.
  private def requestServices(): Unit = {
    LocationService.requestServices(context.system, self, serviceIds)
  }
}
