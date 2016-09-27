package csw.services.events

import akka.actor.{ActorRef, ActorRefFactory}

import scala.concurrent.Future

object EventService {

  /**
   * Returns a concrete implementation of the EventService trait (based on Redis)
   *
   * @param settings contains the host and port settings from reference.conf, or application.conf
   * @param _system  Akka env required for RedisClient
   */
  def apply(settings: EventServiceSettings)(implicit _system: ActorRefFactory): EventService =
    EventServiceImpl(settings.redisHostname, settings.redisPort)

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

  /**
   * Disconnects from the key/value store server
   */
  def disconnect(): Future[Unit]

  /**
   * Shuts the key/value store server down
   */
  def shutdown(): Future[Unit]
}
