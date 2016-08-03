package csw.services.events

import akka.actor.ActorRefFactory

import scala.concurrent.Future

object EventService {

  /**
   * Returns a concrete implementation of the EventService trait (based on Redis)
   * @param settings contains the host and port settings from reference.conf, or application.conf
   * @param _system Akka env required for RedisClient
   */
  def apply(settings: EventServiceSettings)(implicit _system: ActorRefFactory): EventService =
    EventServiceImpl(settings.redisHostname, settings.redisPort)
}

/**
 * The interface of a key value store.
 */
trait EventService {

  /**
   * Publishes the given event
   * @param event the event to publish
   * @param history the max number of history events to keep (default: 0, no history)
   * @return the future result (indicates if and when the operation completed, may be ignored)
   */
  def publish(event: Event, history: Int = 0): Future[Unit]

  /**
   * Gets the (most recent) event published with the given event prefix
   * @param prefix the key
   * @return the future result, None if the key was not found
   */
  def get(prefix: String): Future[Option[Event]]

  /**
   * Returns a list containing up to the last n events published with the given prefix
   * @param prefix the prefix for an event
   * @param n max number of history events to return
   */
  def getHistory(prefix: String, n: Int): Future[Seq[Event]]

  /**
   * Deletes the events with the given prefixes from the store
   * @return the future number of events that were deleted
   */
  def delete(prefix: String*): Future[Long]

  /**
   * Disconnects from the key/value store server
   */
  def disconnect(): Future[Unit]

  /**
   * Shuts the key/value store server down
   */
  def shutdown(): Future[Unit]
}
