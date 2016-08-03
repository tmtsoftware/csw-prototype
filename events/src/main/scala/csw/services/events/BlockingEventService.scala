package csw.services.events

import akka.actor.ActorRefFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * A wrapper API for the Event Service that waits for operations to complete before returning.
 *
 * @param timeout the max amount of time to wait for an operation to complete
 * @param _system Akka env required by RedisClient
 */
case class BlockingEventService(timeout: Duration, settings: EventServiceSettings)(implicit _system: ActorRefFactory) {

  val eventService = EventService(settings)

  /**
   * Publishes the given event
   * @param event the event to publish
   */
  def publish(event: Event, n: Int = 0): Unit =
    Await.result[Unit](eventService.publish(event, n), timeout)

  /**
   * Gets the (most recent) event published with the given event prefix
   * @param prefix the key
   * @return the event or None if the prefix was not found
   */
  def get(prefix: String): Option[Event] =
    Await.result[Option[Event]](eventService.get(prefix), timeout)

  /**
   * Returns a list containing up to the last n events published with the given prefix
   * @param prefix the prefix for an event
   * @param n max number of history events to return
   */
  def getHistory(prefix: String, n: Int): Seq[Event] =
    Await.result[Seq[Event]](eventService.getHistory(prefix, n), timeout)

  /**
   * Deletes the events with the given prefixes from the store
   * @return the future number of events that were deleted
   */
  def delete(prefix: String*): Long =
    Await.result[Long](eventService.delete(prefix: _*), timeout)

  /**
   * Disconnects from the key/value store server
   */
  def disconnect(): Unit = Await.ready(eventService.disconnect(), timeout)

  /**
   * Shuts the key/value store server down
   */
  def shutdown(): Unit = Await.ready(eventService.shutdown(), timeout)
}

