package javacsw.services.kvs

import java.util.Optional

import akka.actor.ActorRefFactory
import csw.services.kvs.KeyValueStore.KvsFormatter
import csw.services.kvs.{BlockingKeyValueStore, KvsSettings, Implicits}
import csw.util.cfg.Configurations.SetupConfig

import scala.concurrent.duration.Duration
import scala.compat.java8.OptionConverters._
import collection.JavaConverters._

object JBlockingKeyValueStore {
  import Implicits._
  // This is easier to do from Scala than from Java due to the use of implicits.

  /**
   * Returns a new JBlockingKeyValueStore[SetupConfig].
   * @param timeout the max amount of time to wait for an operation to complete
   * @param settings Redis server settings
   * @param system Akka env required by RedisClient
   */
  def getSetupConfigStore(timeout: Duration, settings: KvsSettings, system: ActorRefFactory): JBlockingKeyValueStore[SetupConfig] =
    JBlockingKeyValueStore[SetupConfig](timeout, settings, system)

  // XXX TODO add other types
}

/**
 * A wrapper API for a KVS that waits for operations to complete before returing.
 *
 * @param timeout the max amount of time to wait for an operation to complete
 * @param settings Redis server settings
 * @param system Akka env required by RedisClient
 * @tparam T the type (or base type) of objects to store
 */
case class JBlockingKeyValueStore[T: KvsFormatter](timeout: Duration, settings: KvsSettings, system: ActorRefFactory)
    extends IBlockingKeyValueStore[T] {

  private implicit val _system: ActorRefFactory = system
  private val kvs = BlockingKeyValueStore[T](timeout, settings)

  /**
   * Sets (and publishes) the value for the given key
   *
   * @param key the key
   * @param value the value to store
   */
  def set(key: String, value: T): Unit = kvs.set(key, value)

  /**
   * Sets (and publishes) the value for the given key
   *
   * @param key the key
   * @param value the value to store
   * @param n the max number of history values to keep (0 means no history)
   */
  def set(key: String, value: T, n: Int): Unit = kvs.set(key, value, n)

  /**
   * Gets the value of the given key
   *
   * @param key the key
   * @return the result, None if the key was not found
   */
  def get(key: String): Optional[T] = kvs.get(key).asJava

  /**
   * Returns a list containing up to the last n values for the given key
   *
   * @param key the key to use
   * @param n max number of history values to return
   */
  def getHistory(key: String, n: Int): java.util.List[T] = kvs.getHistory(key, n).asJava

  /**
   * Deletes the given key(s) from the store
   *
   * @return the number of keys that were deleted
   */
  def delete(key: String): Boolean = kvs.delete(key) == 1L

  /**
   * Sets a value for the given key, where the value itself is a map with keys and values.
   *
   * @param key the key
   * @param value the map of values to store
   * @return the result (true if successful)
   */
  def hmset(key: String, value: java.util.Map[String, String]): java.lang.Boolean = kvs.hmset(key, value.asScala.toMap)

  /**
   * This method is mainly useful for testing hmset. It gets the value of the given field
   * in the map that is the value for the given key. The value is returned here as a String.
   *
   * @param key the key
   * @param field the key for a value in the map
   * @return the result string value for the field, if found
   */
  def hmget(key: String, field: String): Optional[String] = kvs.hmget(key, field).asJava

}
