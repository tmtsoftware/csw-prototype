package csw.services.events

import akka.actor.{ActorRef, ActorRefFactory}
import csw.services.events.EventService.EventMonitor
import csw.util.config.Events.StatusEvent
import redis.RedisClient

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object TelemetryService {
  // Converts a callback that takes an Event to one that takes a StatusEvent
  private def callbackConverter(telemCallback: StatusEvent => Unit)(event: Event): Unit =
    event match {
      case s: StatusEvent => telemCallback(s)
      case _              =>
    }
}

/**
 * A convenience class for publishing, getting and subscribing to telemetry (StatusEvent objects).
 *
 * @param settings settings from reference.conf or application.conf with the Redis server host/port information
 * @param _system Akka environment needed by the implementation
 */
case class TelemetryService(settings: EventServiceSettings)(implicit _system: ActorRefFactory) {
  import _system.dispatcher
  import TelemetryService._

  private val eventService = EventServiceImpl(RedisClient(settings.redisHostname, settings.redisPort))

  /**
   * Publishes the status event (key is based on the event's prefix)
   *
   * @param status the value to store
   * @param history optional number of previous values to store
   * @return a future indicating if/when the operation has completed
   */
  def publish(status: StatusEvent, history: Int = 0): Future[Unit] =
    eventService.publish(status, history)

  /**
   * Subscribes an actor or callback function to events matching the given prefixes
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param subscriber an optional actor to receive Event messages
   * @param callback   an optional callback which will be called with Event objects (in another thread)
   * @param prefixes   one or more prefixes of events, may include wildcard
   */
  def subscribe(subscriber: Option[ActorRef], callback: Option[StatusEvent => Unit], prefixes: String*): EventMonitor =
    eventService.subscribe(subscriber, callback.map(callbackConverter), prefixes: _*)

  /**
   * Gets the value for the given status event prefix
   *
   * @param prefix the prefix (key) for the event to get
   * @return the status event, if (and when) found
   */
  def get(prefix: String): Future[Option[StatusEvent]] =
    eventService.get(prefix).mapTo[Option[StatusEvent]]

  /**
   * Gets a list of the n most recent status event values for the given prefix
   * @param prefix the status event's prefix
   * @param n the max number of values to get
   * @return future sequence of status events, ordered by most recent
   */
  def getHistory(prefix: String, n: Int): Future[Seq[StatusEvent]] =
    eventService.getHistory(prefix, n).mapTo[Seq[StatusEvent]]

  /**
   * Deletes the given  status event from the store
   * @return a future indicating if/when the operation has completed
   */
  def delete(prefix: String): Future[Unit] = eventService.delete(prefix).map(_ => ())

  /**
   * Disconnects from the key/value store server
   */
  def disconnect(): Future[Unit] = eventService.disconnect()

  /**
   * Shuts the key/value store server down
   */
  def shutdown(): Future[Unit] = eventService.shutdown()
}

/**
 * Provides a blocking, synchronous API to the telemetry service.
 * @param timeout max amount of time to wait for a result before timing out
 * @param ts a reference to the telemetry service to use
 * @param context environment needed for futures
 */
case class BlockingTelemetryService(timeout: Duration, settings: EventServiceSettings)(implicit val context: ActorRefFactory) {
  val ts = TelemetryService(settings)

  /**
   * Publishes the value for the status event (key is based on the event's prefix)
   *
   * @param status the value to store
   * @param history optional number of previous values to store
   */
  def publish(status: StatusEvent, history: Int = 0): Unit =
    Await.result(ts.publish(status, history), timeout)

  /**
   * Subscribes an actor or callback function to events matching the given prefixes
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param subscriber an optional actor to receive Event messages
   * @param callback   an optional callback which will be called with Event objects (in another thread)
   * @param prefixes   one or more prefixes of events, may include wildcard
   */
  def subscribe(subscriber: Option[ActorRef], callback: Option[StatusEvent => Unit], prefixes: String*): EventMonitor =
    ts.subscribe(subscriber, callback, prefixes: _*)

  /**
   * Gets the value for the given status event prefix
   *
   * @param prefix the prefix (key) for the event to get
   * @return the status event, if found
   */
  def get(prefix: String): Option[StatusEvent] =
    Await.result(ts.get(prefix), timeout)

  /**
   * Gets a list of the n most recent status event values for the given prefix
   * @param prefix the status event's prefix
   * @param n the max number of values to get
   * @return sequence of status events, ordered by most recent
   */
  def getHistory(prefix: String, n: Int): Seq[StatusEvent] =
    Await.result(ts.getHistory(prefix, n), timeout)

  /**
   * Deletes the given  status event from the store
   */
  def delete(prefix: String): Unit =
    Await.result(ts.delete(prefix), timeout)

  /**
   * Disconnects from the key/value store server
   */
  def disconnect(): Unit = Await.result(ts.disconnect(), timeout)

  /**
   * Shuts the key/value store server down
   */
  def shutdown(): Unit = Await.result(ts.shutdown(), timeout)
}

