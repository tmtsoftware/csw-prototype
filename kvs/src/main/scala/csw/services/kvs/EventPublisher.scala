package csw.services.kvs

import redis.ByteStringFormatter
import akka.actor.{ActorLogging, Actor}
import redis.RedisClient

/**
 * Adds the ability to publish events.
 */
trait EventPublisher {
  this: Actor with ActorLogging =>

  implicit val actorSystem = context.system
  private val settings = KvsSettings(actorSystem)
  private val redis = RedisClient(settings.redisHostname, settings.redisPort)

  /**
   * Publishes the given event on the given channel, and also saves it in a list
   * of at most n items.
   * @param channel the channel or key for this event
   * @param event the event to publish
   * @param history number of previous events to keep in a list for reference (set to 0 for no history)
   */
  def publish(channel: String, event: Event, history: Int = KeyValueStore.defaultHistory): Unit = {
    // Serialize the event
    val formatter = implicitly[ByteStringFormatter[Event]]
    val bs = formatter.serialize(event) // only do this once

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
