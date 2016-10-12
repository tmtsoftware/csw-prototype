package csw.services.loc

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import csw.services.loc.LocationService.{Location, LocationTracker, TrackConnection, UntrackConnection}

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
 * import TrackerSubscriber._
 *
 * val trackerSubscriber = system.actorOf(TrackerSubscriber.props)
 *
 * val c1 = HttpConnection("lgsTromboneHCD", ComponentType.HCD)
 *
 * trackerSubscriber ! LocationService.Subscribe
 *
 * // An actor wishing to receive Location messages
 * trackerSubscriber ! TrackConnecton(c1)
 *
 * // Later, it can stop receiving Location messages using
 * trackerSubscriber ! UntrackConnection(c1)
 * </pre>
 *
 */
trait TrackerSubscriber {
  this: Actor with ActorLogging =>

  import TrackerSubscriber._

  private val tracker = context.actorOf(LocationTracker.props(Some(self)))

  def trackerSubscriberReceive: Receive = {
    case Subscribe =>
      context.watch(sender())
      context.system.eventStream.subscribe(sender(), classOf[Location])

    case Unsubscribe =>
      unsubscribe(sender())

    case loc: Location =>
      log.info(s"Received location: $loc")
      context.system.eventStream.publish(loc)

    case Terminated(actorRef) =>
      unsubscribe(actorRef)

    case TrackConnection(connection) =>
      tracker ! TrackConnection(connection)

    case UntrackConnection(connection) =>
      tracker ! UntrackConnection(connection)

    case x => log.error(s"TrackerSubscriber received an unknown message: $x")
  }

  def unsubscribe(actorRef: ActorRef) = {
    context.unwatch(actorRef)
    context.system.eventStream.unsubscribe(actorRef)
  }

}

object TrackerSubscriber {

  sealed trait TrackerSubscriberMessages

  case object Subscribe extends TrackerSubscriberMessages

  case object Unsubscribe extends TrackerSubscriberMessages
}
