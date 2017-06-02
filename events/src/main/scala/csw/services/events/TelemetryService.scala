package csw.services.events

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem, PoisonPill, Props, Terminated}
import akka.util.{ByteString, Timeout}
import csw.services.events.TelemetryService.TelemetryMonitor
import csw.services.loc.{ComponentId, ComponentType, LocationService}
import csw.services.loc.Connection.TcpConnection
import csw.services.loc.LocationService.ResolvedTcpLocation
import csw.util.itemSet.ItemSetSerializer.{read, write}
import csw.util.itemSet.Events.StatusEvent
import redis.{ByteStringFormatter, RedisClient}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Failure

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
  private def locateTelemetryService(name: String = defaultName)(implicit system: ActorSystem, timeout: Timeout): Future[RedisClient] = {
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
  def apply(name: String = defaultName)(implicit system: ActorSystem, timeout: Timeout): Future[TelemetryService] = {
    import system.dispatcher
    for {
      redisClient <- locateTelemetryService(name)
    } yield {
      TelemetryServiceImpl(redisClient, defaultScope)
    }
  }

  /**
   * Returns a concrete implementation of the TelemetryService trait (based on Redis)
   *
   * @param settings contains the host and port settings from reference.conf, or application.conf
   * @param _system  Akka env required for RedisClient
   */
  def apply(settings: EventServiceSettings)(implicit _system: ActorSystem): TelemetryService =
    get(settings.redisHostname, settings.redisPort)

  /**
   * Returns an TelemetryService instance using the Redis instance at the given host and port,
   * using the default "127.0.0.1:6379 if not given.
   *
   * @param host the Redis host name or IP address
   * @param port the Redis port
   * @return a new TelemetryService instance
   */
  def get(host: String = "127.0.0.1", port: Int = 6379)(implicit system: ActorSystem): TelemetryService = {
    val redisClient = RedisClient(host, port)
    TelemetryServiceImpl(redisClient, defaultScope)
  }

  // Converts a callback that takes an Telemetry to one that takes a StatusEvent
  private[events] def callbackConverter(telemCallback: StatusEvent => Unit)(event: StatusEvent): Unit =
    event match {
      case s: StatusEvent => telemCallback(s)
      case _              =>
    }

  /**
   * Type of return value from the subscribe method
   */
  trait TelemetryMonitor {
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
 * API for publishing, getting and subscribing to telemetry (StatusEvent objects).
 */
trait TelemetryService {
  import TelemetryService._

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
   * @param subscriber     an actor to receive StatusEvent messages
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
   * @param prefixes       one or more prefixes of events, may include wildcard
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  def subscribe(subscriber: ActorRef, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): TelemetryMonitor

  /**
   * Subscribes a callback function to events matching the given prefixes
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param callback       an callback which will be called with StatusEvent objects (in another thread)
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
   * @param prefixes       one or more prefixes of events, may include wildcard
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  def subscribe(callback: StatusEvent => Unit, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): TelemetryMonitor

  /**
   * Creates an TelemetryMonitorActor and subscribes the given actor to it.
   * The return value can be used to stop the actor or subscribe and unsubscribe to events.
   *
   * @param subscriber     an actor to receive StatusEvent messages
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events first
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  def createEventMonitor(subscriber: ActorRef, postLastEvents: Boolean)(implicit _system: ActorRefFactory): TelemetryMonitor = subscribe(subscriber, postLastEvents)

  /**
   * Creates an TelemetryMonitorActor and subscribes the given actor to it.
   * The return value can be used to stop the actor or subscribe and unsubscribe to events.
   *
   * @param callback       an callback which will be called with StatusEvent objects (in another thread)
   * @param postLastEvents if true, the callback receives the last known values of any subscribed events first
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  def createEventMonitor(callback: StatusEvent => Unit, postLastEvents: Boolean)(implicit _system: ActorRefFactory): TelemetryMonitor = subscribe(callback, postLastEvents)

  /**
   * Gets the value for the given status event prefix
   *
   * @param prefix the prefix (key) for the event to get
   * @return the status event, if (and when) found
   */
  def get(prefix: String): Future[Option[StatusEvent]]

  /**
   * Gets a list of the n most recent status event values for the given prefix
   *
   * @param prefix the status event's prefix
   * @param n      the max number of values to get
   * @return future sequence of status events, ordered by most recent
   */
  def getHistory(prefix: String, n: Int): Future[Seq[StatusEvent]]

  /**
   * Deletes the saved status events matching the given prefixes from the server
   *
   * @return a future indicating if/when the operation has completed
   */
  def delete(prefix: String*)(implicit ec: ExecutionContext): Future[Unit]
}

object TelemetryServiceImpl {

  // Implement value returned from subscribe method
  private[events] case class TelemetryMonitorImpl(actorRef: ActorRef, scope: String) extends TelemetryMonitor {
    import TelemetryMonitorActor._

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
  private object TelemetryMonitorActor {
    def props(
      subscriber:       Option[ActorRef],
      callback:         Option[StatusEvent => Unit],
      telemetryService: TelemetryServiceImpl,
      postLastEvents:   Boolean
    ): Props =
      Props(classOf[TelemetryMonitorActor], subscriber, callback, telemetryService, postLastEvents)

    // Message sent to subscribe to more prefixes
    case class Subscribe(prefixes: String*)

    // Message sent to unsubscribe to prefixes
    case class Unsubscribe(prefixes: String*)

  }

  private class TelemetryMonitorActor(
      subscriber:       Option[ActorRef],
      callback:         Option[StatusEvent => Unit],
      telemetryService: TelemetryServiceImpl,
      postLastEvents:   Boolean
  ) extends EventSubscriber(telemetryService.redisClient.host, telemetryService.redisClient.port) {

    import context.dispatcher
    import TelemetryMonitorActor._

    subscriber.foreach(context.watch)

    def receive: Receive = {
      // Stop if the subscriber terminates
      case Terminated(actorRef) =>
        context.stop(self)

      case event: StatusEvent =>
        notifySubscribers(event)

      case s: Subscribe =>
        if (postLastEvents) {
          // Notify the subscriber of the current event values
          for {
            currentEvents <- Future.sequence(s.prefixes.map(telemetryService.get)).map(_.flatten)
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

    private def notifySubscribers(event: StatusEvent): Unit = {
      subscriber.foreach(_ ! event)
      callback.foreach { f =>
        Future {
          f(event)
        }.onComplete {
          case Failure(ex) => log.error("StatusEvent callback failed: ", ex)
          case _           =>
        }
      }
    }
  }

}

/**
 * A class for publishing, getting and subscribing to telemetry (StatusEvent objects).
 *
 * @param redisClient used to talk to Redis
 * @param scope       a string used to make the keys unique for this class (for example: "telem")
 */
case class TelemetryServiceImpl(redisClient: RedisClient, scope: String) extends TelemetryService {

  import TelemetryService._
  import TelemetryServiceImpl._

  // Implicit conversion between ByteString and StatusEvent, for the Redis API
  implicit val statusEventFormatter = new ByteStringFormatter[StatusEvent] {
    def serialize(e: StatusEvent): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): StatusEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[StatusEvent](ar)
    }
  }

  private def scopedKey(key: String) = {
    if (key.startsWith(scope)) key else s"$scope:$key"
  }

  // Publishes the event and keeps the given number of previous values
  override def publish(event: StatusEvent, history: Int = 0)(implicit ec: ExecutionContext): Future[Unit] = {
    // Serialize the event
    val formatter = implicitly[ByteStringFormatter[StatusEvent]]
    val bs = formatter.serialize(event)
    // only do this once
    val h = if (history >= 0) history else 0
    // Use a transaction to send all commands at once
    val redisTransaction = redisClient.transaction()
    val key = scopedKey(event.prefix)
    redisTransaction.watch(key)
    val f1 = redisTransaction.lpush(key, bs)
    val f2 = redisTransaction.ltrim(key, 0, h + 1)
    val f3 = redisTransaction.publish(key, bs)
    val f4 = redisTransaction.exec()
    Future.sequence(List(f1, f2, f3, f4)).map(_ => ())
  }

  override def subscribe(subscriber: ActorRef, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): TelemetryMonitor = {
    val actorRef = _system.actorOf(TelemetryMonitorActor.props(Some(subscriber), None, this, postLastEvents))
    val monitor = TelemetryMonitorImpl(actorRef, scope)
    monitor.subscribe(prefixes.map(scopedKey): _*)
    monitor
  }

  override def subscribe(callback: StatusEvent => Unit, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): TelemetryMonitor = {
    val actorRef = _system.actorOf(TelemetryMonitorActor.props(None, Some(callback), this, postLastEvents))
    val monitor = TelemetryMonitorImpl(actorRef, scope)
    monitor.subscribe(prefixes.map(scopedKey): _*)
    monitor
  }

  // gets the current value for the given prefix
  override def get(prefix: String): Future[Option[StatusEvent]] = redisClient.lindex(scopedKey(prefix), 0)

  // Gets the last n values for the given prefix
  override def getHistory(prefix: String, n: Int): Future[Seq[StatusEvent]] = redisClient.lrange(scopedKey(prefix), 0, n - 1)

  // deletes the saved values for the given prefixes
  override def delete(prefixes: String*)(implicit ec: ExecutionContext): Future[Unit] = redisClient.del(prefixes.map(scopedKey): _*).map(_ => ())

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
   * @param subscriber     an actor to receive StatusEvent messages
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
   * @param prefixes       one or more prefixes of events, may include wildcard
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  def subscribe(subscriber: ActorRef, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): TelemetryMonitor =
    ts.subscribe(subscriber, postLastEvents, prefixes: _*)

  /**
   * Subscribes an actor or callback function to events matching the given prefixes
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param callback       an callback which will be called with StatusEvent objects (in another thread)
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
   * @param prefixes       one or more prefixes of events, may include wildcard
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  def subscribe(callback: StatusEvent => Unit, postLastEvents: Boolean, prefixes: String*)(implicit _system: ActorRefFactory): TelemetryMonitor =
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

