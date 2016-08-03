package csw.services.events

import akka.actor.ActorRefFactory
import akka.util.Timeout
import csw.util.config.Events.StatusEvent

import scala.concurrent.{Await, Future}

/**
 * A convenience class for publishing, getting and subscribing to telemetry (StatusEvent objects).
 *
 * @param settings settings from reference.conf or application.conf with the Redis server host/port information
 * @param _system Akka environment needed by the implementation
 */
case class TelemetryService(settings: EventServiceSettings)(implicit _system: ActorRefFactory) {
  import _system.dispatcher

  private val eventService = EventService(settings)

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
 * Base type of a subscriber actor to telemetry (status events)
 * The subscribed actor will receive messages of type StatusEvent for the given prefixes.
 */
abstract class TelemetrySubscriber extends Subscriber {
  override def subscribe(prefixes: String*): Unit = {
    super.subscribe(prefixes: _*)
  }

  override def unsubscribe(prefixes: String*): Unit = {
    super.unsubscribe(prefixes: _*)
  }
}

/**
 * Provides a blocking, synchronous API to the telemetry service.
 * @param ts a reference to the telemetry service to use
 * @param timeout max amount of time to wait for a result before timing out
 * @param context environment needed for futures
 */
case class BlockingTelemetryService(ts: TelemetryService)(implicit val timeout: Timeout, context: ActorRefFactory) {

  /**
   * Disconnects from the key/value store server
   */
  def disconnect(): Unit = Await.result(ts.disconnect(), timeout.duration)

  /**
   * Shuts the key/value store server down
   */
  def shutdown(): Unit = Await.result(ts.shutdown(), timeout.duration)

  /**
   * Publishes the value for the status event (key is based on the event's prefix)
   *
   * @param status the value to store
   * @param history optional number of previous values to store
   */
  def publish(status: StatusEvent, history: Int = 0): Unit =
    Await.result(ts.publish(status, history), timeout.duration)

  /**
   * Gets the value for the given status event prefix
   *
   * @param prefix the prefix (key) for the event to get
   * @return the status event, if found
   */
  def get(prefix: String): Option[StatusEvent] =
    Await.result(ts.get(prefix), timeout.duration)

  /**
   * Gets a list of the n most recent status event values for the given prefix
   * @param prefix the status event's prefix
   * @param n the max number of values to get
   * @return sequence of status events, ordered by most recent
   */
  def getHistory(prefix: String, n: Int): Seq[StatusEvent] =
    Await.result(ts.getHistory(prefix, n), timeout.duration)

  /**
   * Deletes the given  status event from the store
   */
  def delete(prefix: String): Unit =
    Await.result(ts.delete(prefix), timeout.duration)
}

