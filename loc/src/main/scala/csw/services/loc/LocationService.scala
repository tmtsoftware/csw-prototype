package csw.services.loc

import java.net.{InetAddress, URI}
import javax.jmdns._

import akka.actor._
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.loc.LocationService.LocationTrackerWorker.LocationsReady
import csw.util.Components._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Success

/**
 * Location Service based on Multicast DNS (AppleTalk, Bonjour).
 * Note: On a mac, you can use the command line tool dns-sd to browser the registered services.
 */
object LocationService {

  private val logger = Logger(LoggerFactory.getLogger("LocationService"))

  // Share the JmDNS instance within this jvm for better performance
  private val registry = getRegistry

  // Get JmDNS instance
  private def getRegistry: JmDNS = {
    logger.info("Get Registry")
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

  sealed trait Registration {
    def connection: Connection
  }
  final case class AkkaRegistration(connection: AkkaConnection, component: ActorRef, prefix: String = "") extends Registration
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

  case class ComponentRegistered(connection: Connection)

  case class TrackConnection(connection: Connection)
  case class UnTrackConnection(connection: Connection)

  sealed trait Location {
    def connection: Connection
  }
  final case class UnTrackedLocation(connection: Connection) extends Location
  final case class Unresolved(connection: Connection) extends Location
  final case class ResolvedAkkaLocation(connection: AkkaConnection, uri: URI, prefix: String = "", actorRef: Option[ActorRef] = None) extends Location
  final case class ResolvedHttpLocation(connection: HttpConnection, uri: URI, path: String) extends Location



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
     * Closes the connection and unregisters services registered with this instance
     */
    def close(): Unit = unregister()
  }

  private case class RegisterResult(registry: JmDNS, info: ServiceInfo) extends RegistrationResult {
    override def unregister(): Unit = registry.unregisterService(info)
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
        PATH_KEY -> uri.getPath,
        SYSTEM_KEY -> uri.getUserInfo,
        PREFIX_KEY -> prefix)
      val service = ServiceInfo.create(dnsType, connection.toString, uri.getPort, 0, 0, values.asJava)
      registry.registerService(service)
      logger.info(s"Registered Akka $connection at $uri")
      RegisterResult(registry, service)
    }
  }

  /**
   * Registers the given service for the local host and the given port
   * (The full name of the local host will be used)
   *
   * @param componentId describes the component or service
   * @param port      the port the service is running on
   * @param path      the path part of the URI (default: empty)
   * @return an object that can be used to close the connection and unregister the service
   */
  def registerHttpConnection(componentId: ComponentId, port: Int, path: String = "")(implicit system: ActorSystem): Future[RegistrationResult] = {
    import system.dispatcher
    val connection = HttpConnection(componentId)
    Future {
      val values = Map(
        PATH_KEY -> path)
      val service = ServiceInfo.create(dnsType, connection.toString, port, 0, 0, values.asJava)
      registry.registerService(service)
      logger.info(s"Registered HTTP $connection")
      RegisterResult(registry, service)
    }
  }

  def unregisterConnection(connection: Connection): Unit = {
    val services: Array[ServiceInfo] = registry.list(dnsType)
    val filteredServices = services.filter(si ⇒ si.getName == connection.toString)
    filteredServices.foreach { si ⇒
      logger.info(s"Unregistered connection: $connection")
      registry.unregisterService(si)
    }
    if (filteredServices.length == 0) {
      logger.info(s"Failed to find and unregister: $connection")
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

  object RegistrationTracker {
    def props(register: Set[Registration], replyTo: Option[ActorRef] = None): Props = Props(classOf[RegistrationTracker], register, replyTo)
  }

  /**
    * Convenience method that gets the location service information for a given set of services.
    *
    * @param connections set of requested connections
    * @param system      the caller's actor system
    * @return a future ServicesReady object describing the services found
    */
  def resolve(connections: Set[Connection])(implicit system: ActorSystem, timeout: Timeout): Future[LocationsReady] = {
    import akka.pattern.ask
    import system.dispatcher
    val actorRef = system.actorOf(LocationTrackerWorker.props(None), "Resolve-Worker")
    val f = (actorRef ? LocationTrackerWorker.TrackConnections(connections)).mapTo[LocationsReady]
    f.onComplete {
      case _ ⇒ {
        logger.debug("LocationTrackerWorker completed")
        system.stop(actorRef)
      }
    }
    f
  }


  /**
   * An actor that tracks registration of one or more connections and replies when registered..
   *
   * @param registration Set of registrations to be registered with Location Service
   * @param replyTo     optional actorRef to reply to (default: parent of this actor)
   */
  case class RegistrationTracker(registration: Set[Registration], replyTo: Option[ActorRef]) extends Actor with ActorLogging with ServiceListener {

    import scala.concurrent.ExecutionContext.Implicits.global

    // This count is kept and when all actors have been registered, we kill ourselves
    var count = 0

    registry.addServiceListener(dnsType, this)

    registration.foreach {
      case AkkaRegistration(connection, component, prefix) ⇒
        log.info("Akka Register: " + connection)
        registerAkkaConnection(connection.componentId, component, prefix)(context.system).onSuccess {
          case x ⇒ println("Why does this come last??")
        }
      case HttpRegistration(connection, port, path) ⇒
        log.info("HTTP Register: " + connection)
        registerHttpConnection(connection.componentId, port, path)(context.system)
    }

    override def serviceAdded(event: ServiceEvent): Unit = {
      log.info(s"Listener serviceAdded: ${event.getName}")
      Connection(event.getName) match {
        case Some(c) ⇒
          log.info(s"addService connection: $c")
          if (registration.map(_.connection).contains(c)) {
            log.info(s"Successful register of connection: $c")
            replyTo.getOrElse(context.parent) ! ComponentRegistered(c)
            count = count + 1
            if (count == registration.size) {
              // Remove myself before quitting
              log.info("Ending RegistrationTracker")
              registry.removeServiceListener(dnsType, this)
              self ! PoisonPill
            }
          }
        case None ⇒
          log.info("Attempt to remove a None connection")
      }
    }

    override def serviceRemoved(event: ServiceEvent): Unit = {
      log.info(s"Service Removed Listener: ${event.getName}")
    }

    override def serviceResolved(event: ServiceEvent): Unit = {
      log.info(s"Service Resolved Listener: ${event.getName}")
    }

    def receive: Receive = {
      case x ⇒ log.error(s"Registration actor received unexpected message $x")
    }
  }

  object LocationTracker {
    /**
     * Used to create the LocationTracker actor
     *
     * @param replyTo     optional actorRef to reply to (default: parent of this actor)
     */
    def props(replyTo: Option[ActorRef] = None): Props = Props(classOf[LocationTracker], replyTo)
  }

  /**
   * An actor that notifies the replyTo actor when all the requested services are available.
   * If all services are available, a ServicesReady message is sent. If any of the requested
   * services stops being available, a Disconnected messages is sent.
   *
   * //    * @param connections set of requested services
   * //    * @param replyTo     optional actorRef to reply to (default: parent of this actor)
   */
  case class LocationTracker(replyTo: Option[ActorRef]) extends Actor with ActorLogging with ServiceListener {

    // Set of resolved services (Needs to be a var, since the ServiceListener callbacks prevent using akka state)
    // Private loc is for testing
    private[loc] var connections = Map.empty[Connection, Location]

    registry.addServiceListener(dnsType, this)

    override def postStop(): Unit = {
      log.info("Closing JmDNS")
      registry.close()
    }

    override def serviceAdded(event: ServiceEvent): Unit = {
      log.debug(s"Listener serviceAdded: ${event.getName}")
    }

    override def serviceRemoved(event: ServiceEvent): Unit = {
      log.debug(s"Service Removed Listener: ${event.getName}")
      removeService(Connection(event.getInfo.getName))
    }

    // Removes the given service
    // If it isn't in our map, we don't care since it's not being tracked
    // If it is Unresolved, it's still unresolved
    // If it is resolved, we update to unresolved and send a message to the client
    private def removeService(connection: Option[Connection]): Unit = {
      //val xx = connection.get
      // val x:TrackedLocation = connections(xx)
      connection.flatMap(connections.get) match {
        case None ⇒
          // A service that has been requested, but not yet resolved
          log.info("Attempt to remove a service that we don't care about.")
        case Some(Unresolved(c)) ⇒
          log.info("Removing a service that is already unresolved.")
        case c @ (Some(ResolvedAkkaLocation(_, _, _, _)) | Some(ResolvedHttpLocation(_, _, _))) ⇒
          // Update to be unresolved
          val cstate = c.get
          log.info(s"Updating a resolved connection to unresolved: $cstate")
          val unc = Unresolved(cstate.connection)
          connections += (cstate.connection -> unc)
          sendLocationUpdate(unc)
        case Some(UnTrackedLocation(c)) ⇒
          // This should never occur here but because I can't figure out how to not have it here
          log.info(s"Untracked location: $c")
      }
    }

    private def tryToResolve(connection: Connection): Unit = {
      // Check to see if it is already resolved
      connections.get(connection) match {
        case Some(Unresolved(c)) ⇒
          log.info("Resolve an unresolved service: " + c)
          // Check to see if is already known in registry
          val services: Array[ServiceInfo] = registry.list(dnsType)
          services.filter(si ⇒ si.getName == connection.toString).foreach { s ⇒
            logger.info(s"tryToResolve will resolve connection: $s")
            resolveService(connection, s)
          }
        case x ⇒
          log.info(s"Attempt to track and already tracked connection: $x")
      }
    }

    override def serviceResolved(event: ServiceEvent): Unit = {
      val connection = Connection(event.getName)
      log.info(s"Service Resolved Listener: ${event.getName}")
      // Attempt to fetch the connection from the map of connections
      connection.flatMap(connections.get) match {
        case Some(Unresolved(c)) ⇒
          log.info("Resolve an unresolved service: " + c)
          resolveService(c, event.getInfo)
        case Some(ResolvedAkkaLocation(c, _, _, _)) ⇒
          log.info(s"Attempt to resolve an already ResolvedAkka: $c")
        case Some(ResolvedHttpLocation(c, _, _)) ⇒
          log.info(s"Attempt to resolve an already ResolvedHttp: $c")
        case None ⇒
          // A service that has been requested, but not yet resolved
          log.info(s"Some service that we don't care about: $connection")
        case Some(UnTrackedLocation(c)) ⇒
          // This should never occur here but because I can't figure out how to not have it here
          log.info(s"Untracked location: $c")
      }
    }

    private def resolveService(connection: Connection, info: ServiceInfo): Unit = {
      try {
        // Gets the URI, adding the akka system as user if needed
        def getUri(uriStr: String): Option[URI] = {
          connection match {
            case _: AkkaConnection ⇒ getAkkaUri(uriStr, info.getPropertyString(SYSTEM_KEY))
            case _                 ⇒ Some(new URI(uriStr))
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
                connections += (connection -> rhc)
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
      val actorPath = ActorPath.fromString(rs.uri.toString)
      log.info(s"Attempting to identify actor ${rs.uri}")
      context.actorSelection(actorPath) ! Identify(rs)
    }

    // Called when an actor is identified.
    // Update the resolved map and check if we have everything that was requested.
    private def actorIdentified(actorRefOpt: Option[ActorRef], rs: ResolvedAkkaLocation): Unit = {
      if (actorRefOpt.isDefined) {
        log.info(s"Resolved identified actor $actorRefOpt")
        // Update the table
        val newrc = rs.copy(actorRef = actorRefOpt)
        connections += (rs.connection -> newrc)
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
          log.info("Not currently in list so add it")
          val unc = Unresolved(connection)
          connections += connection -> unc
          // Should we send an update here?
          sendLocationUpdate(unc)
        }
        // Note this will be called whether we are currently tracking or not, could already be resolved
        tryToResolve(connection)

      case UnTrackConnection(connection: Connection) ⇒
        // This is called from outside, so if it isn't in the tracking list, ignore it
        if (!connections.contains(connection)) {
          log.info("Not currently in list so ignore")
        } else {
          // Remove from the map and send an updated Resolved List
          connections -= connection
          // Send Untrack back so state can be updated
          replyTo.getOrElse(context.parent) ! UnTrackedLocation(connection)
        }

      case Terminated(actorRef) ⇒
        // If a requested Akka service terminates, remove it, just in case it didn't unregister with mDns...
        connections.values.foreach {
          case ResolvedAkkaLocation(c, _, _, Some(otherActorRef)) ⇒
            log.info(s"Unresolving service we care about upon death: $c")
            if (actorRef == otherActorRef) removeService(Some(c))
          case _ ⇒
            log.info(s"Death to non-Akka actor.  Not sure how we get here!")
        }

      case x ⇒
        log.error(s"Received unexpected message $x")
    }

  }

  /**
    * A class that can be started from non-actor code that runs a location service actor until it
    * gets a result.
    */
   object LocationTrackerWorker {
    /**
      * Message sent to the parent actor whenever all the requested services become available
      *
      * @param locations  requested connections to the resolved information
      */
    case class LocationsReady(locations: Set[Location])
    case class TrackConnections(connection: Set[Connection])

    def props(replyTo: Option[ActorRef]): Props = Props(classOf[LocationTrackerWorker], replyTo)
  }

  protected class LocationTrackerWorker(replyTo: Option[ActorRef]) extends Actor with ActorLogging {
    import LocationTrackerWorker._

    // Create a tracker for this set of connections
    val tracker = context.system.actorOf(LocationTracker.props(Some(context.self)))
    // And a client for watching the answers
    val trackerClient = LocationTrackerClient(tracker)

    def startupReceive:Receive = {
      case TrackConnections(connections: Set[Connection]) =>
        val mysender = sender()
        context.become(finishReceive(mysender))
        // Track each of the connections in the set while giving the caller to the finishReceive partial
        connections.foreach(c => trackerClient.trackConnection(c))
    }

    def finishReceive(sender: ActorRef):Receive = {
      case m @ _ =>
        trackerClient.trackerClientReceive(m)  // Pass this to client receive
        if (trackerClient.allResolved) {
          // The replyTo is for testing and possible use outside of resolve call
          replyTo.getOrElse(sender) ! LocationsReady(trackerClient.getLocations)
        }
    }

    // Start with startup Receive
    def receive = startupReceive
  }

}

