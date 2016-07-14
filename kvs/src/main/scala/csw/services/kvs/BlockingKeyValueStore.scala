package csw.services.kvs

import akka.actor.ActorRefFactory
import csw.services.kvs.KeyValueStore.KvsFormatter

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * A wrapper API for a KVS that waits for operations to complete before returning.
 *
 * @param timeout the max amount of time to wait for an operation to complete
 * @param _system Akka env required by RedisClient
 * @tparam T the type (or base type) of objects to store
 */
case class BlockingKeyValueStore[T: KvsFormatter](timeout: Duration, settings: KvsSettings)(implicit _system: ActorRefFactory) {

  val kvs = KeyValueStore[T](settings)

  /**
   * Sets (and publishes) the value for the given key
   * @param key the key
   * @param value the value to store
   * @param n the max number of history values to keep (default: 0, no history)
   */
  def set(key: String, value: T, n: Int = 0): Unit =
    Await.result[Unit](kvs.set(key, value, n), timeout)

  /**
   * Gets the value of the given key
   * @param key the key
   * @return the result, None if the key was not found
   */
  def get(key: String): Option[T] =
    Await.result[Option[T]](kvs.get(key), timeout)

  /**
   * Returns a list containing up to the last n values for the given key
   * @param key the key to use
   * @param n max number of history values to return
   */
  def getHistory(key: String, n: Int): Seq[T] =
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

  /**
   * Disconnects from the key/value store server
   */
  def disconnect(): Unit = Await.ready(kvs.disconnect(), timeout)

  /**
   * Shuts the key/value store server down
   */
  def shutdown(): Unit = Await.ready(kvs.shutdown(), timeout)
}

