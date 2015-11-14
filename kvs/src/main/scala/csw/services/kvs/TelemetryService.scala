package csw.services.kvs

import akka.actor.ActorRefFactory
import csw.util.cfg.Events.StatusEvent
import Implicits._

import scala.concurrent.Future

object TelemetryService {
  def telemetryPrefix(prefix: String): String = s"telem:$prefix"
  def telemetryPrefix(status: StatusEvent): String = telemetryPrefix(status.prefix)
}

/**
 * A class for publishing, getting and subscribing to telemetry.
 *
 * @param settings settings from reference.conf or application.conf with the Redis server host/port information
 * @param _system Akka environment needed by the implementation
 */
case class TelemetryService(settings: KvsSettings)(implicit _system: ActorRefFactory) {
  import _system.dispatcher
  import TelemetryService._
  import Implicits._

  private val kvs = KeyValueStore[StatusEvent](settings)

  /**
   * Sets the value for the status event (key is based on the event's prefix)
   *
   * @param status the value to store
   * @param history optional number of previous values to store
   * @return a future indicating if/when the operation has completed
   */
  def set(status: StatusEvent, history: Int = 0): Future[Unit] =
    kvs.set(telemetryPrefix(status), status, history)

  /**
   * Gets the value for the given status event prefix
   *
   * @param prefix the prefix (key) for the event to get
   * @return the status event, if (and when) found
   */
  def get(prefix: String): Future[Option[StatusEvent]] =
    kvs.get(telemetryPrefix(prefix)).mapTo[Option[StatusEvent]]

  /**
   * Gets a list of the n most recent status event values for the given prefix
   * @param prefix the status event's prefix
   * @param n the max number of values to get
   * @return future sequence of status events, ordered by most recent
   */
  def getHistory(prefix: String, n: Int): Future[Seq[StatusEvent]] =
    kvs.getHistory(telemetryPrefix(prefix), n).mapTo[Seq[StatusEvent]]

  /**
   * Deletes the given  status event from the store
   * @return a future indicating if/when the operation has completed
   */
  def delete(prefix: String): Future[Unit] = kvs.delete(telemetryPrefix(prefix)).map(_ ⇒ ())
}

/**
 * Base type of a subscriber actor to telemetry (status events)
 * The subscribed actor will receive messages of type StatusEvent for the given keys.
 * Note that the keys are automatically prefixed with telem:.
 */
abstract class TelemetrySubscriber extends Subscriber[StatusEvent] {
  import TelemetryService._

  override def subscribe(keys: String*): Unit = {
    super.subscribe(keys.map(telemetryPrefix): _*)
  }

  override def unsubscribe(keys: String*): Unit = {
    super.unsubscribe(keys.map(telemetryPrefix): _*)
  }
}

