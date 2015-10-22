package csw.services.kvs

import akka.actor.ActorSystem
import redis.RedisClient
import scala.concurrent.Future
import csw.services.kvs.KeyValueStore._

/**
 * An implementation of the KeyValueStore trait based on Redis.
 * The host and port can be configured in resources/reference.conf.
 *
 * @param system the Akka actor system, needed to access the settings and RedisClient
 */
case class KeyValueStoreImpl[T: KvsFormatter](implicit system: ActorSystem) extends KeyValueStore[T] {

  protected val settings = KvsSettings(system)
  protected val redis = RedisClient(settings.redisHostname, settings.redisPort)
  implicit val execContext = system.dispatcher

  override def set(key: String, value: T, history: Int): Future[Unit] = publish(key, value, history).map(_ ⇒ ())

  override def get(key: String): Future[Option[T]] = lget(key, 0)

  /**
   * Gets the most recent value of the given key that was previously set with lset
   * @param key the key
   * @param index the index of the value to get (defaults to 0, for the most recent value)
   * @return the future result, None if the key was not found
   */
  def lget(key: String, index: Int = 0): Future[Option[T]] = {
    redis.lindex(key, index)
  }

  override def getHistory(key: String, n: Int): Future[Seq[T]] = {
    redis.lrange(key, 0, n - 1)
  }

  override def delete(keys: String*): Future[Long] = {
    redis.del(keys: _*)
  }

  /**
   * Sets a value for the given key, where the value itself is a map with keys and values.
   *
   * @param key the key
   * @param value the map of values to store
   * @return the future result (true if successful)
   */
  override def hmset(key: String, value: Map[String, String]): Future[Boolean] = {
    redis.hmset(key, value)
  }

  /**
   * This method is mainly useful for testing hmset. It gets the value of the given field
   * in the map that is the value for the given key. The value is returned here as a String.
   * @param key the key
   * @param field the key for a value in the map
   * @return the future string value for the field, if found
   */
  override def hmget(key: String, field: String): Future[Option[String]] = {
    redis.hmget[String](key, field).map(_.head)
  }

  /**
   * Publishes the given value for the given key, and also saves it in a list
   * of at most n items.
   * @param key the key to publish the value to
   * @param value the event to publish
   * @param history number of previous events to keep in a list for reference (set to 0 for no history)
   * @return the number of subscribers that received the value
   */
  def publish(key: String, value: T, history: Int): Future[Long] = {
    // Serialize the event
    val formatter = implicitly[KvsFormatter[T]]
    val bs = formatter.serialize(value) // only do this once
    val h = if (history >= 0) history else 0
    // Use a transaction to send all commands at once
    val redisTransaction = redis.transaction()
    redisTransaction.watch(key)
    redisTransaction.lpush(key, bs)
    redisTransaction.ltrim(key, 0, h + 1)
    val result = redisTransaction.publish(key, bs)
    redisTransaction.exec()
    result
  }

}

