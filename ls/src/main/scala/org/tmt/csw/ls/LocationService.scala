package org.tmt.csw.ls

import akka.actor._
import akka.kernel.Bootable
import scala.concurrent.Future
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import java.net.URI

/**
 * Location service
 */
object LocationService {
  import LocationServiceActor._

  /**
   * Convenience method that returns a reference to the location service actor based on the
   * settings in reference.conf.
   * @param system the caller's actor system
   */
  def getLocationService(system: ActorSystem): ActorSelection = {
    val settings = LocationServiceSettings(system)
    val host = settings.hostname
    val port = settings.port
    val path = s"akka.tcp://$locationServiceName@$host:$port/user/$locationServiceName"
    val actorPath = ActorPath.fromString(path)
    system.actorSelection(actorPath)
  }

  /**
   * Utility method to register with the location service.
   * Note that the context should be the actor context of the HCD or Assembly that is registering.
   * If the actor dies, the location service will remove the reference and the service will need to
   * register again when restarting.
   *
   * @param system the caller's actor system
   * @param actorRef optional reference to the actor for the service
   * @param serviceId name and service type to register with
   * @param configPath optional dot-separated config path be sent to the actor (default is entire config)
   * @param httpUri optional HTTP URI for the actor registering
   */
  def register(system: ActorSystem, actorRef: ActorRef, serviceId: ServiceId,
               configPath: Option[String] = None, httpUri: Option[URI] = None): Unit = {
    getLocationService(system).tell(Register(serviceId, configPath, httpUri), actorRef)
  }

  /**
   * Convenience method that gets the location service information from the service given the serviceId.
   * @param system the caller's actor system
   * @param serviceId name and service type to match on
   * @return a future LocationServiceInfo object, with the serviceId set
   *         (and the other fields only set if the service was found)
   */
  def resolve(system: ActorSystem, serviceId: ServiceId): Future[LocationServiceInfo] = {
    implicit val timeout = Timeout(5.seconds)
    (getLocationService(system) ? Resolve(serviceId)).mapTo[LocationServiceInfo]
  }

  /**
   * Convenience method  to search for services matching the given name or service type.
   * @param system the caller's actor system
   * @param name optional service name (default: any)
   * @param serviceType optional service type (HCD or Assembly): Defaults to any
   */
  def browse(system: ActorSystem, name: Option[String], serviceType: Option[ServiceType]): Future[BrowseResults] = {
    implicit val timeout = Timeout(5.seconds)
    (getLocationService(system) ? Browse(name, serviceType)).mapTo[BrowseResults]
  }

  /**
   * Convenience method to request to be notified when a list of services is up and running.
   * The given actorRef will receive a ServicesReady message when all the requested services are registered and running.
   * @param system the caller's actor system
   * @param actorRef reference to the actor that should receive the reply
   * @param serviceIds a list of services to check for
   */
  def requestServices(system: ActorSystem, actorRef: ActorRef, serviceIds: List[ServiceId]): Unit = {
    getLocationService(system).tell(RequestServices(serviceIds), actorRef)
  }
}

/**
 * This is the entry point to the Location service and is started by
 * the Akka microkernel in standalone mode.
 */
class LocationService extends Bootable {
  import LocationServiceActor._

  val system = ActorSystem(locationServiceName)

  def startup(): Unit = {
    system.actorOf(Props[LocationServiceActor], locationServiceName)
  }

  def shutdown(): Unit = {
    system.shutdown()
  }
}

// --

// Main actor for the location service
object LocationServiceActor {

  val locationServiceName = "LocationService"

  /**
   * Service types
   */
  sealed trait ServiceType
  object ServiceType {
    case object HCD extends ServiceType
    case object Assembly extends ServiceType
  }

  /**
   * Used to identify a service
   * @param name the service name
   * @param serviceType HCD or Assembly
   */
  case class ServiceId(name: String, serviceType: ServiceType)

  /**
   * Message type
   */
  sealed trait LocationServiceMessage

  /**
   * Message used by an actor to register with the location service.
   * Note that the (implicit or explicit) sender of this message should be the HCD or Assembly that is registering.
   * If the actor dies, the location service will remove the reference and the service will need to
   * register again when restarting.
   *
   * @param serviceId the name and service type of the actor (used by other actors to search for it)
   * @param configPath an optional dot separated path expression referring to a hierarchy in a
   *                    Configuration object (Default: the entire configuration)
   * @param httpUri optional HTTP URI for the actor registering
   */
  case class Register(serviceId: ServiceId, configPath: Option[String] = None, httpUri: Option[URI] = None)
    extends LocationServiceMessage

  /**
   * Message sent to the location service to get the information for the given serviceId.
   * The reply will be a LocationServiceInfo message. If the service was not found, the
   * list of URIs will be empty and the actorRef set to None.
   * @param serviceId the name and service type of the actor (used by other actors to search for it)
   */
  case class Resolve(serviceId: ServiceId) extends LocationServiceMessage

  /**
   * Message sent in reply to Resolve(serviceId) (The reply is wrapped in an Option).
   * @param serviceId the name and service type of the actor
   * @param endpoints the HTTP and AKKA URIs for the actor registering
   * @param configPathOpt an optional dot separated path expression referring to a hierarchy in a
   *                    Configuration object (Default is an empty set, meaning any/all)
   * @param actorRefOpt reference to the actor, if known and running
   */
  case class LocationServiceInfo(serviceId: ServiceId, endpoints: List[URI], configPathOpt: Option[String],
                                 actorRefOpt: Option[ActorRef])

  object LocationServiceInfo {
    /**
     * Empty object with only serviceId defined: returned when no service was found for the serviceId
     */
    def notFound(serviceId: ServiceId): LocationServiceInfo = LocationServiceInfo(serviceId, List.empty[URI], None, None)

  }

  /**
   * Used to search for running services matching the given name or service type
   * @param name an optional service name (default: any)
   * @param serviceType optional: HCD or Assembly (default: any)
   */
  case class Browse(name: Option[String], serviceType: Option[ServiceType]) extends LocationServiceMessage

  /**
   * Message sent in reply to Browse(...).
   */
  case class BrowseResults(results: List[LocationServiceInfo])

  /**
   * Message to request information about a list of services (HCDs, assemblies).
   * The reply (a ServicesReady object) is only made when all services are available (up and running).
   * @param serviceIds a list of services to check for
   */
  case class RequestServices(serviceIds: List[ServiceId]) extends LocationServiceMessage

  /**
   * Message sent in response to RequestServices when all of the requested services are ready.
   * @param services list of objects describing the requested services.
   */
  case class ServicesReady(services: List[LocationServiceInfo])
}

//
class LocationServiceActor extends Actor with ActorLogging {
  import LocationServiceActor._

  // Information is saved here when a Register message is received
  private var registry = Map[ServiceId, LocationServiceInfo]()

  // If a Request message could not be completed, an entry is saved here and the request is
  // repeated when the next Register message is received
  private var outstandingRequests = Map[ActorRef, List[ServiceId]]()

  override def receive: Receive = {

    // An HDU or Assembly registers with the location service
    case Register(serviceId, configPath, httpUri) =>
      register(serviceId, configPath, httpUri)

    // If a registered actor terminates, remove the actorRef from the registry entry
    case Terminated(actorRef) =>
      log.info(s"Location Service received terminated message from $actorRef")
      serviceIdForActorRef(actorRef).foreach(registry -= _)

    // Replies with the LocationServiceInfo for the given serviceId.
    // If not found, an empty LocationServiceInfo object is sent with only the serviceId set.
    case Resolve(serviceId) =>
      registry.get(serviceId) match {
        case Some(info) => sender ! info
        case None => sender ! LocationServiceInfo.notFound(serviceId)
      }

    // Wildcard query returning a QueryResult object with a list of LocationServiceInfo
    case b: Browse => sender ! browseService(b)

    // Sends a ServicesReady message to the sender when all of the requested services are available.
    // The sender should watch the returned actors and repeat the request if one of them terminates.
    case RequestServices(serviceIds) => requestServices(sender, serviceIds)

    case x => log.error(s"Unexpected message: $x")
  }

  // Returns the serviceId for the given actorRef
  def serviceIdForActorRef(actorRef: ActorRef): Option[ServiceId] = {
    val list = for ((serviceId, info) <- registry
                    if info.actorRefOpt.isDefined && info.actorRefOpt.get == actorRef) yield serviceId
    list.headOption
  }

  def register(serviceId: ServiceId, configPath: Option[String], httpUri: Option[URI]): Unit = {
    val endpoints = List(Some(new URI(sender.path.toString)), httpUri).flatten
    registry += (serviceId -> LocationServiceInfo(serviceId, endpoints, configPath, Some(sender)))
    log.info(s"Registered ${serviceId.name} (${serviceId.serviceType}) with endpoints: $endpoints for config paths: $configPath")
    context.watch(sender)

    // If there are outstanding requests, check if they can now be completed
    val map = outstandingRequests
    for((requester, serviceIds) <- map) {
      requestServices(requester, serviceIds)
    }
  }

  // Find and return all services matching the query
  def browseService(q: Browse): BrowseResults = {
     val results = for(i <- registry.values if matchService(q, i.serviceId)) yield i
     BrowseResults(results.toList)
  }

  // Returns true if the given query matches the given serviceId
  def matchService(b: Browse, serviceId: ServiceId): Boolean = {
    (b.name, b.serviceType) match {
      case (None, None) => true
      case (Some(name), None) => name == serviceId.name
      case (None, Some(serviceType)) => serviceType == serviceId.serviceType
      case (Some(name), Some(serviceType)) => name == serviceId.name && serviceType == serviceId.serviceType
    }
  }

  // Replies with a ServicesReady message only once all requested services are running.
  // If not all services have registered yet, the arguments are saved and this method is
  // called again when the next new task registers.
  def requestServices(requester: ActorRef, serviceIds: List[ServiceId]): Unit = {
    val list = serviceIds.map(registry.get).flatten
    if (list.size == serviceIds.size) {
      requester ! ServicesReady(list)
      outstandingRequests -= requester
      log.info(s"Services ready: $list")
    } else {
      outstandingRequests += (requester -> serviceIds)
    }
  }
}


