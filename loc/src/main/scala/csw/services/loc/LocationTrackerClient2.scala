package csw.services.loc

import akka.actor.Actor.Receive
import akka.actor.ActorRef
import csw.services.loc.LocationServiceProvider._
import csw.services.loc.TrackerData.LocationMap

object TrackerData {
  /**
    * Type alias for a map from a connection description to a location (resolved or not)
    */
  type LocationMap = Map[Connection, Location]
}

/**
  * Wraps a LocationTracker actor in an immutable class
  *
  * @param connections set of conection states
  */
case class TrackerData(connections: LocationMap = Map.empty[Connection, Location]) {

  // Returns a new LocationMap with the given connection added, replaced, or removed
  private[loc] def handleLocationMessage(connectionsIn: LocationMap, loc: Location): LocationMap = {
    if (connectionsIn.contains(loc.connection)) {
      if (loc.isTracked) {
        connectionsIn + (loc.connection -> loc)
      } else {
        connectionsIn - loc.connection
      }
    } else connectionsIn
  }

  /**
    * Updates the information for the given location
    */
  def locationUpdate(loc: Location): TrackerData = {
    TrackerData(handleLocationMessage(connections, loc))
  }

  /**
    * Starts tracking the given connection, setting the initial state to Unresolved
    */
  def trackConnection(connection: Connection): TrackerData = {
    TrackerData(connections + (connection -> Unresolved(connection)))
  }

  def untrackConnection(connection: Connection): Unit = {
    // No operation needed, tracker sends back location update
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

trait LocationTrackerClientActor3 {

  private var trackerData = TrackerData()

  def tracker: ActorRef

  /**
    * Handles location updates and updates the connections map (Should be called from the actor's receive method)
    */
  def trackerClientReceive: Receive = {
    case loc: Location => updateLocation(loc)
  }

  private def trackerTrack(connection: Connection) {
    tracker ! TrackConnection(connection)
  }

  private def trackerUntrack(connection: Connection) {
    tracker ! UntrackConnection(connection)
  }

  def updateLocation(loc: Location): Unit = {
    trackerData = trackerData.locationUpdate(loc)
    if (allResolved) allResolved(getLocations)
  }

  /**
    * Tracks the locations of the given connections
    */
  def trackConnections(connections: Set[Connection]) {
    connections.foreach(trackConnection)
  }

  /**
    * Stops tracking the locations of the given connections
    */
  def untrackConnections(connections: Set[Connection]) {
    connections.foreach(untrackConnection)
  }

  /**
    * Starts tracking the location for the given connection
    */
  def trackConnection(connection: Connection) {
    trackerData = trackerData.trackConnection(connection)
    trackerTrack(connection)
  }

  /**
    * Stops tracking the location for the given connection
    */
  def untrackConnection(connection: Connection) {
    trackerData.untrackConnection(connection)
    trackerUntrack(connection)
  }

  /**
    * Returns the location for the given connection, if known
    */
  def getLocation(connection: Connection): Option[Location] = trackerData.getLocation(connection)

  /**
    * Returns the set of known locations
    */
  def getLocations: Set[Location] = trackerData.getLocations

  /*
   * Returns true if all the tracked connections are currently resolved to locations
   */
  def allResolved: Boolean = trackerData.allResolved

  /**
    * Called when all locations are resolved
    *
    * @param locations the resolved locations (of HCDs, etc.)
    */
  def allResolved(locations: Set[Location]): Unit = {}

  /**
    * Returns a set of ActorRefs for the components that are resolved and match the config's prefix
    */
  /*
  protected def getActorRefs(targetPrefix: String): Set[ActorRef] = {
    val x = getLocations.collect {
      case r @ ResolvedAkkaLocation(connection, uri, prefix, actorRefOpt) if prefix == targetPrefix => actorRefOpt
    }
    x.flatten
  }
  */

}

/*
class TrackerClientActor(replyTo: Option[ActorRef], locationServiceProvider: LocationServiceProvider) extends Actor with LocationTrackerClientActor2 {

  def receive = trackerClientReceive

  //override def createTracker: ActorRef = context.actorOf(locationServiceProvider.trackerProps(Some(context.self)))

}

*/