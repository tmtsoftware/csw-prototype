package csw.services.events

import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import csw.services.events.EventService.EventMonitor
import csw.services.loc.{ComponentId, ComponentType, LocationService}
import csw.services.loc.Connection.TcpConnection
import csw.services.loc.LocationService.ResolvedTcpLocation
import csw.util.config.Events.StatusEvent
import redis.RedisClient

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object TelemetryService {
  /**
   * The default name that the Telemetry Service is registered with
   */
  val defaultName = "Telemetry Service"

  /**
   * Used to make the redis keys for the telemetry service unique
   */
  val defaultScope = "telem"

  /**
   * Returns the TelemetryService ComponentId for the given, or default name
   */
  def telemetryServiceComponentId(name: String = defaultName): ComponentId = ComponentId(name, ComponentType.Service)

  /**
   * Returns the TelemetryService connection for the given, or default name
   */
  def telemetryServiceConnection(name: String = defaultName): TcpConnection = TcpConnection(telemetryServiceComponentId(name))

  // Lookup the telemetry service redis instance with the location service
  private def locateTelemetryService(name: String = defaultName)(implicit system: ActorRefFactory, timeout: Timeout): Future[RedisClient] = {
    import system.dispatcher
    val connection = telemetryServiceConnection(name)
    LocationService.resolve(Set(connection)).map { locationsReady =>
      val loc = locationsReady.locations.head.asInstanceOf[ResolvedTcpLocation]
      RedisClient(loc.host, loc.port)
    }
  }

  /**
   * Looks up the Redis instance for the Telemetry Service with the Location Service
   * and then returns an TelemetryService instance using it.
   *
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param name name used to register the Redis instance with the Location Service (default: "Telemetry Service")
   * @return a new TelemetryService instance
   */
  def apply(name: String = defaultName)(implicit system: ActorRefFactory, timeout: Timeout): Future[TelemetryService] = {
    import system.dispatcher
    for {
      redisClient <- locateTelemetryService(name)
    } yield {
      TelemetryServiceImpl(redisClient)
    }
  }

  /**
   * Returns aan instancee of the TelemetryService (based on Redis)
   *
   * @param settings contains the host and port settings from reference.conf, or application.conf
   * @param _system  Akka env required for RedisClient
   */
  def apply(settings: EventServiceSettings)(implicit _system: ActorRefFactory): TelemetryService =
    get(settings.redisHostname, settings.redisPort)

  /**
   * Returns an TelemetryService instance using the Redis instance at the given host and port,
   * using the default "127.0.0.1:6379 if not given.
   *
   * @param host the Redis host name or IP address
   * @param port the Redis port
   * @return a new TelemetryService instance
   */
  def get(host: String = "127.0.0.1", port: Int = 6379)(implicit system: ActorRefFactory): TelemetryService = {
    val redisClient = RedisClient(host, port)
    TelemetryServiceImpl(redisClient)
  }

  // Converts a callback that takes an Telemetry to one that takes a StatusEvent
  private[events] def callbackConverter(telemCallback: StatusEvent => Unit)(event: Event): Unit =
    event match {
      case s: StatusEvent => telemCallback(s)
      case _              =>
    }
}

/**
 * API for publishing, getting and subscribing to telemetry (StatusEvent objects).
 */
trait TelemetryService {
  /**
   * Publishes the status event (key is based on the event's prefix)
   *
   * @param status  the value to store
   * @param history optional number of previous values to store
   * @return a future indicating if/when the operation has completed
   */
  def publish(status: StatusEvent, history: Int = 0)(implicit ec: ExecutionContext): Future[Unit]

  /**
   * Subscribes an actor to events matching the given prefixes
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param subscriber an actor to receive Event messages
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
   * @param prefixes   one or more prefixes of events, may include wildcard
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  def subscribe(subscriber: ActorRef, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): EventMonitor

  /**
   * Subscribes a callback function to events matching the given prefixes
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param callback an callback which will be called with Event objects (in another thread)
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
   * @param prefixes one or more prefixes of events, may include wildcard
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  def subscribe(callback: StatusEvent => Unit, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): EventMonitor

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

  /**
   * Gets the value for the given status event prefix
   *
   * @param prefix the prefix (key) for the event to get
   * @return the status event, if (and when) found
   */
  def get(prefix: String)(implicit _system: ActorRefFactory): Future[Option[StatusEvent]]

  /**
   * Gets a list of the n most recent status event values for the given prefix
   *
   * @param prefix the status event's prefix
   * @param n      the max number of values to get
   * @return future sequence of status events, ordered by most recent
   */
  def getHistory(prefix: String, n: Int): Future[Seq[StatusEvent]]

  /**
   * Deletes the given  status event from the store
   *
   * @return a future indicating if/when the operation has completed
   */
  def delete(prefix: String)(implicit ec: ExecutionContext): Future[Unit]
}

/**
 * A class for publishing, getting and subscribing to telemetry (StatusEvent objects).
 *
 * @param redisClient used to talk to Redis
 */
case class TelemetryServiceImpl(redisClient: RedisClient) extends TelemetryService {

  import TelemetryService._

  private val eventService = EventServiceImpl(redisClient, defaultScope)

  override def publish(status: StatusEvent, history: Int = 0)(implicit ec: ExecutionContext): Future[Unit] =
    eventService.publish(status, history)

  override def subscribe(subscriber: ActorRef, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): EventMonitor =
    eventService.subscribe(subscriber, postLastEvents, prefixes: _*)

  override def subscribe(callback: StatusEvent => Unit, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): EventMonitor =
    eventService.subscribe(callbackConverter(callback) _, postLastEvents, prefixes: _*)

  override def get(prefix: String)(implicit _system: ActorRefFactory): Future[Option[StatusEvent]] =
    eventService.get(prefix).mapTo[Option[StatusEvent]]

  override def getHistory(prefix: String, n: Int): Future[Seq[StatusEvent]] =
    eventService.getHistory(prefix, n).mapTo[Seq[StatusEvent]]

  override def delete(prefix: String)(implicit ec: ExecutionContext): Future[Unit] = eventService.delete(prefix).map(_ => ())
}

/**
 * Provides a blocking, synchronous API to the telemetry service.
 *
 * @param ts      the underlying async telemetry service to use
 * @param timeout max amount of time to wait for a result before timing out
 */
case class BlockingTelemetryService(ts: TelemetryService, timeout: Duration) {

  /**
   * Publishes the value for the status event (key is based on the event's prefix)
   *
   * @param status  the value to store
   * @param history optional number of previous values to store
   */
  def publish(status: StatusEvent, history: Int = 0)(implicit ec: ExecutionContext): Unit =
    Await.result(ts.publish(status, history), timeout)

  /**
   * Subscribes an actor to events matching the given prefixes
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param subscriber an actor to receive Event messages
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
   * @param prefixes   one or more prefixes of events, may include wildcard
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  def subscribe(subscriber: ActorRef, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): EventMonitor =
    ts.subscribe(subscriber, postLastEvents, prefixes: _*)

  /**
   * Subscribes an actor or callback function to events matching the given prefixes
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param callback an callback which will be called with Event objects (in another thread)
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
   * @param prefixes one or more prefixes of events, may include wildcard
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  def subscribe(callback: StatusEvent => Unit, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): EventMonitor =
    ts.subscribe(callback, postLastEvents, prefixes: _*)

  /**
   * Gets the value for the given status event prefix
   *
   * @param prefix the prefix (key) for the event to get
   * @return the status event, if found
   */
  def get(prefix: String)(implicit _system: ActorRefFactory): Option[StatusEvent] =
    Await.result(ts.get(prefix), timeout)

  /**
   * Gets a list of the n most recent status event values for the given prefix
   *
   * @param prefix the status event's prefix
   * @param n      the max number of values to get
   * @return sequence of status events, ordered by most recent
   */
  def getHistory(prefix: String, n: Int): Seq[StatusEvent] =
    Await.result(ts.getHistory(prefix, n), timeout)

  /**
   * Deletes the given  status event from the store
   */
  def delete(prefix: String)(implicit ec: ExecutionContext): Unit =
    Await.result(ts.delete(prefix), timeout)
}

