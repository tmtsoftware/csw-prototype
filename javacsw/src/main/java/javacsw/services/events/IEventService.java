package javacsw.services.events;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import csw.services.events.EventService$;
import csw.services.events.EventService.*;
import csw.services.events.EventServiceSettings;
import csw.services.loc.ComponentId;
import csw.services.loc.Connection;
import csw.services.loc.LocationService;
import csw.util.param.Events.EventServiceEvent;
import scala.Unit;

import java.util.concurrent.CompletableFuture;

/**
 * A Java interface for a non-blocking key / value store. This class does not wait for operations to complete
 * and returns Futures as results.
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public interface IEventService {

  /**
   * The default name that the Event Service is registered with
   */
  String defaultName = EventService$.MODULE$.defaultName();

  /**
   * Returns the EventService ComponentId for the given, or default name
   */
  static ComponentId eventServiceComponentId(String name) {
    return EventService$.MODULE$.eventServiceComponentId(name);
  }

  /**
   * Returns the EventService connection for the given
   */
  static Connection.TcpConnection eventServiceConnection(String name) {
    return EventService$.MODULE$.eventServiceConnection(name);
  }

  /**
   * Returns the EventService connection for the default name
   */
  static Connection.TcpConnection eventServiceConnection() {
    return EventService$.MODULE$.eventServiceConnection(defaultName);
  }


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

  /**
   * Creates an EventMonitorActor and subscribes the given actor to it.
   * The return value can be used to stop the actor or subscribe and unsubscribe to events.
   *
   * @param subscriber an actor to receive Event messages
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events first
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  default EventMonitor createEventMonitor(ActorRef subscriber, boolean postLastEvents) {
    return subscribe(subscriber, postLastEvents);
  }

  /**
   * Creates an EventMonitorActor and subscribes the given actor to it.
   * The return value can be used to stop the actor or subscribe and unsubscribe to events.
   *
   * @param callback   an callback which will be called with Event objects (in another thread)
   * @param postLastEvents if true, the callback receives the last known values of any subscribed events first
   * @return an object containing an actorRef that can be used to subscribe and unsubscribe or stop the actor
   */
  default EventMonitor createEventMonitor(IEventService.EventHandler callback, boolean postLastEvents) {
    return subscribe(callback, postLastEvents);
  }


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
  static CompletableFuture<IEventService> getEventService(String name, ActorSystem sys, Timeout timeout) {
    return JEventService.lookup(name, sys, timeout);
  }

    /**
   * Resolves the location of the event service with the given (or default) name.
   * @param name name used to register the Redis instance with the Location Service (default: "Event Service")
   * @param sys the actor system to use for the future
   * @param timeout timeout for the lookup with the location service
   * @return the future resolved location
   */
  static CompletableFuture<LocationService.ResolvedTcpLocation> getEventServiceLocation(String name, ActorSystem sys, Timeout timeout) {
    return JEventService.getEventServiceLocation(name, sys, timeout);
  }

  /**
   * Returns an IEventService instance using the Redis instance at the given host and port
   *
   * @param host the Redis host name or IP address
   * @param port the Redis port
   * @return a new IEventService instance
   */
  static IEventService getEventService(String host, int port, ActorSystem sys) {
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
  static IEventService getEventService(EventServiceSettings settings, ActorSystem system) {
    return new JEventService(settings, system);
  }

}
