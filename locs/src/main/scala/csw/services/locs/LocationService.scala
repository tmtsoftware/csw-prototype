package csw.services.locs

import java.net.InetAddress
import javax.jmdns.{ServiceEvent, ServiceListener, ServiceInfo, JmDNS}
import akka.actor._
import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.locs.AccessType.AkkaType
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import LocationService._

/**
 * Location Service based on Multicast DNS (AppleTalk, Bonjour).
 * Note: On a mac, you can use the command line tool dns-sd to browser the registered services.
 */
object LocationService {

  private val logger = Logger(LoggerFactory.getLogger("LocationService"))

  // Multicast DNS service type
  private val dnsType = "_csw._tcp.local."

  // -- Keys used to store values in DNS records --

  // URI path part
  private val PATH_KEY = "path"

  // Akka system name
  private val SYSTEM_KEY = "system"

  // Indicates the part of a command service config that this service is interested in
  private val PREFIX_KEY = "prefix"

  /**
   * Used to create the actor
   * @param serviceRefs list of services to look for
   */
  def props(serviceRefs: Set[ServiceRef], replyTo: ActorRef): Props = Props(classOf[LocationService], serviceRefs, replyTo)

  /**
   * Returned from register calls so that client can close the connection and deregister the service
   */
  trait Registration {
    /**
     * Closes the connection and unregisters services registered with this instance
     */
    def close(): Unit
  }

  private case class RegisterResult(registry: JmDNS) extends Registration {
    override def close(): Unit = registry.close()
  }

  // Holds information for a resolved service
  case class ResolvedService(serviceRef: ServiceRef, uri: Uri, prefix: String = "")

  /**
   * Message sent to the replyTo actor whenever all the requested services become available
   * @param services maps requested services to the resolved information
   */
  case class ServicesReady(services: Map[ServiceRef, ResolvedService])

  /**
   * Message sent when one of the requested services disconnects
   * @param serviceRef describes the disconnected service
   */
  case class Disconnected(serviceRef: ServiceRef)

  // Get JmDNS instance
  private def getRegistry: JmDNS = {
    val addr = InetAddress.getLocalHost
    val hostname = InetAddress.getByName(addr.getHostName).toString
    JmDNS.create(addr, hostname)
  }

  // Note: DNS Service Discovery specifies the following service instance naming convention:
  //   <Instance>.<ServiceType>.<Protocol>.<Domain>
  // For example:
  //   JimBlog._atom_http._tcp.example.org
  // See http://www.infoq.com/articles/rest-discovery-dns.

  /**
   * Registers the given service for the local host and the given port
   * (The full name of the local host will be used)
   *
   * @param serviceId describes the service
   * @param port the port the service is running on
   * @param path the path part of the URI (default: empty)
   * @return an object that can be used to close the connection and unregister the service
   */
  def registerHttpService(serviceId: ServiceId, port: Int, path: String = "")(implicit ec: ExecutionContext): Future[Registration] = {
    val serviceRef = ServiceRef(serviceId, AccessType.HttpType)
    Future {
      val registry = getRegistry
      val values = Map(
        PATH_KEY -> path)
      val service = ServiceInfo.create(dnsType, serviceRef.toString, port, 0, 0, values.asJava)
      registry.registerService(service)
      logger.info(s"Registered $serviceRef")
      sys.addShutdownHook(registry.close())
      RegisterResult(registry)
    }
  }

  /**
   * Registers the given service for the local host and the given port
   * (The full name of the local host will be used)
   *
   * @param serviceId describes the service
   * @param actorRef the actor reference for the actor being registered
   * @param prefix indicates the part of a command service config that this service is interested in
   */
  def registerAkkaService(serviceId: ServiceId, actorRef: ActorRef, prefix: String = "")(implicit system: ActorSystem): Future[Registration] = {
    import system.dispatcher
    val serviceRef = ServiceRef(serviceId, AccessType.AkkaType)
    Future {
      val registry = getRegistry
      val uri = getActorUri(actorRef, system)
      val values = Map(
        PATH_KEY -> uri.path.toString(),
        SYSTEM_KEY -> uri.authority.userinfo,
        PREFIX_KEY -> prefix)
      val service = ServiceInfo.create(dnsType, serviceRef.toString, uri.authority.port, 0, 0, values.asJava)
      registry.registerService(service)
      logger.info(s"Registered $serviceRef at $uri")
      sys.addShutdownHook(registry.close())
      RegisterResult(registry)
    }
  }

  // Used to get the full path URI of an actor from the actorRef
  private class RemoteAddressExtensionImpl(system: ExtendedActorSystem) extends Extension {
    def address = system.provider.getDefaultAddress
  }

  private object RemoteAddressExtension extends ExtensionKey[RemoteAddressExtensionImpl]

  // Gets the full URI for the actor
  private def getActorUri(actorRef: ActorRef, system: ActorSystem): Uri =
    Uri(actorRef.path.toStringWithAddress(RemoteAddressExtension(system).address))

}

case class LocationService(serviceRefs: Set[ServiceRef], replyTo: ActorRef) extends Actor with ActorLogging with ServiceListener {

  // Set of resolved services
  var resolved = Map.empty[ServiceRef, ResolvedService]

  val registry = getRegistry


  val serviceInfo = registry.list(dnsType).toList
  for(info <- serviceInfo) resolveService(info)

  registry.addServiceListener(dnsType, this)
  sys.addShutdownHook(registry.close())

  override def postStop(): Unit = {
    log.info("Closing JmDNS")
    registry.close()
  }

  override def serviceAdded(event: ServiceEvent): Unit = {
    log.info(s"service added: ${event.getName} ${event.getInfo}")
  }

  override def serviceResolved(event: ServiceEvent): Unit = {
    log.info(s"service resolved: ${event.getName} ${event.getInfo}")
    resolveService(event.getInfo)
  }

  override def serviceRemoved(event: ServiceEvent): Unit = {
    log.info(s"service removed: ${event.getName}")
    val info = event.getInfo
    val serviceRef = ServiceRef(info.getName)
    if (resolved.contains(serviceRef)) {
      resolved -= serviceRef
      replyTo ! Disconnected(serviceRef)
    }
  }

  private def resolveService(info: ServiceInfo): Unit = {
    try {
      val serviceRef = ServiceRef(info.getName)
      if (serviceRefs.contains(serviceRef)) {
        // Gets the URI, adding the akka system as user if needed
        def getUri(urlStr: String) = serviceRef.accessType match {
          case AkkaType ⇒
            val system = info.getPropertyString(SYSTEM_KEY)
            try {
              // fails for ipv6 addresses
              val uri = Uri(urlStr)
              Some(uri.withUserInfo(system).withScheme("akka.tcp"))
            } catch {
              case e: Exception ⇒
                None
            }
          case _ ⇒
            Some(Uri(urlStr).withScheme("akka.tcp"))
        }
        val prefix = info.getPropertyString(PREFIX_KEY)
        val uriList = info.getURLs(serviceRef.accessType.name).toList.flatMap(getUri)
        resolved += serviceRef -> ResolvedService(serviceRef, uriList.head, prefix)
        if (resolved.keySet == serviceRefs) {
          log.info(s"Services ready: $resolved")
          replyTo ! ServicesReady(resolved)
        }
      }
    } catch {
      case e: Exception ⇒ log.error(e, "resolve error")
    }
  }

  override def receive: Receive = {
    //    case ResolvedService(serviceRef, uri, prefix) =>
    case x ⇒
      log.error(s"Received unexpected message $x")
  }

}
