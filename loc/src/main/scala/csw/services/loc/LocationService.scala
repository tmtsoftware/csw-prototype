package csw.services.loc

import java.net.InetAddress
import javax.jmdns.{ ServiceEvent, ServiceListener, ServiceInfo, JmDNS }
import akka.actor._
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.loc.AccessType.AkkaType
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
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
   * @param replyTo optional actorRef to reply to (default: parent of this actor)
   */
  def props(serviceRefs: Set[ServiceRef], replyTo: Option[ActorRef] = None): Props =
    Props(classOf[LocationService], serviceRefs, replyTo)

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

  /**
   * Holds information for a resolved service
   * @param serviceRef describes the service
   * @param uri the URI for the service
   * @param actorRefOpt set if this is an Akka/actor based service
   * @param prefix for actor based services, indicates the part of a configuration it is interested in, otherwise empty string
   */
  case class ResolvedService(serviceRef: ServiceRef, uri: Uri, prefix: String = "", actorRefOpt: Option[ActorRef] = None)

  /**
   * Message sent to the parent actor whenever all the requested services become available
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
    val registry = JmDNS.create(addr, hostname)
    sys.addShutdownHook(registry.close())
    registry
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
      RegisterResult(registry)
    }
  }

  // --- Used to get the full path URI of an actor from the actorRef ---
  private class RemoteAddressExtensionImpl(system: ExtendedActorSystem) extends Extension {
    def address = system.provider.getDefaultAddress
  }

  private object RemoteAddressExtension extends ExtensionKey[RemoteAddressExtensionImpl]

  // Gets the full URI for the actor
  private def getActorUri(actorRef: ActorRef, system: ActorSystem): Uri =
    Uri(actorRef.path.toStringWithAddress(RemoteAddressExtension(system).address))

  /**
   * Convenience method that gets the location service information for a given set of services.
   *
   * @param serviceRefs set of requested services
   * @param system the caller's actor system
   * @return a future ServicesReady object describing the services found
   */
  def resolve(serviceRefs: Set[ServiceRef])(implicit system: ActorSystem, timeout: Timeout): Future[ServicesReady] = {
    import akka.pattern.ask
    import system.dispatcher
    val actorRef = system.actorOf(LocationServiceWorker.props())
    val f = (actorRef ? LocationServiceWorker.Request(serviceRefs)).mapTo[ServicesReady]
    f.onComplete {
      case _ ⇒ system.stop(actorRef)
    }
    f
  }
}

/**
 * An actor that notifies the replyTo actor when all the requested services are available.
 * If all services are available, a ServicesReady message is sent. If any of the requested
 * services stops being available, a Disconnected messages is sent.
 *
 * @param serviceRefs set of requested services
 * @param replyTo optional actorRef to reply to (default: parent of this actor)
 */
case class LocationService(serviceRefs: Set[ServiceRef], replyTo: Option[ActorRef] = None)
    extends Actor with ActorLogging with ServiceListener {

  // Set of resolved services
  var resolved = Map.empty[ServiceRef, ResolvedService]

  val registry = getRegistry

  val serviceInfo = registry.list(dnsType).toList
  for (info ← serviceInfo) resolveService(info)

  registry.addServiceListener(dnsType, this)

  override def postStop(): Unit = {
    log.info("Closing JmDNS")
    registry.close()
  }

  override def serviceAdded(event: ServiceEvent): Unit = {
    //    log.info(s"service added: ${event.getName} ${event.getInfo}")
  }

  override def serviceResolved(event: ServiceEvent): Unit = {
    log.info(s"service resolved: ${event.getName}")
    resolveService(event.getInfo)
  }

  override def serviceRemoved(event: ServiceEvent): Unit = {
    removeService(ServiceRef(event.getInfo.getName))
  }

  // Removes the given service
  private def removeService(serviceRef: ServiceRef): Unit = {
    if (resolved.contains(serviceRef)) {
      resolved -= serviceRef
      log.info(s"Removed service $serviceRef")
      replyTo.getOrElse(context.parent) ! Disconnected(serviceRef)
    }
  }

  private def getAkkaUri(uriStr: String, userInfo: String): Option[Uri] = try {
    Some(Uri(uriStr).withUserInfo(userInfo).withScheme("akka.tcp"))
  } catch {
    case e: Exception ⇒
      // dome issue with ipv6 addresses?
      //      log.error(s"Couldn't make URI from $uriStr and userInfo $userInfo", e)
      None
  }

  private def resolveService(info: ServiceInfo): Unit = {
    try {
      log.debug(s"resolveService $info")
      val serviceRef = ServiceRef(info.getName)
      if (serviceRefs.contains(serviceRef)) {
        // Gets the URI, adding the akka system as user if needed
        def getUri(uriStr: String): Option[Uri] = {
          // XXX ignore ipv6 URLs for now
          if (uriStr.count(_ == ':') > 2) None else {
            serviceRef.accessType match {
              case AkkaType ⇒ getAkkaUri(uriStr, info.getPropertyString(SYSTEM_KEY))
              case _        ⇒ Some(Uri(uriStr))
            }
          }
        }
        val prefix = info.getPropertyString(PREFIX_KEY)
        val uriList = info.getURLs(serviceRef.accessType.name).toList.flatMap(getUri)
        val uri = uriList.head
        val rs = ResolvedService(serviceRef, uri, prefix)
        if (serviceRef.accessType == AkkaType) identify(rs)
        resolved += serviceRef -> rs
        checkResolved()
      }
    } catch {
      case e: Exception ⇒ log.error(e, "resolve error")
    }
  }

  // True if the service is an Akka service and the actorRef is not yet known
  private def isActorRefUnknown(rs: ResolvedService): Boolean = {
    rs.serviceRef.accessType == AkkaType && rs.actorRefOpt.isEmpty
  }

  // Checks if all services have been resolved and the actors identified, and if so,
  // sends a ServicesReady message to the replyTo actor.
  private def checkResolved(): Unit = {
    if (resolved.keySet == serviceRefs && !resolved.values.toList.exists(isActorRefUnknown)) {
      replyTo.getOrElse(context.parent) ! ServicesReady(resolved)
    }
  }

  // Sends an Identify message to the URI for the actor, which should result in an
  // ActorIdentity reply containing the actorRef.
  private def identify(rs: ResolvedService): Unit = {
    val actorPath = ActorPath.fromString(rs.uri.toString())
    log.info(s"Attempting to identify actor ${rs.uri}")
    context.actorSelection(actorPath) ! Identify(rs)
  }

  // Called when an actor is identified.
  // Update the resolved map and check if we have everything that was requested.
  private def actorIdentified(actorRefOpt: Option[ActorRef], rs: ResolvedService): Unit = {
    if (actorRefOpt.isDefined) {
      resolved += rs.serviceRef -> rs.copy(actorRefOpt = actorRefOpt)
      context.watch(actorRefOpt.get)
      log.info(s"Resolved actor $actorRefOpt")
      checkResolved()
    } else {
      log.warning(s"Could not identify actor for ${rs.serviceRef} ${rs.uri}")
    }
  }

  // Receive messages
  override def receive: Receive = {
    // Result of sending an Identify message to the actor's URI (actorSelection)
    case ActorIdentity(id, actorRefOpt) ⇒
      id match {
        case rs: ResolvedService ⇒ actorIdentified(actorRefOpt, rs)
        case _                   ⇒ log.warning(s"Received unexpected ActorIdentity id: $id")
      }

    case Terminated(actorRef) ⇒
      // If a requested Akka service terminates, remove it, just in case it didn't unregister with mDns...
      resolved.values.toList.find(_.actorRefOpt.contains(actorRef)).foreach(rs ⇒ removeService(rs.serviceRef))

    case x ⇒
      log.error(s"Received unexpected message $x")
  }

}

/**
 * A class that can be started from non-actor code that runs a location service actor until it
 * gets a result.
 */
protected object LocationServiceWorker {
  case class Request(serviceRefs: Set[ServiceRef])

  def props(): Props = Props(classOf[LocationServiceWorker])
}

protected class LocationServiceWorker extends Actor with ActorLogging {
  import LocationServiceWorker._

  override def receive: Receive = {
    case Request(serviceRefs) ⇒
      context.actorOf(LocationService.props(serviceRefs, Some(sender())))
  }
}

