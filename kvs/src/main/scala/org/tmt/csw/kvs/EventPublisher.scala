package org.tmt.csw.kvs

import redis.RedisClient
import akka.actor.{ActorLogging, Actor}

/**
 * Adds the ability to publish events.
 */
trait EventPublisher {
  this: Actor with ActorLogging =>

  implicit val actorSystem = context.system
  private val settings = KvsSettings(actorSystem)
  private val redis = RedisClient(settings.redisHostname, settings.redisPort)

  def publish(channel: String, event: Event): Unit = {
    redis.publish(channel, event.toJson)
  }
}
