package csw.services.loc

import akka.actor.{Actor, ActorLogging, ActorRef}
import csw.services.loc.LocationService._
import csw.services.loc.LocationTrackerClient.{AllResolved, LocationMap}

object LocationTrackerClient {
  /**
   * Type alias for a map from a connection description to a location (resolved or not)
   */
  type LocationMap = Map[Connection, Location]

  // Returns a new LocationMap with the given connection added, replaced, or removed
  private[loc] def handleLocationMessage(connectionsIn: LocationMap, loc: Location): LocationMap = {
    if (connectionsIn.contains(loc.connection)) {
      if (loc.isTracked)
        connectionsIn + (loc.connection → loc)
      else
        connectionsIn - loc.connection
    } else connectionsIn
  }

  /**
   * Message optionally sent to self when all connections are resolved
   */
  case object AllResolved
}

/**
 * Wraps a LocationTracker actor in an immutable class
 *
 * @param tracker     the LocationTracker actor ref
 * @param connections set of conection states
 */
case class LocationTrackerClient(tracker: ActorRef, connections: LocationMap = Map.empty[Connection, Location]) {

  import csw.services.loc.LocationTrackerClient._

  /**
   * Updates the information for the given location
   */
  def locationUpdate(loc: Location): LocationTrackerClient = {
    LocationTrackerClient(tracker, handleLocationMessage(connections, loc))
  }

  /**
   * Starts tracking the given connection, setting the initial state to Unresolved
   */
  def trackConnection(connection: Connection): LocationTrackerClient = {
    tracker ! TrackConnection(connection)
    LocationTrackerClient(tracker, connections + (connection → Unresolved(connection)))
  }

  /**
   * Stops tracking the given connection
   */
  def untrackConnection(connection: Connection): Unit = {
    tracker ! UntrackConnection(connection)
  }

  /**
   * Returns the location for the given connection, if known
   */
  def getLocation(connection: Connection): Option[Location] = connections.get(connection)

  /**
   * Returns the set of known locations
   */
  def getLocations: Set[Location] = connections.values.toSet

  /*
   * Returns true if all the tracked connections are currently resolved to locations
   */
  def allResolved: Boolean = !connections.values.exists(!_.isResolved)
}

/**
 * Can be used by an actor to keep track of component connections.
 */
trait LocationTrackerClientActor {
  this: Actor with ActorLogging ⇒

  private val tracker = context.actorOf(LocationTracker.props(Some(self)))
  private var trackerClient = LocationTrackerClient(tracker)

  /**
   * Handles location updates and updates the connections map (Should be called from the actor's receive method)
   * @param notifyAllResolved if true, an AllResolved message is sent to self once all connections are resolved
   */
  protected def trackerClientReceive(notifyAllResolved: Boolean = false): Receive = {
    case loc: Location ⇒
      log.info(s"Received location: $loc")
      trackerClient = trackerClient.locationUpdate(loc)
      if (notifyAllResolved && allResolved) {
        self ! AllResolved
      }

    case TrackConnection(connection) ⇒
      trackConnection(connection)

    case UntrackConnection(connection) ⇒
      untrackConnection(connection)
  }

  /**
   * Tracks the locations of the given connections
   */
  protected def trackConnections(connections: Set[Connection]): Unit = {
    connections.foreach(trackConnection)
  }

  /**
   * Stops tracking the locations of the given connections
   */
  protected def untrackConnections(connections: Set[Connection]): Unit = {
    connections.foreach(untrackConnection)
  }

  /**
   * Starts tracking the location for the given connection
   */
  protected def trackConnection(connection: Connection): Unit = {
    trackerClient = trackerClient.trackConnection(connection)
  }

  /**
   * Stops tracking the location for the given connection
   */
  protected def untrackConnection(connection: Connection): Unit = {
    trackerClient.untrackConnection(connection)
  }

  /**
   * Returns the location for the given connection, if known
   */
  protected def getLocation(connection: Connection): Option[Location] = trackerClient.getLocation(connection)

  /**
   * Returns the set of known locations
   */
  protected def getLocations: Set[Location] = trackerClient.getLocations

  /*
   * Returns true if all the tracked connections are currently resolved to locations
   */
  protected def allResolved: Boolean = trackerClient.allResolved

  /**
   * Returns a set of ActorRefs for the components that are resolved and match the config's prefix
   */
  protected def getActorRefs(targetPrefix: String): Set[ActorRef] = {
    val x = getLocations.collect {
      case r @ ResolvedAkkaLocation(connection, uri, prefix, actorRefOpt) if prefix == targetPrefix ⇒ actorRefOpt
    }
    x.flatten
  }

}
