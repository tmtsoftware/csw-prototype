package csw.services.loc

import java.net.URI
import javax.jmdns.{JmDNS, ServiceEvent, ServiceInfo, ServiceListener}

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorPath, ActorRef, Identify, Props, Terminated}
import csw.services.loc.Connection.{AkkaConnection, HttpConnection}
import csw.services.loc.LocationService._

object jmsDNSLocationTracker {
  /**
    * Used to create the LocationTracker actor
    *
    * @param replyTo optional actorRef to reply to (default: parent of this actor)
    */
  def props(registry: JmDNS, replyTo: Option[ActorRef] = None): Props = Props(classOf[jmsDNSLocationTracker], registry, replyTo)
}

/**
  * An actor that notifies the replyTo actor when all the requested services are available.
  * If all services are available, a ServicesReady message is sent. If any of the requested
  * services stops being available, a Disconnected messages is sent.
  *
  * @param replyTo optional actorRef to reply to (default: parent of this actor)
  */
case class jmsDNSLocationTracker(registry: JmDNS, replyTo: Option[ActorRef]) extends Actor with ActorLogging with ServiceListener {

  // Set of resolved services (Needs to be a var, since the ServiceListener callbacks prevent using akka state)
  // Private loc is for testing
  private[loc] var connections = Map.empty[Connection, Location]

  // Multicast DNS service type
  private val dnsType = "_csw._tcp.local."
  // URI path part
  private val PATH_KEY = "path"

  // Akka system name
  private val SYSTEM_KEY = "system"

  // Indicates the part of a command service config that this service is interested in
  private val PREFIX_KEY = "prefix"

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
        connections += (loc.connection -> unc)
        sendLocationUpdate(unc)
      }
    }
    connections.get(connection).foreach(rm)
  }

  // Check to see if a connection is already resolved, and if so, resolve the service
  private def tryToResolve(connection: Connection): Unit = {
    connections.get(connection) match {
      case Some(Unresolved(c)) =>
        val s = Option(registry.getServiceInfo(dnsType, connection.toString))
        log.debug(s"Try to resolve connection: $connection: Result: $s")
        s.foreach(resolveService(connection, _))
      case x =>
        log.warning(s"Attempt to track and already tracked connection: $x")
    }
  }

  override def serviceResolved(event: ServiceEvent): Unit = {
    // Gets the connection from the name and, if we are tracking the connection, resolve it
    Connection(event.getName).foreach(connections.get(_).filter(_.isTracked).foreach { loc =>
      resolveService(loc.connection, event.getInfo)
    })
  }

  private def resolveService(connection: Connection, info: ServiceInfo): Unit = {
    try {
      // Gets the URI, adding the akka system as user if needed
      def getUri(uriStr: String): Option[URI] = {
        connection match {
          case _: AkkaConnection =>
            val path = info.getPropertyString(PATH_KEY)
            if (path == null) None else getAkkaUri(uriStr, info.getPropertyString(SYSTEM_KEY))
          case _ =>
            Some(new URI(uriStr))
        }
      }

      info.getURLs(connection.connectionType.name).toList.flatMap(getUri).foreach {
        uri =>
          log.debug(s"Resolve service: resolve URI = $uri")
          connection match {
            case ac: AkkaConnection =>
              val prefix = info.getPropertyString(PREFIX_KEY)
              // An Akka connection is finished after identify returns
              val rac = ResolvedAkkaLocation(ac, uri, prefix)
              identify(rac)
            case hc: HttpConnection =>
              // An Http connection is finished off here
              val path = info.getPropertyString(PATH_KEY)
              val rhc = ResolvedHttpLocation(hc, uri, path)
              connections += (connection -> rhc)
              log.debug("Resolved HTTP: " + connections.values.toList)
              // Here is where the resolved message is sent for an Http Connection
              sendLocationUpdate(rhc)
          }
      }
    } catch {
      case e: Exception => log.error(e, "resolveService: resolve error")
    }
  }

  private def getAkkaUri(uriStr: String, userInfo: String): Option[URI] = try {
    val uri = new URI(uriStr)
    Some(new URI("akka.tcp", userInfo, uri.getHost, uri.getPort, uri.getPath, uri.getQuery, uri.getFragment))
  } catch {
    case e: Exception =>
      // some issue with ipv6 addresses?
      log.error(s"Couldn't make URI from $uriStr and userInfo $userInfo", e)
      None
  }

  // Sends an Identify message to the URI for the actor, which should result in an
  // ActorIdentity reply containing the actorRef.
  private def identify(rs: ResolvedAkkaLocation): Unit = {
    log.debug(s"Attempting to identify actor ${rs.uri.toString}")
    val actorPath = ActorPath.fromString(rs.uri.toString)
    context.actorSelection(actorPath) ! Identify(rs)
  }

  // Called when an actor is identified.
  // Update the resolved map and check if we have everything that was requested.
  private def actorIdentified(actorRefOpt: Option[ActorRef], rs: ResolvedAkkaLocation): Unit = {
    if (actorRefOpt.isDefined) {
      log.debug(s"Resolved: Identified actor $actorRefOpt")
      // Update the table
      val newrc = rs.copy(actorRef = actorRefOpt)
      connections += (rs.connection -> newrc)
      // Watch the actor for death
      context.watch(actorRefOpt.get)
      // Here is where the resolved message is sent for an Akka Connection
      log.debug("Resolved: " + connections.values.toList)
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
    case ActorIdentity(id, actorRefOpt) =>
      id match {
        case rs: ResolvedAkkaLocation => actorIdentified(actorRefOpt, rs)
        case _ => log.warning(s"Received unexpected ActorIdentity id: $id")
      }

    case TrackConnection(connection: Connection) =>
      // This is called from outside, so if it isn't in the tracking list, add it
      if (!connections.contains(connection)) {
        val unc = Unresolved(connection)
        connections += connection -> unc
        // Should we send an update here?
        sendLocationUpdate(unc)
      }
      // Note this will be called whether we are currently tracking or not, could already be resolved
      tryToResolve(connection)

    case UntrackConnection(connection: Connection) =>
      // This is called from outside, so if it isn't in the tracking list, ignore it
      if (connections.contains(connection)) {
        // Remove from the map and send an updated Resolved List
        connections -= connection
        // Send Untrack back so state can be updated
        replyTo.getOrElse(context.parent) ! UnTrackedLocation(connection)
      }

    case Terminated(actorRef) =>
      // If a requested Akka service terminates, remove it, just in case it didn't unregister with mDns...
      connections.values.foreach {
        case ResolvedAkkaLocation(c, _, _, Some(otherActorRef)) =>
          log.debug(s"Unresolving terminated actor: $c")
          if (actorRef == otherActorRef) removeService(c)
        case x => // should not happen
          log.warning(s"Received Terminated message from unknown location: $x")
      }

    case x =>
      log.error(s"Received unexpected message $x")
  }

}
