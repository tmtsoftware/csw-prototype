package javacsw.services.events

import java.util.concurrent.CompletableFuture
import javacsw.services.events.IEventService.EventHandler

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.util.Timeout
import csw.services.events.EventService.EventMonitor
import csw.services.events.{EventService, EventServiceSettings}
import csw.services.loc.LocationService.ResolvedTcpLocation
import csw.util.config.Events.EventServiceEvent

import scala.compat.java8.FutureConverters._

case object JEventService {
  /**
   * Looks up the Redis instance for the Event Service with the Location Service
   * and then returns an EventService instance using it.
   *
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param name name used to register the Redis instance with the Location Service (default: "Event Service")
   * @param sys required Akka environment
   * @param timeout amount of time to wait looking up name with the location service before giving up with an error
   * @return a future JEventService instance
   */
  def lookup(name: String, sys: ActorSystem, timeout: Timeout): CompletableFuture[IEventService] = {
    import sys.dispatcher
    EventService(name)(sys, timeout).map(JEventService(_, sys).asInstanceOf[IEventService]).toJava.toCompletableFuture
  }

  /**
   * Returns location information about the event service with the given name
   * @param name name used to register the Redis instance with the Location Service (default: "Event Service")
   * @param sys required Akka environment
   * @param timeout amount of time to wait looking up name with the location service before giving up with an error
   * @return a future resolved tcp location
   */
  def getEventServiceLocation(name: String, sys: ActorSystem, timeout: Timeout): CompletableFuture[ResolvedTcpLocation] = {
    EventService.getEventServiceLocation(name)(sys, timeout).toJava.toCompletableFuture
  }
}

/**
 * A Java wrapper API for a key/value store
 *
 * @param eventService the underlying Scala event service implementation
 * @param system Akka env required by RedisClient
 */
case class JEventService(eventService: EventService, system: ActorRefFactory)
    extends IEventService {

  private implicit val _system: ActorRefFactory = system
  import system.dispatcher

  /**
   * Alternate constructor to use the Redis instance at the given host and port
   *
   * @param host the Redis host name or IP address
   * @param port the Redis port
   * @return a new JEventService instance
   */
  def this(host: String, port: Int, sys: ActorSystem) {
    this(EventService.get(host, port)(sys), sys)
  }

  /**
   * Alternate constructor that gets the redis host and port from the Akka system settings.
   *
   * @param settings contains the host and port settings from reference.conf, or application.conf
   * @param sys  Akka env required for RedisClient
   */
  def this(settings: EventServiceSettings, sys: ActorSystem) {
    this(EventService(settings)(sys), sys)
  }

  override def publish(event: EventServiceEvent): CompletableFuture[Unit] =
    eventService.publish(event).toJava.toCompletableFuture

  override def subscribe(subscriber: ActorRef, postLastEvents: Boolean, prefixes: String*): EventMonitor =
    eventService.subscribe(subscriber, postLastEvents, prefixes: _*)

  override def subscribe(callback: EventHandler, postLastEvents: Boolean, prefixes: String*): EventMonitor =
    eventService.subscribe(callback.handleEvent _, postLastEvents = true, prefixes: _*)
}
