package csw.services.events

import akka.actor.{AbstractActor, Actor, ActorLogging, ActorRef, Props}
import redis.actors.{DecodeReplies, RedisWorkerIO}
import java.net.InetSocketAddress

import redis.api.pubsub._
import akka.util.ByteString
import redis.ByteStringFormatter
import redis.protocol.{MultiBulk, RedisReply}

import scala.annotation.varargs
import EventServiceImpl._

/**
 * Adds the ability to an actor to subscribe to events from the event service.
 * The subscribed actor will receive messages of type Event for the given event prefixes.
 */
private[events] abstract class EventSubscriber(redisHost: String, redisPort: Int) extends Actor with ActorLogging {

  private lazy val redis = context.actorOf(SubscribeActor.props(self, redisHost, redisPort)
    .withDispatcher(SubscribeActor.dispatcherName))

  /**
   * Subscribes this actor to events with the given prefixes.
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param prefixes the top level keys for the events you want to subscribe to.
   */
  def subscribe(prefixes: String*): Unit = {
    val (patterns, channels) = prefixes.partition(_.endsWith("*"))
    if (patterns.nonEmpty) redis ! PSUBSCRIBE(patterns: _*)
    if (channels.nonEmpty) redis ! SUBSCRIBE(channels: _*)
  }

  /**
   * Unsubscribes this actor from events with the given prefixes.
   * Each prefix may be followed by a '*' wildcard to unsubscribe to all matching events.
   *
   * @param prefixes the top level keys for the events you want to unsubscribe from.
   */
  def unsubscribe(prefixes: String*): Unit = {
    val (patterns, channels) = prefixes.partition(_.endsWith("*"))
    if (patterns.nonEmpty) redis ! PUNSUBSCRIBE(patterns: _*)
    if (channels.nonEmpty) redis ! UNSUBSCRIBE(channels: _*)
  }
}

/**
 * Helper class For Java API: Adds the ability to subscribe to events.
 * The subscribed actor will receive messages of type Event for the given prefixes.
 */
abstract class JAbstractSubscriber(redisHost: String, redisPort: Int) extends AbstractActor {

  private lazy val redis = context.actorOf(SubscribeActor.props(self, redisHost, redisPort)
    .withDispatcher(SubscribeActor.dispatcherName))

  /**
   * Subscribes this actor to values with the given prefixes.
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param prefixes the top level prefixes for the events you want to subscribe to.
   */
  @varargs
  def subscribe(prefixes: String*): Unit = {
    val (patterns, channels) = prefixes.partition(_.endsWith("*"))
    if (patterns.nonEmpty) redis ! PSUBSCRIBE(patterns: _*)
    if (channels.nonEmpty) redis ! SUBSCRIBE(channels: _*)
  }

  /**
   * Unsubscribes this actor from events with the given prefixes.
   * Each prefix may be followed by a '*' wildcard to unsubscribe to all matching values.
   *
   * @param prefixes the top level prefixes for the events you want to unsubscribe from.
   */
  @varargs
  def unsubscribe(prefixes: String*): Unit = {
    val (patterns, channels) = prefixes.partition(_.endsWith("*"))
    if (patterns.nonEmpty) redis ! PUNSUBSCRIBE(patterns: _*)
    if (channels.nonEmpty) redis ! UNSUBSCRIBE(channels: _*)
  }
}

// -- Implementation --

private object SubscribeActor {
  def props(subscriber: ActorRef, redisHost: String, redisPort: Int): Props =
    Props(new SubscribeActor(subscriber, redisHost, redisPort))

  val dispatcherName = "rediscala.rediscala-client-worker-dispatcher"
}

// The actor that receives the messages from Redis.
// Note we could extend RedisSubscriberActor, but I'm doing it this way, so we can
// customize the type of the message received if needed (RedisSubscriberActor forces Message(String)).
private class SubscribeActor(subscriber: ActorRef, redisHost: String, redisPort: Int)
    extends RedisWorkerIO(new InetSocketAddress(redisHost, redisPort), (_: Boolean) => ()) with DecodeReplies {

  /**
   * Keep states of channels and actor in case of connection reset
   */
  private var channelsSubscribed = Set[String]()
  private var patternsSubscribed = Set[String]()

  override def preStart() {
    super.preStart()
    if (channelsSubscribed.nonEmpty) {
      write(SUBSCRIBE(channelsSubscribed.toSeq: _*).toByteString)
      log.debug(s"Subscribed to channels ${channelsSubscribed.mkString(", ")}")
    }
    if (patternsSubscribed.nonEmpty) {
      write(PSUBSCRIBE(patternsSubscribed.toSeq: _*).toByteString)
      log.debug(s"Subscribed to patterns ${patternsSubscribed.mkString(", ")}")
    }
  }

  override def writing: Receive = {
    case message: SubscribeMessage =>
      write(message.toByteString)
      message match {
        case s: SUBSCRIBE     => channelsSubscribed ++= s.channel
        case u: UNSUBSCRIBE   => channelsSubscribed --= u.channel
        case ps: PSUBSCRIBE   => patternsSubscribed ++= ps.pattern
        case pu: PUNSUBSCRIBE => patternsSubscribed --= pu.pattern
      }
  }

  override def onConnectWrite(): ByteString = {
    ByteString.empty
  }

  override def onConnectionClosed(): Unit = {
    log.debug("Connection to Redis closed")
  }

  override def onWriteSent() {}

  override def onDataReceived(dataByteString: ByteString) {
    decodeReplies(dataByteString)
  }

  override def onDecodedReply(reply: RedisReply) {
    val formatter = implicitly[ByteStringFormatter[Event]]

    reply match {
      case MultiBulk(Some(list)) if list.length == 3 && list.head.toByteString.utf8String == "message" =>
        subscriber ! formatter.deserialize(list(2).toByteString)
      case MultiBulk(Some(list)) if list.length == 4 && list.head.toByteString.utf8String == "pmessage" =>
        subscriber ! formatter.deserialize(list(3).toByteString)
      case _ => // subscribe or psubscribe
    }
  }

  override def onDataReceivedOnClosingConnection(dataByteString: ByteString): Unit = decodeReplies(dataByteString)

  override def onClosingConnectionClosed(): Unit = {}
}

