package csw.services.loc

import java.net.{Inet6Address, NetworkInterface, URI, InetAddress}
import javax.jmdns._
import akka.actor._
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.loc.AccessType.AkkaType
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

  // Share the JmDNS instance within this jvm for better performance
  // (Note: Using lazy initialization, since this should run after calling initInterface() below
  private lazy val registry = getRegistry

  // Used to log a warning if initInterface was not called before registering
  private var initialized = false

  /**
   * Sets the "akka.remote.netty.tcp.hostname" and net.mdns.interface system properties, if not already
   * set on the command line (with -D), so that any services or akka actors created will use and publish the correct IP address.
   * This method should be called before creating any actors or web services that depend on the location service.
   *
   * Note that calling this method overrides any setting for akka.remote.netty.tcp.hostname in the akka config file.
   * Since the application config is immutable and cached once it is loaded, I can't think of a way to take the config
   * setting into account here. This should not be a problem, since we don't want to hard code host names anyway.
   */
  def initInterface(): Unit = {
    initialized = true
    case class Addr(index: Int, addr: InetAddress)
    def defaultAddr = Addr(0, InetAddress.getLocalHost)
    def filter(a: Addr): Boolean = {
      // Don't use ipv6 addresses yet, since it seems to not be working with the current akka version
      !a.addr.isLoopbackAddress && !a.addr.isInstanceOf[Inet6Address]
    }
    // Get this host's primary IP address.
    // Note: The trick to getting the right one seems to be in sorting by network interface index
    // and then ignoring the loopback address.
    // I'm assuming that the addresses are sorted by network interface priority (which seems to be the case),
    // although this is not documented anywhere.
    def getIpAddress: String = {
      import scala.collection.JavaConversions._
      val addresses = for {
        i ← NetworkInterface.getNetworkInterfaces
        a ← i.getInetAddresses
      } yield Addr(i.getIndex, a)
      addresses.toList.sortWith(_.index < _.index).find(filter).getOrElse(defaultAddr).addr.getHostAddress
    }

    val akkaKey = "akka.remote.netty.tcp.hostname"
    val mdnsKey = "net.mdns.interface"
    //    val config = ConfigFactory.load()
    val mdnsHost = Option(System.getProperty(mdnsKey))
    mdnsHost.foreach(h ⇒ logger.info(s"Found system property for $mdnsKey: $h"))
    //    val akkaHost = if (config.hasPath(akkaKey) && config.getString(akkaKey).nonEmpty) Some(config.getString(akkaKey)) else None
    val akkaHost = Option(System.getProperty(akkaKey))
    akkaHost.foreach(h ⇒ logger.info(s"Found system property for: $akkaKey: $h"))
    val host = akkaHost.getOrElse(mdnsHost.getOrElse(getIpAddress))
    logger.info(s"Using $host as listening IP address")
    System.setProperty(akkaKey, host)
    System.setProperty(mdnsKey, host)
  }

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
   *
   * @param serviceRefs list of services to look for
   * @param replyTo     optional actorRef to reply to (default: parent of this actor)
   */
  def props(serviceRefs: Set[ServiceRef], replyTo: Option[ActorRef] = None): Props =
    Props(classOf[LocationService], serviceRefs, replyTo)

  /**
   * Returned from register calls so that client can close the connection and deregister the service
   */
  trait Registration {
    /**
     * Unregisters the previously registered service.
     * Note that all services are automatically unregistered on shutdown.
     */
    def unregister(): Unit

    /**
     * Same as unregister, for backward compatibility
     */
    def close(): Unit = unregister()
  }

  private case class RegisterResult(registry: JmDNS, info: ServiceInfo) extends Registration {
    override def unregister(): Unit = registry.unregisterService(info)
  }

  /**
   * Holds information for a resolved service
   *
   * @param serviceRef  describes the service
   * @param uri         the URI for the service
   * @param actorRefOpt set if this is an Akka/actor based service
   * @param prefix      for actor based services, indicates the part of a configuration it is interested in, otherwise empty string
   */
  case class ResolvedService(serviceRef: ServiceRef, uri: URI, prefix: String = "", actorRefOpt: Option[ActorRef] = None)

  /**
   * Message sent to the parent actor whenever all the requested services become available
   *
   * @param services maps requested services to the resolved information
   */
  case class ServicesReady(services: Map[ServiceRef, ResolvedService])

  /**
   * Message sent when one of the requested services disconnects
   *
   * @param serviceRef describes the disconnected service
   */
  case class Disconnected(serviceRef: ServiceRef)

  // Get JmDNS instance
  private def getRegistry: JmDNS = {
    if (!initialized) logger.warn("LocationService.initInterface() should be called once before using this class or starting any actors!")
    val hostname = Option(System.getProperty("akka.remote.netty.tcp.hostname"))
    val registry = if (hostname.isDefined) {
      val addr = InetAddress.getByName(hostname.get)
      JmDNS.create(addr, hostname.get)
    } else {
      JmDNS.create()
    }
    logger.info(s"Using host = ${registry.getHostName} (${registry.getInterface})")
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
   * @param port      the port the service is running on
   * @param path      the path part of the URI (default: empty)
   * @return an object that can be used to close the connection and unregister the service
   */
  def registerHttpService(serviceId: ServiceId, port: Int, path: String = "")(implicit ec: ExecutionContext): Future[Registration] = {
    val serviceRef = ServiceRef(serviceId, AccessType.HttpType)
    Future {
      val values = Map(
        PATH_KEY → path
      )
      val service = ServiceInfo.create(dnsType, serviceRef.toString, port, 0, 0, values.asJava)
      registry.registerService(service)
      logger.info(s"Registered $serviceRef")
      RegisterResult(registry, service)
    }
  }

  /**
   * Registers the given service for the local host and the given port
   * (The full name of the local host will be used)
   *
   * @param serviceId describes the service
   * @param actorRef  the actor reference for the actor being registered
   * @param prefix    indicates the part of a command service config that this service is interested in
   */
  def registerAkkaService(serviceId: ServiceId, actorRef: ActorRef, prefix: String = "")(implicit system: ActorSystem): Future[Registration] = {
    import system.dispatcher
    val serviceRef = ServiceRef(serviceId, AccessType.AkkaType)
    Future {
      val uri = getActorUri(actorRef, system)
      logger.info(s"registering with akka uri: $uri")
      val values = Map(
        PATH_KEY → uri.getPath,
        SYSTEM_KEY → uri.getUserInfo,
        PREFIX_KEY → prefix
      )
      val service = ServiceInfo.create(dnsType, serviceRef.toString, uri.getPort, 0, 0, values.asJava)
      registry.registerService(service)
      logger.info(s"Registered $serviceRef at ${service.getInet4Addresses.toList}")
      RegisterResult(registry, service)
    }
  }

  // --- Used to get the full path URI of an actor from the actorRef ---
  private class RemoteAddressExtensionImpl(system: ExtendedActorSystem) extends Extension {
    def address = system.provider.getDefaultAddress
  }

  private object RemoteAddressExtension extends ExtensionKey[RemoteAddressExtensionImpl]

  // Gets the full URI for the actor
  private def getActorUri(actorRef: ActorRef, system: ActorSystem): URI =
    new URI(actorRef.path.toStringWithAddress(RemoteAddressExtension(system).address))

  /**
   * Convenience method that gets the location service information for a given set of services.
   *
   * @param serviceRefs set of requested services
   * @param system      the caller's actor system
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
 * @param replyTo     optional actorRef to reply to (default: parent of this actor)
 */
case class LocationService(serviceRefs: Set[ServiceRef], replyTo: Option[ActorRef] = None)
    extends Actor with ActorLogging with ServiceListener {

  // Set of resolved services (Needs to be a var, since the ServiceListener callbacks prevent using akka state)
  var resolved = Map.empty[ServiceRef, ResolvedService]

  // Check if location is already known
  val serviceInfo = registry.list(dnsType).toList
  for (info ← serviceInfo) resolveService(info)

  // Listen for future changes
  registry.addServiceListener(dnsType, this)

  override def serviceAdded(event: ServiceEvent): Unit = {
    log.info(s"service added: ${event.getName} ${event.getInfo}")
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

  private def getAkkaUri(uriStr: String, userInfo: String): Option[URI] = try {
    val uri = new URI(uriStr)
    Some(new URI("akka.tcp", userInfo, uri.getHost, uri.getPort, uri.getPath, uri.getQuery, uri.getFragment))
  } catch {
    case e: Exception ⇒
      // some issue with ipv6 addresses?
      log.error(s"Couldn't make URI from $uriStr and userInfo $userInfo", e)
      None
  }

  private def resolveService(info: ServiceInfo): Unit = {
    try {
      log.info(s"resolveService $info")
      val serviceRef = ServiceRef(info.getName)
      if (serviceRefs.contains(serviceRef)) {

        // Gets the URI, adding the akka system as user if needed
        def getUri(uriStr: String): Option[URI] = {
          serviceRef.accessType match {
            case AkkaType ⇒ getAkkaUri(uriStr, info.getPropertyString(SYSTEM_KEY))
            case _        ⇒ Some(new URI(uriStr))
          }
        }

        val prefix = info.getPropertyString(PREFIX_KEY)
        info.getURLs(serviceRef.accessType.name).toList.flatMap(getUri).foreach {
          uri ⇒
            log.info(s"location service: resolve URI = $uri")
            val rs = ResolvedService(serviceRef, uri, prefix)
            if (serviceRef.accessType == AkkaType) {
              identify(rs)
            } else {
              resolved += serviceRef → rs
              checkResolved()
            }
        }
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
    val actorPath = ActorPath.fromString(rs.uri.toString)
    log.info(s"Attempting to identify actor ${rs.uri}")
    context.actorSelection(actorPath) ! Identify(rs)
  }

  // Called when an actor is identified.
  // Update the resolved map and check if we have everything that was requested.
  private def actorIdentified(actorRefOpt: Option[ActorRef], rs: ResolvedService): Unit = {
    if (actorRefOpt.isDefined) {
      resolved += rs.serviceRef → rs.copy(actorRefOpt = actorRefOpt)
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

