package csw.services.events

import akka.util.Timeout
import csw.services.loc.Connection.TcpConnection
import csw.services.loc.LocationService.ResolvedTcpLocation
import csw.services.loc.{ComponentId, ComponentType, LocationService}
import akka.actor.{ActorRef, ActorRefFactory, PoisonPill, Props}
import akka.util.ByteString
import csw.services.events.EventService.EventMonitor
import csw.util.config.ConfigSerializer._
import redis.{ByteStringFormatter, RedisClient}

import scala.concurrent.Future

object EventService {

  /**
   * The default name that the Event Service is registered with
   */
  val defaultName = "Event Service"

  // Lookup the event service redis instance with the location service
  private def locateEventService(name: String = defaultName)(implicit system: ActorRefFactory, timeout: Timeout): Future[RedisClient] = {
    import system.dispatcher
    val connection = TcpConnection(ComponentId(name, ComponentType.Service))
    LocationService.resolve(Set(connection)).map { locationsReady =>
      val loc = locationsReady.locations.head.asInstanceOf[ResolvedTcpLocation]
      RedisClient(loc.host, loc.port)
    }
  }

  /**
   * Looks up the Redis instance for the Event Service with the Location Service
   * and then returns an EventService instance using it.
   *
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param name      name used to register the Redis instance with the Location Service (default: "Event Service")
   * @return a new EventService instance
   */
  def apply(name: String = defaultName)(implicit system: ActorRefFactory, timeout: Timeout): Future[EventService] = {
    import system.dispatcher
    for {
      redisClient <- locateEventService(name)
    } yield {
      EventServiceImpl(redisClient)
    }
  }

  /**
   * Returns a concrete implementation of the EventService trait (based on Redis)
   *
   * @param settings contains the host and port settings from reference.conf, or application.conf
   * @param _system  Akka env required for RedisClient
   */
  def apply(settings: EventServiceSettings)(implicit _system: ActorRefFactory): EventService =
    get(settings.redisHostname, settings.redisPort)

  /**
   * Returns an EventService instance using the Redis instance at the given host and port,
   * using the default "127.0.0.1:6379 if not given.
   *
   * @param host        the Redis host name or IP address
   * @param port        the Redis port
   * @return a new EventService instance
   */
  def get(host: String = "127.0.0.1", port: Int = 6379)(implicit system: ActorRefFactory): EventService = {
    val redisClient = RedisClient(host, port)
    EventServiceImpl(redisClient)
  }

  /**
   * Type of return value from the subscribe method
   */
  trait EventMonitor {
    /**
     * Stops the subscribing actor
     */
    def stop(): Unit

    /**
     * A reference to the subscribing actor (could be used to watch the actor to detect if it stops for some reason)
     *
     * @return
     */
    def actorRef: ActorRef
  }
}

/**
 * The interface of a key value store.
 */
trait EventService {

  import EventService._

  /**
   * Publishes the given event
   *
   * @param event   the event to publish
   * @return the future result (indicates if and when the operation completed, may be ignored)
   */
  def publish(event: Event): Future[Unit]

  /**
   * Subscribes an actor or callback function to events matching the given prefixes
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param subscriber an optional actor to receive Event messages
   * @param callback   an optional callback which will be called with Event objects (in another thread)
   * @param prefixes   one or more prefixes of events, may include wildcard
   */
  def subscribe(subscriber: Option[ActorRef], callback: Option[Event => Unit], prefixes: String*): EventMonitor
}

private[events] object EventServiceImpl {
  // Implicit conversion between ByteString and Event, for the Redis API
  implicit val eventFormatter = new ByteStringFormatter[Event] {
    def serialize(e: Event): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): Event = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[Event](ar)
    }
  }

  // Implement value returned from subscribe method
  private[events] case class EventMonitorImpl(actorRef: ActorRef) extends EventMonitor {
    override def stop(): Unit = {
      actorRef ! PoisonPill
    }
  }

  // Actor used to subscribe to events for given prefixes and then notify the actor or call the function
  private object EventMonitorActor {
    def props(subscriber: Option[ActorRef], callback: Option[Event => Unit],
              currentEvents: Future[Seq[Event]], prefixes: String*): Props =
      Props(classOf[EventMonitorActor], subscriber, callback, currentEvents: Future[Seq[Event]], prefixes)
  }

  private class EventMonitorActor(subscriber: Option[ActorRef], callback: Option[Event => Unit],
                                  currentEvents: Future[Seq[Event]], prefixes: String*) extends EventSubscriber {
    import context.dispatcher

    // First send the subscribers the current values for the events
    currentEvents.onSuccess {
      case events => events.foreach(notifySubscribers)
    }
    // Then subscribe to future events
    currentEvents.onComplete(_ => subscribe(prefixes: _*))

    override def postStop(): Unit = {
      unsubscribe(prefixes: _*)
    }

    def receive: Receive = {
      case event: Event => notifySubscribers(event)
    }

    private def notifySubscribers(event: Event): Unit = {
      subscriber.foreach(_ ! event)
      callback.foreach { f =>
        Future {
          f(event)
        }.onFailure {
          case ex => log.error("Event callback failed: ", ex)
        }
      }
    }
  }
}

/**
 * An implementation of the EventService trait based on Redis.
 *
 * @param redisClient used to talk to Redis
 * @param _system Akka env required by RedisClient
 */
private[events] case class EventServiceImpl(redisClient: RedisClient)(implicit _system: ActorRefFactory) extends EventService {
  import EventServiceImpl._

  implicit val execContext = _system.dispatcher

  override def publish(event: Event): Future[Unit] = publish(event, 0)

  // Publishes the event and keeps the given number of previous values
  def publish(event: Event, history: Int): Future[Unit] = {
    // Serialize the event
    val formatter = implicitly[ByteStringFormatter[Event]]
    val bs = formatter.serialize(event) // only do this once
    val h = if (history >= 0) history else 0
    // Use a transaction to send all commands at once
    val redisTransaction = redisClient.transaction()
    val key = event.prefix
    redisTransaction.watch(key)
    val f1 = redisTransaction.lpush(key, bs)
    val f2 = redisTransaction.ltrim(key, 0, h + 1)
    val f3 = redisTransaction.publish(key, bs)
    val f4 = redisTransaction.exec()
    Future.sequence(List(f1, f2, f3, f4)).map(_ => ())
  }

  override def subscribe(subscriber: Option[ActorRef], callback: Option[Event => Unit], prefixes: String*): EventMonitor = {
    val currentEvents = Future.sequence(prefixes.map(get)).map(_.flatten)
    EventMonitorImpl(_system.actorOf(EventMonitorActor.props(subscriber, callback, currentEvents, prefixes: _*)))
  }

  // gets the current value for the given prefix
  def get(prefix: String): Future[Option[Event]] = redisClient.lindex(prefix, 0)

  // Gets the last n values for the given prefix
  def getHistory(prefix: String, n: Int): Future[Seq[Event]] = redisClient.lrange(prefix, 0, n - 1)

  // deletes the saved value for the given prefix
  def delete(prefix: String*): Future[Long] = redisClient.del(prefix: _*)
}

