package csw.services.loc

import akka.actor.{Actor, ActorLogging, ActorRef}
import csw.services.loc.LocationService._
import csw.services.loc.LocationTrackerClient.LocationMap

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
   */
  def trackerClientReceive: Receive = {
    case loc: Location ⇒
      log.info(s"Received location: $loc")
      trackerClient = trackerClient.locationUpdate(loc)

    case TrackConnection(connection) ⇒
      trackerClient = trackerClient.trackConnection(connection)

    case UntrackConnection(connection) ⇒
      trackerClient.untrackConnection(connection)
  }

  /**
   * Returns the location for the given connection, if known
   */
  def getLocation(connection: Connection): Option[Location] = trackerClient.getLocation(connection)

  /**
   * Returns the set of known locations
   */
  def getLocations: Set[Location] = trackerClient.getLocations

  /*
   * Returns true if all the tracked connections are currently resolved to locations
   */
  def allResolved: Boolean = trackerClient.allResolved
}
