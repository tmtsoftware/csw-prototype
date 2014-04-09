package org.tmt.csw.kvs

import redis.{ByteStringFormatter, RedisClient}
import akka.actor.{ActorLogging, Actor}
import akka.util.ByteString

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
   * @param history number of previous events to keep in a list for reference
   */
  def publish(channel: String, event: Event, history: Int = 6): Unit = {
    // Serialize the event
    val formatter = implicitly[ByteStringFormatter[Event]]
    val bs = formatter.serialize(event) // only do this once

    // Use a transaction to send all commands at once
    // XXX TODO check future return values and log errors
    val redisTransaction = redis.transaction()
    redisTransaction.watch(channel)
    redisTransaction.lpush(channel, bs)
    redisTransaction.ltrim(channel, 0, history)
    redisTransaction.publish(channel, bs)
    redisTransaction.exec()
  }

  // temp test
  def tmpPublish(channel: String, bs: ByteString, history: Int = 6): Unit = {
    val redisTransaction = redis.transaction()
    redisTransaction.watch(channel)
    redisTransaction.lpush(channel, bs)
    redisTransaction.ltrim(channel, 0, history)
    redisTransaction.publish(channel, bs)
    redisTransaction.exec()
  }

}
