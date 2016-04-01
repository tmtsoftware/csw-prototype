package csw.services.loc

import akka.actor.Actor.Receive
import akka.actor.{ Actor, ActorLogging, ActorRef }
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.loc.LocationService._
import org.slf4j.LoggerFactory

object LocationTrackerClient {
  private val logger = Logger(LoggerFactory.getLogger("LocationTrackerClient"))
  type LocationMap = Map[Connection, Location]

  private[loc] def handleLocationMessage(connectionsIn: LocationMap, message: Any): LocationMap = {
    val connectionsOut: Option[LocationMap] = message match {
      // Outer case is for current state
      case urc: Unresolved ⇒
        logger.info(s"Unresolved: ${urc.connection}")
        connectionsIn.get(urc.connection).collect {
          case _: Unresolved ⇒
            connectionsIn
          case _ ⇒
            connectionsIn + (urc.connection -> urc)
        }
      case rac: ResolvedAkkaLocation ⇒
        logger.info(s"Resolved Akka Location: " + rac)
        connectionsIn.get(rac.connection).collect { case Unresolved(c) ⇒ connectionsIn + (c -> rac) }
      case rhl: ResolvedHttpLocation ⇒
        logger.info(s"Resolved Http Location")
        connectionsIn.get(rhl.connection).collect { case Unresolved(c) ⇒ connectionsIn + (c -> rhl) }
      case UnTrackedLocation(c) ⇒
        logger.info(s"Untrack Received for: $c")
        Some(connectionsIn - c)
    }
    connectionsOut.getOrElse(connectionsIn)
  }
}

case class LocationTrackerClient(tracker: ActorRef) {
  // Set of conection states
  private[loc] var connections = Map.empty[Connection, Location]

  def trackerClientReceive: Receive = {
    case c: Any ⇒ connections = LocationTrackerClient.handleLocationMessage(connections, c)
  }

  def trackConnection(connection: Connection) = {
    // Add it for checking first tracker message
    connections += (connection -> Unresolved(connection))
    tracker ! TrackConnection(connection)
  }

  def untrackConnection(connection: Connection) = {
    tracker ! UnTrackConnection(connection)
  }

  def getLocation(connection: Connection): Location = connections(connection)

  def getLocations = connections.values.toSet
  /*
   * Returns true if all the tracked connections are resolved to locations
   */
  def allResolved: Boolean = connections.values.collect { case c: Unresolved ⇒ c }.isEmpty

}

// XXX allan: needs a better name
trait LocationTrackerClient2 {
  this: Actor with ActorLogging ⇒

  // Set of conection states
  private[loc] var connections = Map.empty[Connection, Location]

  private lazy val tracker = context.actorOf(LocationTracker.props(Some(context.self)))

  def trackerClientReceive: Receive = {
    // Outer case is for current state
    case c: Any ⇒ connections = LocationTrackerClient.handleLocationMessage(connections, c)
  }

  def checkLocationMessage(message: Any): Boolean = {
    connections = LocationTrackerClient.handleLocationMessage(connections, message)
    allResolved
  }

  def trackConnection(connection: Connection) = {
    // Add it for checking first tracker message
    connections += (connection -> Unresolved(connection))
    tracker ! TrackConnection(connection)
  }

  def untrackConnection(connection: Connection) = {
    tracker ! UnTrackConnection(connection)
  }

  def getLocation(connection: Connection): Location = connections(connection)

  def getLocations: Set[Location] = connections.values.toSet
  /*
   * Returns true if all the tracked connections are resolved to locations
   */
  def allResolved: Boolean = connections.values.collect { case c: Unresolved ⇒ c }.isEmpty
}
