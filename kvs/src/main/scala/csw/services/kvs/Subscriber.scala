package csw.services.kvs

import akka.actor.{AbstractActor, Actor, ActorLogging, ActorRef, Props}
import csw.services.kvs.KeyValueStore.KvsFormatter
import redis.actors.{DecodeReplies, RedisWorkerIO}
import java.net.InetSocketAddress

import redis.api.pubsub._
import akka.util.ByteString
import redis.protocol.{MultiBulk, RedisReply}

import scala.annotation.varargs

/**
 * Adds the ability to subscribe to objects of type T.
 * The subscribed actor will receive messages of type T for the given keys.
 */
abstract class Subscriber[T: KvsFormatter] extends Actor with ActorLogging {

  private val settings = KvsSettings(context.system)

  private lazy val redis = context.actorOf(SubscribeActor.props[T](self, settings.redisHostname, settings.redisPort)
    .withDispatcher(SubscribeActor.dispatcherName))

  /**
   * Subscribes this actor to values with the given keys.
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
   * Unsubscribes this actor from values with the given keys.
   * Each key may be followed by a '*' wildcard to unsubscribe to all matching values.
   *
   * @param keys the top level keys for the events you want to unsubscribe from.
   */
  def unsubscribe(keys: String*): Unit = {
    val (patterns, channels) = keys.partition(_.endsWith("*"))
    if (patterns.nonEmpty) redis ! PUNSUBSCRIBE(patterns: _*)
    if (channels.nonEmpty) redis ! UNSUBSCRIBE(channels: _*)
  }
}

/**
 * Helper class For Java API: Adds the ability to subscribe to objects of type T.
 * The subscribed actor will receive messages of type T for the given keys.
 */
abstract class JAbstractSubscriber[T: KvsFormatter] extends AbstractActor {

  private val settings = KvsSettings(context.system)

  private lazy val redis = context.actorOf(SubscribeActor.props[T](self, settings.redisHostname, settings.redisPort)
    .withDispatcher(SubscribeActor.dispatcherName))

  /**
   * Subscribes this actor to values with the given keys.
   * Each key may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param keys the top level keys for the events you want to subscribe to.
   */
  @varargs
  def subscribe(keys: String*): Unit = {
    val (patterns, channels) = keys.partition(_.endsWith("*"))
    if (patterns.nonEmpty) redis ! PSUBSCRIBE(patterns: _*)
    if (channels.nonEmpty) redis ! SUBSCRIBE(channels: _*)
  }

  /**
   * Unsubscribes this actor from values with the given keys.
   * Each key may be followed by a '*' wildcard to unsubscribe to all matching values.
   *
   * @param keys the top level keys for the events you want to unsubscribe from.
   */
  @varargs
  def unsubscribe(keys: String*): Unit = {
    val (patterns, channels) = keys.partition(_.endsWith("*"))
    if (patterns.nonEmpty) redis ! PUNSUBSCRIBE(patterns: _*)
    if (channels.nonEmpty) redis ! UNSUBSCRIBE(channels: _*)
  }
}

// -- Implementation --

private object SubscribeActor {
  def props[T: KvsFormatter](subscriber: ActorRef, redisHost: String, redisPort: Int): Props =
    Props(new SubscribeActor[T](subscriber, redisHost, redisPort))

  val dispatcherName = "rediscala.rediscala-client-worker-dispatcher"
}

// The actor that receives the messages from Redis.
// Note we could extend RedisSubscriberActor, but I'm doing it this way, so we can
// customize the type of the message received if needed (RedisSubscriberActor forces Message(String)).
private class SubscribeActor[T: KvsFormatter](subscriber: ActorRef, redisHost: String, redisPort: Int)
    extends RedisWorkerIO(new InetSocketAddress(redisHost, redisPort), (b: Boolean) ⇒ ()) with DecodeReplies {

  /**
   * Keep states of channels and actor in case of connection reset
   */
  private var channelsSubscribed = Set[String]()
  private var patternsSubscribed = Set[String]()

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
    val formatter = implicitly[KvsFormatter[T]]

    reply match {
      case MultiBulk(Some(list)) if list.length == 3 && list.head.toByteString.utf8String == "message" ⇒
        subscriber ! formatter.deserialize(list(2).toByteString)
      case MultiBulk(Some(list)) if list.length == 4 && list.head.toByteString.utf8String == "pmessage" ⇒
        subscriber ! formatter.deserialize(list(3).toByteString)
      case _ ⇒ // subscribe or psubscribe
    }
  }

  def onDataReceivedOnClosingConnection(dataByteString: ByteString): Unit = decodeReplies(dataByteString)

  def onClosingConnectionClosed(): Unit = {}
}

