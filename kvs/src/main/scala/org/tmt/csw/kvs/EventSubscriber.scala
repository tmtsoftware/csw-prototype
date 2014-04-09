package org.tmt.csw.kvs

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import redis.actors.{DecodeReplies, RedisWorkerIO}
import java.net.InetSocketAddress
import redis.api.pubsub._
import akka.util.ByteString
import redis.api.connection.Auth
import redis.protocol.{MultiBulk, RedisReply}


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

  case class RedisMessage(channel: String, data: ByteString)

  case class RedisPMessage(patternMatched: String, channel: String, data: ByteString)

}

private class SubscribeActor(subscriber: ActorRef, redisHost: String, redisPort: Int)
  extends RedisWorkerIO(new InetSocketAddress(redisHost, redisPort)) with DecodeReplies {
//  extends RedisSubscriberActor(new InetSocketAddress(redisHost, redisPort), List(), List()) {

  import SubscribeActor._

  // XXX temp
  val channels: Seq[String] = List()
  val patterns: Seq[String] = List()
  val authPassword = None // XXX temp

  /**
   * Keep states of channels and actor in case of connection reset
   */
  var channelsSubscribed = channels.toSet
  var patternsSubscribed = patterns.toSet

  def writing: Receive = {
    case message: SubscribeMessage =>
      write(message.toByteString)
      message match {
        case s: SUBSCRIBE => channelsSubscribed ++= s.channel
        case u: UNSUBSCRIBE => channelsSubscribed --= u.channel
        case ps: PSUBSCRIBE => patternsSubscribed ++= ps.pattern
        case pu: PUNSUBSCRIBE => patternsSubscribed --= pu.pattern
      }
  }

  def onConnectWrite(): ByteString = {
//    authPassword.map(Auth(_).encodedRequest).getOrElse(ByteString.empty)
    ByteString.empty
  }

  def onConnectionClosed() {}

  def onWriteSent() {}

  def onDataReceived(dataByteString: ByteString) {
    decodeReplies(dataByteString)
  }

  def onDecodedReply(reply: RedisReply) {
    reply match {
      case MultiBulk(Some(list)) if list.length == 3 && list.head.toByteString.utf8String == "message" =>
        onMessage(RedisMessage(list(1).toByteString.utf8String, list(2).toByteString))
      case MultiBulk(Some(list)) if list.length == 4 && list.head.toByteString.utf8String == "pmessage" =>
        onPMessage(RedisPMessage(list(1).toByteString.utf8String, list(2).toByteString.utf8String, list(3).toByteString))
      case _ => // subscribe or psubscribe
    }
  }

  def onDataReceivedOnClosingConnection(dataByteString: ByteString): Unit = decodeReplies(dataByteString)

  def onClosingConnectionClosed(): Unit = {}

  def onMessage(message: RedisMessage) {
    subscriber ! Event(message.data.utf8String)
//    subscriber ! message.data // XXX temp
  }

  def onPMessage(pmessage: RedisPMessage) {
    subscriber ! Event(pmessage.data.utf8String)
//    subscriber ! pmessage.data // XXX temp
  }
}


