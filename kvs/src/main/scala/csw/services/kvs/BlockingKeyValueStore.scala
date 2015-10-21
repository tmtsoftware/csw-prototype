package csw.services.kvs

import akka.actor.ActorSystem
import csw.services.kvs.KeyValueStore.KvsFormatter

import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, FiniteDuration }

/**
 * A wrapper API for a KVS that waits for operations to complete before returing.
 *
 * @param timeout the max amount of time to wait for an operation to complete
 * @tparam T the type (or base type) of objects to store
 */
case class BlockingKeyValueStore[T: KvsFormatter](timeout: Duration)(implicit system: ActorSystem) {
  import KeyValueStore._

  val kvs = KeyValueStore[T]

  /**
   * Sets the value for the given key
   * @param key the key
   * @param value the value to store
   * @param expire optional amount of time until value expires
   * @param setCond optional condition for setting the value
   * @return the result (true if successful)
   */
  def set(key: String, value: T,
          expire: Option[FiniteDuration] = None,
          setCond: SetCondition = SetAlways): Boolean =
    Await.result[Boolean](kvs.set(key, value, expire, setCond), timeout)

  /**
   * Gets the value of the given key
   * @param key the key
   * @return the result, None if the key was not found
   */
  def get(key: String): Option[T] =
    Await.result[Option[T]](kvs.get(key), timeout)

  /**
   * Sets the value for the given key as the head of a list, which is used to remember the previous values.
   * @param key the key to use to store the value
   * @param value the value to store
   * @param history number of previous events to keep in a list for reference (must be a positive number)
   * @return the result (true if successful)
   */
  def lset(key: String, value: T, history: Int = defaultHistory): Boolean =
    Await.result[Boolean](kvs.lset(key, value, history), timeout)

  /**
   * Gets the most recent value of the given key that was previously set with lset
   * @param key the key
   * @return the result, None if the key was not found
   */
  def lget(key: String): Option[T] =
    Await.result[Option[T]](kvs.lget(key), timeout)

  /**
   * Returns a list containing up to the last n values for the given key
   * @param key the key to use
   * @param n max number of history values to return
   */
  def getHistory(key: String, n: Int = defaultHistory + 1): Seq[T] =
    Await.result[Seq[T]](kvs.getHistory(key, n), timeout)

  /**
   * Deletes the given key(s) from the store
   * @return the number of keys that were deleted
   */

  def delete(key: String*): Long =
    Await.result[Long](kvs.delete(key: _*), timeout)

  /**
   * Sets a value for the given key, where the value itself is a map with keys and values.
   *
   * @param key the key
   * @param value the map of values to store
   * @return the result (true if successful)
   */
  def hmset(key: String, value: Map[String, String]): Boolean =
    Await.result[Boolean](kvs.hmset(key, value), timeout)

  /**
   * This method is mainly useful for testing hmset. It gets the value of the given field
   * in the map that is the value for the given key. The value is returned here as a String.
   * @param key the key
   * @param field the key for a value in the map
   * @return the result string value for the field, if found
   */
  def hmget(key: String, field: String): Option[String] =
    Await.result[Option[String]](kvs.hmget(key, field), timeout)
}

