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
        connectionsIn + (loc.connection -> loc)
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
    LocationTrackerClient(tracker, connections + (connection -> Unresolved(connection)))
  }

  /**
    * Stops tracking the given connection
    */
  def untrackConnection(connection: Connection): Unit = {
    tracker ! UnTrackConnection(connection)
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
  * Can be used by an actor to keep track of component connections
  */
trait LocationTrackerClientActor {
  this: Actor with ActorLogging ⇒

  // Set of conection states
  private var connections = Map.empty[Connection, Location]

  private lazy val tracker = context.actorOf(LocationTracker.props(Some(self)))

  /**
    * Handles location updates and updates the connections map (Should be called from the actor's receive method)
    */
  def trackerClientReceive: Receive = {
    case loc: Location ⇒ connections = LocationTrackerClient.handleLocationMessage(connections, loc)
    case x => log.error(s"Received unexpected message: $x")
  }

  /**
    * Starts tracking the given connection, setting the initial state to Unresolved
    */
  def trackConnection(connection: Connection) = {
    connections += (connection -> Unresolved(connection))
    tracker ! TrackConnection(connection)
  }

  /**
    * Stops tracking the given connection
    */
  def untrackConnection(connection: Connection) = {
    tracker ! UnTrackConnection(connection)
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
