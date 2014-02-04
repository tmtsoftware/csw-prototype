package org.tmt.csw.ls

import akka.actor._
import akka.kernel.Bootable
import scala.concurrent.Future
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout

/**
 * Location service
 */
object LocationService {
  import LocationServiceActor._

  val locationServiceName = "LocationService"
  implicit val timeout = Timeout(5.seconds)

  /**
   * Service types
   */
  sealed trait ServiceType
  case object HCD extends ServiceType {
    override def toString = "HCD"
  }
  case object Assembly extends ServiceType {
    override def toString = "Assembly"
  }

  /**
   * Used to identify a service
   * @param name the service name
   * @param serviceType HCD or Assembly
   */
  case class ServiceId(name: String, serviceType: ServiceType)

  /**
   * Returns a reference to the location service actor based on the settings in its reference.conf.
   */
  def getLocationService(context: ActorContext): ActorSelection = {
    val settings = LocationServiceSettings(context.system)
    val host = settings.hostname
    val port = settings.port
    val path = s"akka.tcp://$locationServiceName@$host:$port/user/$locationServiceName"
    val actorPath = ActorPath.fromString(path)
    context.system.actorSelection(actorPath)
  }

  /**
   * Utiity method to register with the location service
   * @param context actor context from caller
   * @param serviceId name and service type to register with
   * @param actorRef the actor registering
   * @param configPaths optional dot-separated config paths to be sent to the actor (default is all messages)
   */
  def register(context: ActorContext, serviceId: ServiceId, actorRef: ActorRef, configPaths: Set[String] = Set.empty[String]): Unit = {
    getLocationService(context) ! Register(serviceId, actorRef.path, configPaths)
  }

  /**
   * Convenience method that gets the location service information from the service given the serviceId.
   * @param context actor context from caller
   * @param serviceId name and service type to match on
   * @return the location service information, if found
   */
  def get(context: ActorContext, serviceId: ServiceId): Future[Option[LocationServiceInfo]] = {
    (getLocationService(context) ? Get(serviceId)).mapTo[Option[LocationServiceInfo]]
  }

  /**
   * Convenience method  to search for services matching the given name or service type.
   * @param name the service name
   * @param serviceType HCD or Assembly
   */
  def query(context: ActorContext, name: Option[String], serviceType: Option[ServiceType]): Future[QueryResult] = {
    (getLocationService(context) ? Query(name, serviceType)).mapTo[QueryResult]
  }
}

/**
 * This is the entry point to the Location service and is started by
 * the Akka microkernel in standalone mode.
 */
class LocationService extends Bootable {
  import LocationService._

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
  import LocationService._

  /**
   * Message type
   */
  sealed trait LocationServiceMessage

  /**
   * Message used by an actor to register with the location service.
   * @param serviceId the name and service type of the actor (used by other actors to search for it)
   * @param actorPath path for the actor that is registering
   * @param configPaths an optional set of dot separated path expressions, each referring to a hierarchy in a
   *                    Configuration object (Default is an empty set, meaning any/all)
   */
  case class Register(serviceId: ServiceId, actorPath: ActorPath, configPaths: Set[String] = Set.empty[String])
    extends LocationServiceMessage

  /**
   * Get the location service info for the given serviceId.
   * @param serviceId the name and service type of the actor (used by other actors to search for it)
   */
  case class Get(serviceId: ServiceId) extends LocationServiceMessage

  /**
   * Message sent in reply to Get(serviceId) (The reply is wrapped in an Option).
   * @param serviceId the name and service type of the actor
   * @param actorPath the path for the actor
   * @param configPaths a set of dot separated path expressions, each referring to a hierarchy in a
   *                    Configuration object (Default is an empty set, meaning any/all)
   */
  case class LocationServiceInfo(serviceId: ServiceId, actorPath: ActorPath, configPaths: Set[String])

  /**
   * Used to search for services matching the given name or service type
   * @param name the service name
   * @param serviceType HCD or Assembly
   */
  case class Query(name: Option[String], serviceType: Option[ServiceType]) extends LocationServiceMessage

  /**
   * Message sent in reply to Query(...).
   */
  case class QueryResult(results: List[LocationServiceInfo])
}

//
class LocationServiceActor extends Actor with ActorLogging {
  import LocationService._
  import LocationServiceActor._

  // In-memory DB (for now)
  private var registry = Map[ServiceId, LocationServiceInfo]()

  override def receive: Receive = {
    case Register(serviceId, path, configPaths) =>
      registry += (serviceId -> LocationServiceInfo(serviceId, path, configPaths))
      log.info(s"Registered ${serviceId.name} (${serviceId.serviceType}) with path: $path fpr config paths: $configPaths")

    case Get(serviceId) => sender ! registry.get(serviceId)

    case q: Query => sender ! queryService(q)

    case x => log.error(s"Unexpected message: $x")
  }

  // Find and return all services matching the query
  def queryService(q: Query): QueryResult = {
     val results = for(i <- registry.values if matchService(q, i.serviceId)) yield i
     QueryResult(results.toList)
  }

  def matchService(q: Query, serviceId: ServiceId): Boolean = {
    (q.name, q.serviceType) match {
      case (None, None) => false
      case (Some(name), None) => name == serviceId.name
      case (None, Some(serviceType)) => serviceType == serviceId.serviceType
      case (Some(name), Some(serviceType)) => name == serviceId.name && serviceType == serviceId.serviceType
    }
  }
}


