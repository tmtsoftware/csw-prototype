package csw.services.kvs_old

import akka.actor.{ ActorRef, Props, ActorLogging, Actor }
import csw.util.cfg_old.Events.EventType
import redis.actors.{ DecodeReplies, RedisWorkerIO }
import java.net.InetSocketAddress
import redis.api.pubsub._
import akka.util.ByteString
import redis.protocol.{ MultiBulk, RedisReply }

/**
 * Adds the ability to subscribe to events.
 * The subscribed actor wil receive Event messages for the given channel.
 */
trait EventSubscriber {
  this: Actor with ActorLogging ⇒

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
    if (patterns.nonEmpty) redis ! PSUBSCRIBE(patterns: _*)
    if (channels.nonEmpty) redis ! SUBSCRIBE(channels: _*)
  }

  /**
   * Unsubscribes this actor from events with the given keys.
   * Each key may be followed by a '*' wildcard to unsubscribe to all matching events.
   *
   * @param keys the top level keys for the events you want to unsubscribe from.
   */
  def unsubscribe(keys: String*): Unit = {
    val (patterns, channels) = keys.partition(_.endsWith("*"))
    if (patterns.nonEmpty) redis ! PUNSUBSCRIBE(patterns: _*)
    if (channels.nonEmpty) redis ! UNSUBSCRIBE(channels: _*)
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

// The actor that receives the messages from Redis.
// Note we could extend RedisSubscriberActor, but I'm doing it this way, so we can
// customize the type of the message received if needed (RedisSubscriberActor forces Message(String)).
private class SubscribeActor(subscriber: ActorRef, redisHost: String, redisPort: Int)
    extends RedisWorkerIO(new InetSocketAddress(redisHost, redisPort), (b: Boolean) ⇒ ()) with DecodeReplies {

  /**
   * Keep states of channels and actor in case of connection reset
   */
  var channelsSubscribed = Set[String]()
  var patternsSubscribed = Set[String]()

  def writing: Receive = {
    case message: SubscribeMessage ⇒
      write(message.toByteString)
      message match {
        case s: SUBSCRIBE     ⇒ channelsSubscribed ++= s.channel
        case u: UNSUBSCRIBE   ⇒ channelsSubscribed --= u.channel
        case ps: PSUBSCRIBE   ⇒ patternsSubscribed ++= ps.pattern
        case pu: PUNSUBSCRIBE ⇒ patternsSubscribed --= pu.pattern
      }
  }

  def onConnectWrite(): ByteString = {
    ByteString.empty
  }

  def onConnectionClosed() {}

  def onWriteSent() {}

  def onDataReceived(dataByteString: ByteString) {
    decodeReplies(dataByteString)
  }

  def onDecodedReply(reply: RedisReply) {
    reply match {
      case MultiBulk(Some(list)) if list.length == 3 && list.head.toByteString.utf8String == "message" ⇒
        subscriber ! EventType(list(2).toByteString.toArray)
      case MultiBulk(Some(list)) if list.length == 4 && list.head.toByteString.utf8String == "pmessage" ⇒
        subscriber ! EventType(list(3).toByteString.toArray)
      case _ ⇒ // subscribe or psubscribe
    }
  }

  def onDataReceivedOnClosingConnection(dataByteString: ByteString): Unit = decodeReplies(dataByteString)

  def onClosingConnectionClosed(): Unit = {}
}

