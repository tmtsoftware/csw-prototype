package csw.services.kvs

import akka.actor.ActorSystem
import redis.ByteStringFormatter

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

  final val defaultHistory = 6

  /**
    * All generic objects stored in the KVS need to implement this trait,
    * which is just a wrapper for the Redis specific ByteStringFormatter trait,
    * to hide the Redis dependency from the API.
    * @tparam T the type of the items being stored in the KVS
    */
  trait KvsFormatter[T] extends ByteStringFormatter[T]

  /**
    * Returns a concrete implementation of the KeyValueStore trait (based on Redis)
    * @tparam T the type of values being stored
    */
  def apply[T: KvsFormatter](implicit system: ActorSystem): KeyValueStore[T] = RedisKeyValueStore[T]
}

/**
  * The interface of a key value store.
  */
trait KeyValueStore[T] {

  import KeyValueStore._

  /**
    * Sets (and publishes) the value for the given key
    * @param key the key
    * @param value the value to store
    * @param expire optional amount of time until value expires
    * @param setCond optional condition for setting the value
    * @return the future result (true if successful)
    */
  def set(key: String, value: T,
          expire: Option[FiniteDuration] = None,
          setCond: SetCondition = SetAlways): Future[Boolean]

  /**
    * Gets the value of the given key
    * @param key the key
    * @return the future result, None if the key was not found
    */
  def get(key: String): Future[Option[T]]

  /**
    * Sets the value for the given key as the head of a list, which is used to remember the previous values.
    * @param key the key to use to store the value
    * @param value the value to store
    * @param history number of previous events to keep in a list for reference (must be a positive number)
    * @return the future result (true if successful)
    */
  def lset(key: String, value: T, history: Int = defaultHistory): Future[Boolean]

  /**
    * Gets the most recent value of the given key that was previously set with lset
    * @param key the key
    * @return the future result, None if the key was not found
    */
  def lget(key: String): Future[Option[T]]

  /**
    * Returns a list containing up to the last n values for the given key
    * @param key the key to use
    * @param n max number of history values to return
    */
  def getHistory(key: String, n: Int = defaultHistory + 1): Future[Seq[T]]

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
