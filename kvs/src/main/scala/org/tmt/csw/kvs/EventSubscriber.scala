package org.tmt.csw.kvs

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import redis.actors.RedisSubscriberActor
import java.net.InetSocketAddress
import redis.api.pubsub._


/**
 * Adds the ability to subscribe to events.
 * The subscribed actor wil receive Event messages for the given channel.
 */
trait EventSubscriber {
  this: Actor with ActorLogging =>

  private val settings = KvsSettings(context.system)

  val redis = context.actorOf(SubscribeActor.props(self, settings.redisHostname, settings.redisPort)
    .withDispatcher(SubscribeActor.dispatcherName))

  /**
   * Subscribes this actor to events with the given keys.
   * Each key may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param keys the top level keys for the events you want to subscribe to.
   */
  def subscribe(keys: String*): Unit = {
    val (patterns, channels) = keys.partition(_.endsWith("*"))
    if (patterns.size != 0) redis ! PSUBSCRIBE(patterns: _*)
    if (channels.size != 0) redis ! SUBSCRIBE(channels: _*)
  }

  /**
   * Unsubscribes this actor from events with the given keys.
   * Each key may be followed by a '*' wildcard to unsubscribe to all matching events.
   *
   * @param keys the top level keys for the events you want to unsubscribe from.
   */
  def unsubscribe(keys: String*): Unit = {
    val (patterns, channels) = keys.partition(_.endsWith("*"))
    if (patterns.size != 0) redis ! PUNSUBSCRIBE(patterns: _*)
    if (channels.size != 0) redis ! UNSUBSCRIBE(channels: _*)
  }
}

// -- Implementation --

private object SubscribeActor {
  def props(subscriber: ActorRef, redisHost: String, redisPort: Int): Props =
    Props(classOf[SubscribeActor], subscriber, redisHost, redisPort)

  val dispatcherName = "rediscala.rediscala-client-worker-dispatcher"
}

private class SubscribeActor(subscriber: ActorRef, redisHost: String, redisPort: Int)
  extends RedisSubscriberActor(new InetSocketAddress(redisHost, redisPort), List(), List()) {

  def onMessage(message: Message) {
    subscriber ! Event(message.data)
  }

  def onPMessage(pmessage: PMessage) {
    subscriber ! Event(pmessage.data)
  }
}


