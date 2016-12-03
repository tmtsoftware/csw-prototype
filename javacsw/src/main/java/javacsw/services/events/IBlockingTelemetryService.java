package javacsw.services.events;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.util.Timeout;
import csw.services.events.EventService;
import csw.services.events.TelemetryService$;
import csw.util.config.Events.StatusEvent;

import java.util.List;
import java.util.Optional;

/**
 * Synchronous/blocking Java API for the telemetry service
 */
@SuppressWarnings("unused")
public interface IBlockingTelemetryService {

    /**
     * The default name that the Telemetry Service is registered with
     */
    String defaultName = TelemetryService$.MODULE$.defaultName();

    /**
     * Publishes the status event (key is based on the event's prefix)
     *
     * @param status the value to store
     */
    void publish(StatusEvent status);

    /**
     * Publishes the status event (key is based on the event's prefix)
     *
     * @param status the value to store
     * @param history optional number of previous values to store
     */
    void publish(StatusEvent status, int history);

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
     * @param subscriber an actor to receive StatusEvent messages
     * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
     * @param prefixes   one or more prefixes of events, may include wildcard
     */
    EventService.EventMonitor subscribe(ActorRef subscriber, boolean postLastEvents, String... prefixes);

    /**
     * Subscribes a callback function to telemetry events matching the given prefixes
     * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
     *
     * @param callback   an callback which will be called with StatusEvent objects (in another thread)
     * @param postLastEvents if true, the subscriber receives the last known values of any subscribed events
     * @param prefixes   one or more prefixes of events, may include wildcard
     */
    EventService.EventMonitor subscribe(TelemetryHandler callback, boolean postLastEvents, String... prefixes);


    /**
     * Gets the value for the given status event prefix
     *
     * @param prefix the prefix (key) for the event to get
     * @return the status event, if (and when) found
     */
    Optional<StatusEvent> get(String prefix);

    /**
     * Gets a list of the n most recent status event values for the given prefix
     * @param prefix the status event's prefix
     * @param n the max number of values to get
     * @return list of status events, ordered by most recent
     */
    List<StatusEvent> getHistory(String prefix, int n);

    /**
     * Deletes the given status event from the store
     */
    void delete(String key);


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
    static IBlockingTelemetryService getTelemetryService(String name, ActorRefFactory sys, Timeout timeout) {
        return JBlockingTelemetryService.lookup(name, sys, timeout);
    }

    /**
     * Returns an ITelemetryService instance using the Redis instance at the given host and port
     *
     * @param host the Redis host name or IP address
     * @param port the Redis port
     * @return a new ITelemetryService instance
     */
    static IBlockingTelemetryService getTelemetryService(String host, int port, ActorRefFactory sys, Timeout timeout) {
        return new JBlockingTelemetryService(host, port, sys, timeout);
    }
}
