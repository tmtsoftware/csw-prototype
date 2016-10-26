package javacsw.services.events;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import csw.services.events.EventService.*;
import csw.services.events.EventServiceSettings;
import csw.util.config.Events.EventServiceEvent;
import scala.Unit;

import java.util.concurrent.CompletableFuture;

/**
 * A Java interface for a non-blocking key / value store. This class does not wait for operations to complete
 * and returns Futures as results.
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public interface IEventService {


  /**
   * Publishes the value for the given key
   *
   * @param event the event to publish
   */
  CompletableFuture<Unit> publish(EventServiceEvent event);

  /**
   * API to handle an event from the event service
   */
  interface EventHandler {
    void handleEvent(EventServiceEvent event);
  }

  /**
   * Subscribes an actor to events matching the given prefixes
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param subscriber     an actor to receive Event messages
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
   * @param prefixes       one or more prefixes of events, may include wildcard
   */
  EventMonitor subscribe(ActorRef subscriber, boolean postLastEvents, String... prefixes);

  /**
   * Subscribes a callback function to events matching the given prefixes
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param callback       an callback which will be called with Event objects (in another thread)
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
   * @param prefixes       one or more prefixes of events, may include wildcard
   */
  EventMonitor subscribe(EventHandler callback, boolean postLastEvents, String... prefixes);

  // --- Static factory methods ---

  /**
   * Looks up the Redis instance for the Event Service with the Location Service
   * and then returns an EventService instance using it.
   * <p>
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param name    name used to register the Redis instance with the Location Service (default: "Event Service")
   * @param sys     required Akka environment
   * @param timeout amount of time to wait looking up name with the location service before giving up with an error
   * @return a future IEventService instance
   */
  static CompletableFuture<IEventService> getEventService(String name, ActorRefFactory sys, Timeout timeout) {
    return JEventService.lookup(name, sys, timeout);
  }

  /**
   * Returns an IEventService instance using the Redis instance at the given host and port
   *
   * @param host the Redis host name or IP address
   * @param port the Redis port
   * @return a new IEventService instance
   */
  static IEventService getEventService(String host, int port, ActorRefFactory sys) {
    return new JEventService(host, port, sys);
  }

  /**
   * @param system the actor system used to access the akka config file containing the kvs settings
   * @return an object containing the kvs settings
   */
  static EventServiceSettings getEventServiceSettings(ActorSystem system) {
    return EventServiceSettings.getEventServiceSettings(system);
  }

  /**
   * @param settings Redis server settings
   * @param system   Akka env required by RedisClient
   * @return a new IEventService
   */
  static IEventService getEventService(EventServiceSettings settings, ActorRefFactory system) {
    return new JEventService(settings, system);
  }

}
