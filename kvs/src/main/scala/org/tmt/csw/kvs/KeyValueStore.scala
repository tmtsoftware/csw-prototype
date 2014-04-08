package org.tmt.csw.kvs

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future

object KeyValueStore {

  /**
   * Option when setting a string value for a key
   */
  sealed trait SetCondition
  case object SetOnlyIfExists extends SetCondition
  case object SetOnlyIfNotExists extends SetCondition
  case object SetAlways extends SetCondition
}

/**
 * The interface of a key value store.
 */
trait KeyValueStore {
  import org.tmt.csw.kvs.KeyValueStore._

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
   * Deletes the given key(s) from the store
   * @return the future number of keys that were deleted
   */
  def delete(key: String*): Future[Long]
}
