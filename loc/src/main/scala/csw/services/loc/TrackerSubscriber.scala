package csw.services.loc

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.services.loc.LocationService.{Location, LocationTracker, TrackConnection, UntrackConnection}

/**
 * LocationSubscriberClient can be used to receive updates to Locations.
 *
 * The message received is a LocationService.Location, which can be a ResolvedAkkLocation, ResolvedHttpLocation, or a ResolvedServiceLocation
 *
 *
 */
trait LocationSubscriberClient extends ActorLogging {
  this: Actor =>

  /**
   * An Akka receive partial function that can be used rather than receiving the Location message in your
   * own code.
   * @return Receive partial function
   */
  def locationSubscriberReceive: Receive = {

    case location: Location => locationUpdate(location)

    case x                  => log.error(s"TrackerSubscriberClient received an unknown message: $x")
  }

  /**
   * Start receiving location updates.  It is necessary to call this in the client when you are ready to receive updates.
   * @return Unit
   */
  def subscribeToLocationUpdates(): Unit = {
    context.system.eventStream.subscribe(context.self, classOf[Location])
  }

  /**
   * The given actor stops listening to Location updates.
   * @return Unit
   */
  def unsubscribeLocationUpdates(): Unit = {
    context.system.eventStream.unsubscribe(context.self)
  }

  /**
   * If calling the TrackerSubscriberClient recieve, then override this method to handle Location events.
   * @param location a resolved Location; either HTTP or Akka
   */
  def locationUpdate(location: Location): Unit = {}

  // Indicate we want location updates
  subscribeToLocationUpdates()
}

/**
 * This class distributes Location events from a LocatonTracker within a single ActorSystem of a component.
 *
 * Any client actor that Subscribes will receive Location messages. It then sends the TrackConnection message
 * to the TrackerSubscriber with a Connection to the TrackerSubscriber. This causes the LocationTracker to
 * send updates in the form of Location messages, which are then passed to the subscriber.
 *
 * Note that when the TrackConnection message is sent, the subscriber first receives an Unresolved message before
 * it receives an ResolvedAkkaLocation or ResolvedHttpLocation.
 *
 * It is also possible to send an UntrackConnection(connection) to the TrackerSubscriber to stop getting updates.
 *
 * The Actor watches the subscriber and will unsubscribe it if the subscriber terminates, but it doesn't untrack the connections.
 *
 * Example:
 * <pre>
 *   import TrackerSubscriber._
 *
 *   val trackerSubscriber = system.actorOf(TrackerSubscriber.props)
 *
 *   val c1 = HttpConnection("lgsTromboneHCD", ComponentType.HCD)
 *
 *   trackerSubscriber ! LocationService.Subscribe
 *
 *   // An actor wishing to receive Location messages
 *   trackerSubscriber ! TrackConnecton(c1)
 *
 *   // Later, it can stop receiving Location messages using
 *   trackerSubscriber ! UntrackConnection(c1)
 * </pre>
 *
 */
class LocationSubscriberActor() extends Actor with ActorLogging {
  import LocationSubscriberActor._

  // Start a LocationTracker to listen for our connections
  private val tracker = context.actorOf(LocationTracker.props(Some(self)))

  /**
   * The TrackerSubscriberActor's Receive function listens for messages.
   * @return Receive partion function
   */
  def receive: Receive = {
    // Message to indicate desire to be updated with Location changes
    case Subscribe   => context.system.eventStream.subscribe(sender(), classOf[Location])

    // Indicates desire to unsubscribe sender from location updates
    case Unsubscribe => context.system.eventStream.unsubscribe(sender())

    // Called when tracker sees a change in a location
    case location: Location =>
      log.info(s"Publishing: $location")
      context.system.eventStream.publish(location)

    // Called to indicate need to track a specific connection
    case TrackConnection(connection)   => tracker ! TrackConnection(connection)

    // Called to stop tracking a connection
    case UntrackConnection(connection) => tracker ! UntrackConnection(connection)

    case x                             => log.error(s"TrackerSubscriberActor received an unknown message: $x")
  }
}

object LocationSubscriberActor {

  def props = Props[LocationSubscriberActor]()

  def trackConnection(connection: Connection, trackerSubscriberActor: ActorRef): Unit = {
    trackerSubscriberActor ! TrackConnection(connection)
  }

  def untrackConnection(connection: Connection, trackerSubscriberActor: ActorRef): Unit = {
    trackerSubscriberActor ! TrackConnection(connection)
  }

  def trackConnections(connections: Set[Connection], trackerSubscriberActor: ActorRef) = {
    connections.foreach(trackConnection(_, trackerSubscriberActor))
  }

  def untrackConnections(connections: Set[Connection], trackerSubscriberActor: ActorRef) = {
    connections.foreach(untrackConnection(_, trackerSubscriberActor))
  }

  sealed trait LocationSubscriberMessages

  /**
   * Message sent to begin receiving Location events
   */
  case object Subscribe extends LocationSubscriberMessages

  /**
   * Message sent to stop receiving Location events
   */
  case object Unsubscribe extends LocationSubscriberMessages

}

