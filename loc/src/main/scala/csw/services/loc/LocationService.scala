package csw.services.loc

import java.net.{Inet6Address, InetAddress, NetworkInterface, URI}
import javax.jmdns._

import akka.actor._
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.loc.Connection.{AkkaConnection, HttpConnection}
import csw.services.loc.LocationTrackerWorker.LocationsReady
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}

import collection.JavaConverters._

/**
 * Location Service based on Multicast DNS (AppleTalk, Bonjour).
 *
 * The Location Service is based on Multicast DNS (AppleTalk, Bonjour) and can be used to register and lookup
 * akka and http based services in the local network.
 *
 * Every application using the location service should call the initInterface() method once at startup,
 * before creating any actors.
 *
 * Note: On a mac, you can use the command line tool dns-sd to browse the registered services.
 * On Linux use avahi-browse.
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
    if (!initialized) {
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
  }

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

  /**
   * Represents a registered connection to a service
   */
  sealed trait Registration {
    def connection: Connection
  }

  /**
   * Represents a registered connection to an Akka service
   */
  final case class AkkaRegistration(connection: AkkaConnection, component: ActorRef, prefix: String = "") extends Registration

  /**
   * Represents a registered connection to a HTTP based service
   */
  final case class HttpRegistration(connection: HttpConnection, port: Int, path: String) extends Registration

  // Multicast DNS service type
  private val dnsType = "_csw._tcp.local."

  // -- Keys used to store values in DNS records --

  // URI path part
  private val PATH_KEY = "path"

  // Akka system name
  private val SYSTEM_KEY = "system"

  // Indicates the part of a command service config that this service is interested in
  private val PREFIX_KEY = "prefix"

  case class ComponentRegistered(connection: Connection, result: RegistrationResult)

  case class TrackConnection(connection: Connection)

  case class UntrackConnection(connection: Connection)

  sealed trait Location {
    def connection: Connection

    val isResolved: Boolean = false
    val isTracked: Boolean = true
  }

  final case class UnTrackedLocation(connection: Connection) extends Location {
    override val isTracked = false
  }

  final case class Unresolved(connection: Connection) extends Location

  final case class ResolvedAkkaLocation(connection: AkkaConnection, uri: URI, prefix: String = "", actorRef: Option[ActorRef] = None) extends Location {
    override val isResolved = true
  }

  final case class ResolvedHttpLocation(connection: HttpConnection, uri: URI, path: String) extends Location {
    override val isResolved = true
  }

  /**
   * Returned from register calls so that client can close the connection and deregister the service
   */
  trait RegistrationResult {
    /**
     * Unregisters the previously registered service.
     * Note that all services are automatically unregistered on shutdown.
     */
    def unregister(): Unit

    /**
     * Identifies the registered component
     */
    val componentId: ComponentId
  }

  private case class RegisterResult(registry: JmDNS, info: ServiceInfo, componentId: ComponentId) extends RegistrationResult {
    override def unregister(): Unit = registry.unregisterService(info)
  }

  /**
   * Registers a component connection with the location sevice.
   * The component will automatically be unregistered when the vm exists or when
   * unregister() is called on the result of this method.
   *
   * @param reg    component registration information
   * @param system akka system
   * @return a future result that completes when the registration has completed and can be used to unregister later
   */
  def register(reg: Registration)(implicit system: ActorSystem): Future[RegistrationResult] = {
    reg match {
      case AkkaRegistration(connection, component, prefix) ⇒
        registerAkkaConnection(connection.componentId, component, prefix)

      case HttpRegistration(connection, port, path) ⇒
        registerHttpConnection(connection.componentId, port, path)
    }
  }

  /**
   * Registers the given service for the local host and the given port
   * (The full name of the local host will be used)
   *
   * @param componentId describes the component or service
   * @param actorRef    the actor reference for the actor being registered
   * @param prefix      indicates the part of a command service config that this service is interested in
   */

  def registerAkkaConnection(componentId: ComponentId, actorRef: ActorRef, prefix: String = "")(implicit system: ActorSystem): Future[RegistrationResult] = {
    import system.dispatcher
    val connection = AkkaConnection(componentId)
    Future {
      val uri = getActorUri(actorRef, system)
      val values = Map(
        PATH_KEY → uri.getPath,
        SYSTEM_KEY → uri.getUserInfo,
        PREFIX_KEY → prefix
      )
      val service = ServiceInfo.create(dnsType, connection.toString, uri.getPort, 0, 0, values.asJava)
      registry.registerService(service)
      logger.info(s"Registered Akka $connection at $uri with $values")
      RegisterResult(registry, service, componentId)
    }
  }

  /**
   * Registers the given service for the local host and the given port
   * (The full name of the local host will be used)
   *
   * @param componentId describes the component or service
   * @param port        the port the service is running on
   * @param path        the path part of the URI (default: empty)
   * @return an object that can be used to close the connection and unregister the service
   */
  def registerHttpConnection(componentId: ComponentId, port: Int, path: String = "")(implicit system: ActorSystem): Future[RegistrationResult] = {
    import system.dispatcher
    val connection = HttpConnection(componentId)
    Future {
      val values = Map(
        PATH_KEY → path
      )
      val service = ServiceInfo.create(dnsType, connection.toString, port, 0, 0, values.asJava)
      registry.registerService(service)
      logger.info(s"Registered HTTP $connection")
      RegisterResult(registry, service, componentId)
    }
  }

  /**
   * Unregisters the connection from the location service
   * (Note: it can take some time before the service is removed from the list: see
   * comments in registry.unregisterService())
   */
  def unregisterConnection(connection: Connection): Unit = {
    import scala.collection.JavaConverters._
    logger.info(s"Unregistered connection: $connection")
    val si = ServiceInfo.create(dnsType, connection.toString, 0, 0, 0, Map.empty[String, String].asJava)
    registry.unregisterService(si)
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
   * @param connections set of requested connections
   * @param system      the caller's actor system
   * @return a future object describing the services found
   */
  def resolve(connections: Set[Connection])(implicit system: ActorRefFactory, timeout: Timeout): Future[LocationsReady] = {
    import akka.pattern.ask
    val actorRef = system.actorOf(LocationTrackerWorker.props(None))
    (actorRef ? LocationTrackerWorker.TrackConnections(connections)).mapTo[LocationsReady]
  }

  object RegistrationTracker {
    /**
     * Used to create the RegistrationTracker actor
     *
     * @param registration Set of registrations to be registered with Location Service
     * @param replyTo      optional actorRef to reply to (default: parent of this actor)
     */
    def props(registration: Set[Registration], replyTo: Option[ActorRef] = None): Props = Props(classOf[RegistrationTracker], registration, replyTo)
  }

  /**
   * An actor that tracks registration of one or more connections and replies with a
   * ComponentRegistered message when done.
   *
   * @param registration Set of registrations to be registered with Location Service
   * @param replyTo      optional actorRef to reply to (default: parent of this actor)
   */
  case class RegistrationTracker(registration: Set[Registration], replyTo: Option[ActorRef]) extends Actor with ActorLogging {

    import context.dispatcher

    implicit val system = context.system

    val a = self
    Future.sequence(registration.toList.map(LocationService.register)).onComplete {
      case Success(list) ⇒ registration.foreach { r ⇒
        val c = r.connection
        log.info(s"Successful register of connection: $c")
        list.find(_.componentId == c.componentId).foreach { result ⇒
          replyTo.getOrElse(context.parent) ! ComponentRegistered(c, result)
        }
        system.stop(a)
      }
      case Failure(ex) ⇒
        val failed = registration.map(_.connection)
        log.error(s"Registration failed for $failed", ex)
        // XXX allan: Shoud an error message be sent to replyTo?
        system.stop(a)
    }

    def receive: Receive = {
      case x ⇒ log.error(s"Received unexpected message: $x")
    }
  }

  object LocationTracker {
    /**
     * Used to create the LocationTracker actor
     *
     * @param replyTo optional actorRef to reply to (default: parent of this actor)
     */
    def props(replyTo: Option[ActorRef] = None): Props = Props(classOf[LocationTracker], replyTo)
  }

  /**
   * An actor that notifies the replyTo actor when all the requested services are available.
   * If all services are available, a ServicesReady message is sent. If any of the requested
   * services stops being available, a Disconnected messages is sent.
   *
   * @param replyTo optional actorRef to reply to (default: parent of this actor)
   */
  case class LocationTracker(replyTo: Option[ActorRef]) extends Actor with ActorLogging with ServiceListener {

    // Set of resolved services (Needs to be a var, since the ServiceListener callbacks prevent using akka state)
    // Private loc is for testing
    private[loc] var connections = Map.empty[Connection, Location]

    registry.addServiceListener(dnsType, this)

    override def postStop: Unit = {
      registry.removeServiceListener(dnsType, this)
    }

    override def serviceAdded(event: ServiceEvent): Unit = {
      log.debug(s"Listener serviceAdded: ${event.getName}")
    }

    override def serviceRemoved(event: ServiceEvent): Unit = {
      log.debug(s"Service Removed Listener: ${event.getName}")
      Connection(event.getInfo.getName).map(removeService)
    }

    // Removes the given service
    // If it isn't in our map, we don't care since it's not being tracked
    // If it is Unresolved, it's still unresolved
    // If it is resolved, we update to unresolved and send a message to the client
    private def removeService(connection: Connection): Unit = {
      def rm(loc: Location): Unit = {
        if (loc.isResolved) {
          val unc = Unresolved(loc.connection)
          connections += (loc.connection → unc)
          sendLocationUpdate(unc)
        }
      }
      connections.get(connection).foreach(rm)
    }

    // Check to see if a connection is already resolved, and if so, resolve the service
    private def tryToResolve(connection: Connection): Unit = {
      connections.get(connection) match {
        case Some(Unresolved(c)) ⇒
          val s = Option(registry.getServiceInfo(dnsType, connection.toString))
          log.info(s"Try to resolve connection: $connection: Result: $s")
          s.foreach(resolveService(connection, _))
        case x ⇒
          log.warning(s"Attempt to track and already tracked connection: $x")
      }
    }

    override def serviceResolved(event: ServiceEvent): Unit = {
      // Gets the connection from the name and, if we are tracking the connection, resolve it
      Connection(event.getName).foreach(connections.get(_).filter(_.isTracked).foreach { loc ⇒
        resolveService(loc.connection, event.getInfo)
      })
    }

    private def resolveService(connection: Connection, info: ServiceInfo): Unit = {
      try {
        // Gets the URI, adding the akka system as user if needed
        def getUri(uriStr: String): Option[URI] = {
          connection match {
            case _: AkkaConnection ⇒
              val path = info.getPropertyString(PATH_KEY)
              if (path == null) None else getAkkaUri(uriStr, info.getPropertyString(SYSTEM_KEY))
            case _ ⇒
              Some(new URI(uriStr))
          }
        }

        info.getURLs(connection.connectionType.name).toList.flatMap(getUri).foreach {
          uri ⇒
            log.info(s"Resolve service: resolve URI = $uri")
            connection match {
              case ac: AkkaConnection ⇒
                val prefix = info.getPropertyString(PREFIX_KEY)
                // An Akka connection is finished after identify returns
                val rac = ResolvedAkkaLocation(ac, uri, prefix)
                identify(rac)
              case hc: HttpConnection ⇒
                // An Http connection is finished off here
                val path = info.getPropertyString(PATH_KEY)
                val rhc = ResolvedHttpLocation(hc, uri, path)
                connections += (connection → rhc)
                log.info("Resolved HTTP: " + connections.values.toList)
                // Here is where the resolved message is sent for an Http Connection
                sendLocationUpdate(rhc)
            }
        }
      } catch {
        case e: Exception ⇒ log.error(e, "resolveService: resolve error")
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

    // Sends an Identify message to the URI for the actor, which should result in an
    // ActorIdentity reply containing the actorRef.
    private def identify(rs: ResolvedAkkaLocation): Unit = {
      log.info(s"Attempting to identify actor ${rs.uri.toString}")
      val actorPath = ActorPath.fromString(rs.uri.toString)
      context.actorSelection(actorPath) ! Identify(rs)
    }

    // Called when an actor is identified.
    // Update the resolved map and check if we have everything that was requested.
    private def actorIdentified(actorRefOpt: Option[ActorRef], rs: ResolvedAkkaLocation): Unit = {
      if (actorRefOpt.isDefined) {
        log.info(s"Resolved: Identified actor $actorRefOpt")
        // Update the table
        val newrc = rs.copy(actorRef = actorRefOpt)
        connections += (rs.connection → newrc)
        // Watch the actor for death
        context.watch(actorRefOpt.get)
        // Here is where the resolved message is sent for an Akka Connection
        log.info("Resolved: " + connections.values.toList)
        sendLocationUpdate(newrc)
      } else {
        log.warning(s"Could not identify actor for ${rs.connection} ${rs.uri}")
      }
    }

    private def sendLocationUpdate(location: Location): Unit = {
      replyTo.getOrElse(context.parent) ! location
    }

    // Receive messages
    override def receive: Receive = {

      // Result of sending an Identify message to the actor's URI (actorSelection)
      case ActorIdentity(id, actorRefOpt) ⇒
        id match {
          case rs: ResolvedAkkaLocation ⇒ actorIdentified(actorRefOpt, rs)
          case _                        ⇒ log.warning(s"Received unexpected ActorIdentity id: $id")
        }

      case TrackConnection(connection: Connection) ⇒
        // This is called from outside, so if it isn't in the tracking list, add it
        if (!connections.contains(connection)) {
          val unc = Unresolved(connection)
          connections += connection → unc
          // Should we send an update here?
          sendLocationUpdate(unc)
        }
        // Note this will be called whether we are currently tracking or not, could already be resolved
        tryToResolve(connection)

      case UntrackConnection(connection: Connection) ⇒
        // This is called from outside, so if it isn't in the tracking list, ignore it
        if (connections.contains(connection)) {
          // Remove from the map and send an updated Resolved List
          connections -= connection
          // Send Untrack back so state can be updated
          replyTo.getOrElse(context.parent) ! UnTrackedLocation(connection)
        }

      case Terminated(actorRef) ⇒
        // If a requested Akka service terminates, remove it, just in case it didn't unregister with mDns...
        connections.values.foreach {
          case ResolvedAkkaLocation(c, _, _, Some(otherActorRef)) ⇒
            log.info(s"Unresolving terminated actor: $c")
            if (actorRef == otherActorRef) removeService(c)
          case x ⇒ // should not happen
            log.warning(s"Received Terminated message from unknown location: $x")
        }

      case x ⇒
        log.error(s"Received unexpected message $x")
    }

  }

}
