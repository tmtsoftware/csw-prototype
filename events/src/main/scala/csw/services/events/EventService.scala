package csw.services.events

import akka.util.Timeout
import csw.services.loc.Connection.TcpConnection
import csw.services.loc.LocationService.ResolvedTcpLocation
import csw.services.loc.{ComponentId, ComponentType, LocationService}
import akka.actor.{ActorRef, ActorRefFactory, ActorSystem, PoisonPill, Props}
import akka.util.ByteString
import csw.services.events.EventService.EventMonitor
import csw.util.config.ConfigSerializer._
import redis.{ByteStringFormatter, RedisClient}

import scala.annotation.varargs
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

object EventService {

  /**
   * The default name that the Event Service is registered with
   */
  val defaultName = "Event Service"

  /**
   * Used to make the redis keys for the event service unique
   */
  val defaultScope = "event"

  /**
   * Returns the EventService ComponentId for the given, or default name
   */
  def eventServiceComponentId(name: String = defaultName): ComponentId = ComponentId(name, ComponentType.Service)

  /**
   * Returns the EventService connection for the given, or default name
   */
  def eventServiceConnection(name: String = defaultName): TcpConnection = TcpConnection(eventServiceComponentId(name))

  // Lookup the event service redis instance with the location service
  private def locateEventService(name: String = defaultName)(implicit system: ActorSystem, timeout: Timeout): Future[RedisClient] = {
    import system.dispatcher
    val connection = eventServiceConnection(name)
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
   * @param name name used to register the Redis instance with the Location Service (default: "Event Service")
   * @return a new EventService instance
   */
  def apply(name: String = defaultName)(implicit system: ActorSystem, timeout: Timeout): Future[EventService] = {
    import system.dispatcher
    for {
      redisClient <- locateEventService(name)
    } yield {
      EventServiceImpl(redisClient, defaultScope)
    }
  }

  /**
   * Returns a concrete implementation of the EventService trait (based on Redis)
   *
   * @param settings contains the host and port settings from reference.conf, or application.conf
   * @param _system  Akka env required for RedisClient
   */
  def apply(settings: EventServiceSettings)(implicit _system: ActorSystem): EventService =
    get(settings.redisHostname, settings.redisPort)

  /**
   * Returns an EventService instance using the Redis instance at the given host and port,
   * using the default "127.0.0.1:6379 if not given.
   *
   * @param host the Redis host name or IP address
   * @param port the Redis port
   * @return a new EventService instance
   */
  def get(host: String = "127.0.0.1", port: Int = 6379)(implicit system: ActorSystem): EventService = {
    val redisClient = RedisClient(host, port)
    EventServiceImpl(redisClient, defaultScope)
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
     * Adds events matching the given prefixes to the current subscription.
     * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
     *
     * @param prefixes one or more prefixes of events, may include wildcard
     */
    def subscribe(prefixes: String*): Unit

    /**
     * Adds events matching the given prefix to the current subscription.
     * The prefix may be followed by a '*' wildcard to subscribe to all matching events.
     *
     * @param prefix one or more prefixes of events, may include wildcard
     */
    def subscribeTo(prefix: String): Unit = subscribe(prefix) // Note: Needed to avoid varargs issues in Java

    /**
     * Ubsubscribes from events matching the given prefixes.
     *
     * @param prefixes one or more prefixes of events, may include wildcard
     */
    def unsubscribe(prefixes: String*): Unit

    /**
     * Ubsubscribes from events matching the given prefix.
     *
     * @param prefix one or more prefixes of events, may include wildcard
     */
    def unsubscribeFrom(prefix: String): Unit = unsubscribe(prefix) // Note: Needed to avoid varargs issues in Java

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
   * @param event the event to publish
   * @return the future result (indicates if and when the operation completed, may be ignored)
   */
  def publish(event: Event)(implicit ec: ExecutionContext): Future[Unit]

  /**
   * Subscribes an actor to events matching the given prefixes.
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   * An actor is created to manage the subscriptions and the EventMonitor return value can be used to
   * stop the actor or change the prefixes subscribed to.
   *
   * @param subscriber an actor to receive Event messages
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events first
   * @param prefixes   one or more prefixes of events, may include wildcard
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  @varargs
  def subscribe(subscriber: ActorRef, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): EventMonitor

  /**
   * Subscribes a callback function to events matching the given prefixes.
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   * An actor is created to manage the subscriptions and the EventMonitor return value can be used to
   * stop the actor or change the prefixes subscribed to.
   *
   * @param callback   an callback which will be called with Event objects (in another thread)
   * @param postLastEvents if true, the callback receives the last known values of any subscribed events first
   * @param prefixes   one or more prefixes of events, may include wildcard
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  @varargs
  def subscribe(callback: Event => Unit, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): EventMonitor

  /**
   * Creates an EventMonitorActor and subscribes the given actor to it.
   * The return value can be used to stop the actor or subscribe and unsubscribe to events.
   *
   * @param subscriber an actor to receive Event messages
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events first
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  def createEventMonitor(subscriber: ActorRef, postLastEvents: Boolean)(implicit _system: ActorRefFactory): EventMonitor = subscribe(subscriber, postLastEvents)

  /**
   * Creates an EventMonitorActor and subscribes the given actor to it.
   * The return value can be used to stop the actor or subscribe and unsubscribe to events.
   *
   * @param callback   an callback which will be called with Event objects (in another thread)
   * @param postLastEvents if true, the callback receives the last known values of any subscribed events first
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  def createEventMonitor(callback: Event => Unit, postLastEvents: Boolean)(implicit _system: ActorRefFactory): EventMonitor = subscribe(callback, postLastEvents)
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
  private[events] case class EventMonitorImpl(actorRef: ActorRef, scope: String) extends EventMonitor {
    import EventMonitorActor._

    private def scopedKey(key: String) = {
      if (key.startsWith(scope)) key else s"$scope:$key"
    }

    override def stop(): Unit = {
      actorRef ! PoisonPill
    }

    override def subscribe(prefixes: String*): Unit = {
      actorRef ! Subscribe(prefixes.map(scopedKey): _*)
    }

    override def unsubscribe(prefixes: String*): Unit = {
      actorRef ! Unsubscribe(prefixes.map(scopedKey): _*)
    }
  }

  // Actor used to subscribe to events for given prefixes and then notify the actor or call the function
  private object EventMonitorActor {
    def props(
      subscriber:     Option[ActorRef],
      callback:       Option[Event => Unit],
      eventService:   EventServiceImpl,
      postLastEvents: Boolean
    ): Props =
      Props(classOf[EventMonitorActor], subscriber, callback, eventService, postLastEvents)

    // Message sent to subscribe to more prefixes
    case class Subscribe(prefixes: String*)

    // Message sent to unsubscribe to prefixes
    case class Unsubscribe(prefixes: String*)

  }

  private class EventMonitorActor(
      subscriber:     Option[ActorRef],
      callback:       Option[Event => Unit],
      eventService:   EventServiceImpl,
      postLastEvents: Boolean
  ) extends EventSubscriber(eventService.redisClient.host, eventService.redisClient.port) {

    import context.dispatcher
    import EventMonitorActor._

    def receive: Receive = {
      case event: Event =>
        notifySubscribers(event)

      case s: Subscribe =>
        if (postLastEvents) {
          // Notify the subscriber of the current event values
          for {
            currentEvents <- Future.sequence(s.prefixes.map(eventService.get)).map(_.flatten)
          } {
            currentEvents.foreach(notifySubscribers)
            subscribe(s.prefixes: _*)
          }
        } else {
          // Just subscribe to future values
          subscribe(s.prefixes: _*)
        }

      case u: Unsubscribe =>
        unsubscribe(u.prefixes: _*)
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
 * @param scope a string used to make the keys unique for this class (for example: "event")
 */
private[events] case class EventServiceImpl(redisClient: RedisClient, scope: String) extends EventService {

  import EventServiceImpl._

  private def scopedKey(key: String) = {
    if (key.startsWith(scope)) key else s"$scope:$key"
  }

  override def publish(event: Event)(implicit ec: ExecutionContext): Future[Unit] = {
    // Serialize the event
    val formatter = implicitly[ByteStringFormatter[Event]]
    val bs = formatter.serialize(event)
    val key = scopedKey(event.prefix)
    Future.sequence(List(redisClient.publish(key, bs), redisClient.set(key, bs))).map(_ => ())
  }

  override def subscribe(subscriber: ActorRef, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): EventMonitor = {
    val actorRef = _system.actorOf(EventMonitorActor.props(Some(subscriber), None, this, postLastEvents))
    val monitor = EventMonitorImpl(actorRef, scope)
    monitor.subscribe(prefixes.map(scopedKey): _*)
    monitor
  }

  override def subscribe(callback: Event => Unit, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): EventMonitor = {
    val actorRef = _system.actorOf(EventMonitorActor.props(None, Some(callback), this, postLastEvents))
    val monitor = EventMonitorImpl(actorRef, scope)
    monitor.subscribe(prefixes.map(scopedKey): _*)
    monitor
  }

  // gets the current value for the given prefix
  def get(prefix: String): Future[Option[Event]] = redisClient.get(scopedKey(prefix))
}
