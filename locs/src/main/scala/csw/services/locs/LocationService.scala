package csw.services.locs

import java.net.URI
import javax.jmdns.{ ServiceEvent, ServiceListener, ServiceInfo, JmDNS }
import akka.actor.{ Props, ActorRef, ActorLogging, Actor }

import scala.collection.JavaConverters._
import scala.concurrent.Future
import LocationService._

/**
 * Location Service based on Multicast DNS (AppleTalk, Bonjour).
 */
object LocationService {

  // DNS service type
  val dnsType = "_csw._tcp.local."

  /**
   * Used to create the actor
   * @param serviceRefs list of services to look for
   */
  def props(serviceRefs: List[ServiceRef]): Props = Props(classOf[LocationService], serviceRefs)

  /**
   * CSW Service types
   */
  sealed trait ServiceType

  object ServiceType {

    /**
     * Returns the named service type (default: Service)
     */
    def apply(name: String): ServiceType = name.toLowerCase match {
      case "container" ⇒ Container
      case "assembly"  ⇒ Assembly
      case "hcd"       ⇒ HCD
      case "service"   ⇒ Service
      case _           ⇒ Unknown
    }

    /**
     * A container for services, assemblies and HCDs
     */
    case object Container extends ServiceType

    /**
     * A service that controls a hardware device
     */
    case object HCD extends ServiceType

    /**
     * A service that controls one or more HCDs or assemblies
     */
    case object Assembly extends ServiceType

    /**
     * A general purpose service (actor and/or web service application)
     */
    case object Service extends ServiceType

    /**
     * An unknown service type
     */
    case object Unknown extends ServiceType
  }

  /**
   * Used to identify a service
   * @param name the service name
   * @param serviceType HCD, Assembly, Service
   */
  case class ServiceId(name: String, serviceType: ServiceType) {
    override def toString = s"$name-$serviceType".toLowerCase
  }

  /**
   * Access type: Indicate if it is an http server or an akka actor.
   *
   * @param name the service type name (part of the DNS path)
   */
  sealed abstract class AccessType(val name: String) {
    override def toString = name
  }

  /**
   * Type of a REST/HTTP based service
   */
  case object HttpType extends AccessType("http")

  /**
   * Type of an Akka actor based service
   */
  case object AkkaType extends AccessType("akka")

  /**
   * Describes a service and the way it is accessed (http, akka)
   * @param serviceId holds the service name and type (assembly, hcd, etc.)
   * @param accessType indicates how the service is accessed (http, akka)
   */
  case class ServiceRef(serviceId: ServiceId, accessType: AccessType) {
    override def toString = s"$serviceId-$accessType"
  }

  /**
   * Registers the given service
   *
   * @param serviceRef describes the service
   * @param port the port the service is running on
   * @param values a map of key/value pairs with information about the service
   */
  def register(serviceRef: ServiceRef, port: Int, values: Map[String, Any] = Map.empty): Unit = {

    // Note: DNS Service Discovery specifies the following service instance naming convention:
    //   <Instance>.<ServiceType>.<Protocol>.<Domain>
    // For example:
    //   JimBlog._atom_http._tcp.example.org
    // See http://www.infoq.com/articles/rest-discovery-dns.

    // XXX use .local or domain/host name? See https://en.wikipedia.org/wiki/.local

    val jmdns = JmDNS.create()
    val service = ServiceInfo.create(dnsType, serviceRef.toString, port, 0, 0, values.asJava)
    jmdns.registerService(service)
  }
}

case class LocationService(serviceRefs: List[ServiceRef]) extends Actor with ActorLogging {

  val jmdns = JmDNS.create()
  jmdns.addServiceListener(dnsType, new ServiceListener() {
    override def serviceAdded(event: ServiceEvent): Unit = {
      println(s"XXX serviceAdded ${event.getName}")
    }

    override def serviceResolved(event: ServiceEvent): Unit = {
      val info = event.getInfo
      val domain = info.getDomain
      val app = info.getApplication
      val name = info.getName
      val key = info.getKey
      val addresses = info.getHostAddresses
      val port = info.getPort
      val typ = info.getType
      println(s"XXX serviceResolved name=$name, domain=$domain, app=$app, key=$key, port=$port, type=$typ, adrs=$addresses")
    }

    override def serviceRemoved(event: ServiceEvent): Unit = {
      println(s"XXX serviceRemoved ${event.getName}")
    }
  })

  override def receive: Receive = {
    case x ⇒ log.error(s"Received unexpected message $x")
  }

  //  def subscribe(): ???

}
