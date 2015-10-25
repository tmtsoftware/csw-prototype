package csw.services.kvs

import akka.actor.ActorRefFactory
import redis.ByteStringFormatter

import scala.concurrent.Future

object KeyValueStore {

  /**
   * All generic objects stored in the KVS need to implement this trait,
   * which is just a wrapper for the Redis specific ByteStringFormatter trait,
   * to hide the Redis dependency from the API.
   * @tparam T the type of the items being stored in the KVS
   */
  trait KvsFormatter[T] extends ByteStringFormatter[T]

  /**
   * Returns a concrete implementation of the KeyValueStore trait (based on Redis)
   * @param settings contains the host and port settings from reference.conf, or application.conf
   * @param _system Akka env required for RedisClient
   * @tparam T the type of values being stored
   */
  def apply[T: KvsFormatter](settings: KvsSettings)(implicit _system: ActorRefFactory): KeyValueStore[T] =
    KeyValueStoreImpl[T](settings.redisHostname, settings.redisPort)
}

/**
 * The interface of a key value store.
 */
trait KeyValueStore[T] {

  /**
   * Sets (and publishes) the value for the given key
   * @param key the key
   * @param value the value to store
   * @param history the max number of history values to keep (default: 0, no history)
   * @return the future result (indicates if and when the operation completed, may be ignored)
   */
  def set(key: String, value: T, history: Int = 0): Future[Unit]

  /**
   * Gets the (most recent) value of the given key
   * @param key the key
   * @return the future result, None if the key was not found
   */
  def get(key: String): Future[Option[T]]

  /**
   * Returns a list containing up to the last n values for the given key
   * @param key the key to use
   * @param n max number of history values to return
   */
  def getHistory(key: String, n: Int): Future[Seq[T]]

  /**
   * Deletes the given key(s) from the store
   * @return the future number of keys that were deleted
   */
  def delete(key: String*): Future[Long]

  /**
   * Sets a value for the given key, where the value itself is a map with keys and values.
   *
   * @param key the key
   * @param value the map of values to store
   * @return the future result (true if successful)
   */
  def hmset(key: String, value: Map[String, String]): Future[Boolean]

  /**
   * This method is mainly useful for testing hmset. It gets the value of the given field
   * in the map that is the value for the given key. The value is returned here as a String.
   * @param key the key
   * @param field the key for a value in the map
   * @return the future string value for the field, if found
   */
  def hmget(key: String, field: String): Future[Option[String]]

}
