package csw.services.loc

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.services.loc.LocationService.{Location, LocationTracker}

object LocationTrackerWorker {

  /**
   * Message sent to the replyTo actor (or sender) whenever all the requested services become available
   *
   * @param locations requested connections to the resolved information
   */
  case class LocationsReady(locations: Set[Location])

  case class TrackConnections(connection: Set[Connection])

  /**
   * Used to create the actor
   *
   * @param replyTo the LocationsReady message is sent to this actor, if set, otherwise the actor that sent the
   *                TrackConnections message
   */
  def props(replyTo: Option[ActorRef]): Props = Props(classOf[LocationTrackerWorker], replyTo)
}

private class LocationTrackerWorker(replyTo: Option[ActorRef]) extends Actor with ActorLogging {

  import LocationTrackerWorker._

  // Create a tracker for this set of connections
  val tracker = context.actorOf(LocationTracker.props(Some(self)))

  // And a client for watching the answers
  var trackerClient = LocationTrackerClient(tracker)

  // This is needed in order to get a sender to reply to in the case that replyTo is None
  // (This is the case when used in an "ask" message, where you want to turn the response into a Future)
  def startupReceive: Receive = {
    case TrackConnections(connections) =>
      context.become(finishReceive(replyTo.getOrElse(sender())))
      // Track each of the connections
      connections.foreach(c => trackerClient = trackerClient.trackConnection(c))
  }

  def finishReceive(a: ActorRef): Receive = {
    case loc: Location =>
      trackerClient = trackerClient.locationUpdate(loc)
      if (trackerClient.allResolved) {
        a ! LocationsReady(trackerClient.getLocations)
        context.stop(self)
      }

    case x => log.error(s"Unexpected message: $x")
  }

  // Start with startup Receive
  def receive = startupReceive
}

