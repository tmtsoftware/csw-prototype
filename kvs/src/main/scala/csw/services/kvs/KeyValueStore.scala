package csw.services.kvs

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import csw.services.kvs.KeyValueStore._

object KeyValueStore {

  /**
   * Option when setting a string value for a key
   */
  sealed trait SetCondition

  case object SetOnlyIfExists extends SetCondition

  case object SetOnlyIfNotExists extends SetCondition

  case object SetAlways extends SetCondition

  final val defaultHistory = 6
}

/**
 * The interface of a key value store.
 */
trait KeyValueStore {

  /**
   * Sets the value for the given key
   * @param key the key
   * @param event the value to store
   * @param expire optional amount of time until value expires
   * @param setCond optional condition for setting the value
   * @return the future result (true if successful)
   */
  def set(key: String, event: Event,
          expire: Option[FiniteDuration] = None,
          setCond: SetCondition = SetAlways): Future[Boolean]

  /**
   * Gets the value of the given key
   * @param key the key
   * @return the future result, None if the key was not found
   */
  def get(key: String): Future[Option[Event]]

  /**
   * Sets the value for the given key as the head of a list, which is used to remember the previous values.
   * @param key the key to use to store the value
   * @param event the value to store
   * @param history number of previous events to keep in a list for reference (must be a positive number)
   * @return the future result (true if successful)
   */
  def lset(key: String, event: Event, history: Int = defaultHistory): Future[Boolean]

  /**
   * Gets the most recent value of the given key that was previously set with lset
   * @param key the key
   * @return the future result, None if the key was not found
   */
  def lget(key: String): Future[Option[Event]]

  /**
   * Returns a list containing up to the last n values for the given key
   * @param key the key to use
   * @param n max number of history values to return
   */
  def getHistory(key: String, n: Int = defaultHistory + 1): Future[Seq[Event]]

  /**
   * Deletes the given key(s) from the store
   * @return the future number of keys that were deleted
   */
  def delete(key: String*): Future[Long]
}
