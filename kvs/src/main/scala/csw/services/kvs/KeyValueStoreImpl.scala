package csw.services.kvs

import akka.actor.ActorRefFactory
import akka.actor.FSM.Failure
import redis.RedisClient
import scala.concurrent.Future
import csw.services.kvs.KeyValueStore._

/**
 * An implementation of the KeyValueStore trait based on Redis.
 * The host and port can be configured in resources/reference.conf.
 *
 * @tparam T the type of items being stored
 * @param host    the Redis server host
 * @param port    the Redis server port
 * @param _system Akka env required by RedisClient
 */
case class KeyValueStoreImpl[T: KvsFormatter](host: String = "127.0.0.1", port: Int = 6379)(implicit _system: ActorRefFactory) extends KeyValueStore[T] {

  protected val redis = RedisClient(host, port)
  implicit val execContext = _system.dispatcher

  override def set(key: String, value: T, history: Int): Future[Unit] = publish(key, value, history)

  override def get(key: String): Future[Option[T]] = lget(key, 0)

  /**
   * Gets the most recent value of the given key that was previously set with lset
   *
   * @param key   the key
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
   * @param key   the key
   * @param value the map of values to store
   * @return the future result (true if successful)
   */
  override def hmset(key: String, value: Map[String, String]): Future[Boolean] = {
    redis.hmset(key, value)
  }

  /**
   * This method is mainly useful for testing hmset. It gets the value of the given field
   * in the map that is the value for the given key. The value is returned here as a String.
   *
   * @param key   the key
   * @param field the key for a value in the map
   * @return the future string value for the field, if found
   */
  override def hmget(key: String, field: String): Future[Option[String]] = {
    redis.hmget[String](key, field).map(_.head)
  }

  /**
   * Publishes the given value for the given key, and also saves it in a list
   * of at most n items.
   *
   * @param key     the key to publish the value to
   * @param value   the event to publish
   * @param history number of previous events to keep in a list for reference (set to 0 for no history)
   * @return A future indicating when publishing has completed
   */
  def publish(key: String, value: T, history: Int): Future[Unit] = {
    // Serialize the event
    val formatter = implicitly[KvsFormatter[T]]
    val bs = formatter.serialize(value) // only do this once
    val h = if (history >= 0) history else 0
    // Use a transaction to send all commands at once
    val redisTransaction = redis.transaction()
    redisTransaction.watch(key)
    val f1 = redisTransaction.lpush(key, bs)
    val f2 = redisTransaction.ltrim(key, 0, h + 1)
    val f3 = redisTransaction.publish(key, bs)
    val f4 = redisTransaction.exec()
    Future.sequence(List(f1, f2, f3, f4)).map(_ ⇒ ())
  }

  def disconnect(): Unit = redis.quit()
}

