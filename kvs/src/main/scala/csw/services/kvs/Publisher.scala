package csw.services.kvs

import redis.ByteStringFormatter
import akka.actor.{ ActorLogging, Actor }
import redis.RedisClient

/**
 * Adds the ability to publish objects of type T.
 */
abstract class Publisher[T: ByteStringFormatter] extends Actor with ActorLogging {

  private val actorSystem = context.system
  private val settings = KvsSettings(actorSystem)
  private val redis = RedisClient(settings.redisHostname, settings.redisPort)
  private val formatter = implicitly[ByteStringFormatter[T]]

  /**
   * Publishes the given event on the given channel, and also saves it in a list
   * of at most n items.
   * @param channel the channel or key for this event
   * @param value the event to publish
   * @param history number of previous events to keep in a list for reference (set to 0 for no history)
   */
  def publish(channel: String, value: T, history: Int = KeyValueStore.defaultHistory): Unit = {
    // Serialize the event
    val bs = formatter.serialize(value) // only do this once

    if (history >= 0) {
      // Use a transaction to send all commands at once
      val redisTransaction = redis.transaction()
      redisTransaction.watch(channel)
      redisTransaction.lpush(channel, bs)
      redisTransaction.ltrim(channel, 0, history + 1)
      redisTransaction.publish(channel, bs)
      redisTransaction.exec()
    } else {
      redis.publish(channel, bs)
    }
  }
}
