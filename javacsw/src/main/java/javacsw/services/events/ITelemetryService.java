package javacsw.services.events;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import csw.services.events.EventService$;
import csw.services.events.EventService.EventMonitor;
import csw.services.events.TelemetryService$;
import csw.services.loc.ComponentId;
import csw.services.loc.Connection;
import csw.util.config.Events.StatusEvent;
import scala.Unit;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Java API for the telemetry service
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public interface ITelemetryService {
  /**
   * The default name that the Telemetry Service is registered with
   */
  String defaultName = TelemetryService$.MODULE$.defaultName();

  /**
   * Returns the EventService ComponentId for the given, or default name
   */
  static ComponentId telemetryServiceComponentId(String name) {
    return TelemetryService$.MODULE$.telemetryServiceComponentId(name);
  }

  /**
   * Returns the TelemetryService connection for the given name
   */
  static Connection.TcpConnection telemetryServiceConnection(String name) {
    return TelemetryService$.MODULE$.telemetryServiceConnection(name);
  }

  /**
   * Returns the TelemetryService connection for the default name
   */
  static Connection.TcpConnection telemetryServiceConnection() {
    return TelemetryService$.MODULE$.telemetryServiceConnection(defaultName);
  }

  /**
   * Publishes the value for the status event (key is based on the event's prefix)
   *
   * @param status the value to store
   * @return a future indicating if/when the operation has completed
   */
  CompletableFuture<Unit> publish(StatusEvent status);

  /**
   * Publishes the value for the status event (key is based on the event's prefix)
   *
   * @param status  the value to store
   * @param history optional number of previous values to store
   * @return a future indicating if/when the operation has completed
   */
  CompletableFuture<Unit> publish(StatusEvent status, int history);

  /**
   * API to handle a telemetry event (StatusEvent) from the telemetry service
   */
  interface TelemetryHandler {
    void handleEvent(StatusEvent event);
  }

  /**
   * Subscribes an actor to telemetry events matching the given prefixes
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param subscriber     an actor to receive StatusEvent messages
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
   * @param prefixes       one or more prefixes of events, may include wildcard
   */
  EventMonitor subscribe(ActorRef subscriber, boolean postLastEvents, String... prefixes);

  /**
   * Subscribes a callback function to telemetry events matching the given prefixes
   * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
   *
   * @param callback       an callback which will be called with StatusEvent objects (in another thread)
   * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
   * @param prefixes       one or more prefixes of events, may include wildcard
   */
  EventMonitor subscribe(TelemetryHandler callback, boolean postLastEvents, String... prefixes);

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
  default EventMonitor createEventMonitor(TelemetryHandler callback, boolean postLastEvents) {
    return subscribe(callback, postLastEvents);
  }

  /**
   * Gets the value for the given status event prefix
   *
   * @param prefix the prefix (key) for the event to get
   * @return the status event, if (and when) found
   */
  CompletableFuture<Optional<StatusEvent>> get(String prefix);

  /**
   * Gets a list of the n most recent status event values for the given prefix
   *
   * @param prefix the status event's prefix
   * @param n      the max number of values to get
   * @return future sequence of status events, ordered by most recent
   */
  CompletableFuture<List<StatusEvent>> getHistory(String prefix, int n);

  /**
   * Deletes the given status event from the store
   *
   * @return a future indicating if/when the operation has completed
   */
  CompletableFuture<Unit> delete(String key);

  // --- Static factory methods ---

  /**
   * Looks up the Redis instance for the Telemetry Service with the Location Service
   * and then returns an TelemetryService instance using it.
   * <p>
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param name    name used to register the Redis instance with the Location Service (default: "Telemetry Service")
   * @param sys     required Akka environment
   * @param timeout amount of time to wait looking up name with the location service before giving up with an error
   * @return a future ITelemetryService instance
   */
  static CompletableFuture<ITelemetryService> getTelemetryService(String name, ActorSystem sys, Timeout timeout) {
    return JTelemetryService.lookup(name, sys, timeout);
  }

  /**
   * Returns an ITelemetryService instance using the Redis instance at the given host and port
   *
   * @param host the Redis host name or IP address
   * @param port the Redis port
   * @return a new ITelemetryService instance
   */
  static ITelemetryService getTelemetryService(String host, int port, ActorSystem sys) {
    return new JTelemetryService(host, port, sys);
  }

}
