package csw.services.loc

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import csw.services.loc.LocationServiceProvider.Location
import csw.services.loc.LocationServiceProvider._

/**
 * TMT Source Code: 8/4/16.
 */
object TestLocationTracker {

  def props(connections: Map[Connection, Location], replyTo: Option[ActorRef]): Props = Props(classOf[TestLocationTracker], connections, replyTo)
}

case class TestLocationTracker(var connections: Map[Connection, Location], replyTo: Option[ActorRef]) extends Actor with ActorLogging {
  // Set of resolved services (Needs to be a var, since the ServiceListener callbacks prevent using akka state)

  // This is done so that we can add any registrations after the Tracker is created
  TestLocationService.subscribe(context.self)

  // Note that this is a shared data structure between all test trackers!!!

  private def sendLocationUpdate(location: Location): Unit = {
    replyTo.getOrElse(context.parent) ! location
  }

  // Check to see if a connection is already resolved, and if so, resolve the service
  private def tryToResolve(connection: Connection): Unit = {
    connections.get(connection) match {
      case Some(Unresolved(c)) =>
      /*
        val s = Option(registry.getServiceInfo(dnsType, connection.toString))
        log.debug(s"Try to resolve connection: $connection: Result: $s")
        s.foreach(resolveService(connection, _))
        */
      case x =>
        log.warning(s"Attempt to track and already tracked connection: $x")
    }
  }

  // Receive messages
  def receive: Receive = {

    case TrackConnection(connection: Connection) =>
      // This is called from outside, so if it isn't in the tracking make it an error for now
      if (!connections.contains(connection)) {
        // This should fail since all connections need to be registered properly via register in service
        log.info(s"Failed to track connection: $connection. With tests it must be registered prior to tracking in TestLocationService/TestLocationTracker")
      } else {
        val rc: Location = connections(connection)
        // Send tracking information back
        sendLocationUpdate(rc)
      }

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
        // if (actorRef == otherActorRef) removeService(c)
        //connections += (loc.connection -> unc)
        case x => // should not happen
          log.warning(s"Received Terminated message from unknown location: $x")
      }

    case x =>
      log.error(s"Received unexpected message $x")
  }

}
