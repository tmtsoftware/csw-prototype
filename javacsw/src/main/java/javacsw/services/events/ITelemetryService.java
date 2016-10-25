package javacsw.services.events;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import csw.services.events.EventService;
import csw.services.events.EventServiceSettings;
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
     * Publishes the value for the status event (key is based on the event's prefix)
     *
     * @param status the value to store
     * @return a future indicating if/when the operation has completed
     */
    CompletableFuture<Unit> publish(StatusEvent status);

    /**
     * Publishes the value for the status event (key is based on the event's prefix)
     *
     * @param status the value to store
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
    CompletableFuture<Optional<StatusEvent>> get(String prefix);

    /**
     * Gets a list of the n most recent status event values for the given prefix
     * @param prefix the status event's prefix
     * @param n the max number of values to get
     * @return future sequence of status events, ordered by most recent
     */
    CompletableFuture<List<StatusEvent>> getHistory(String prefix, int n);

    /**
     * Deletes the given status event from the store
     * @return a future indicating if/when the operation has completed
     */
    CompletableFuture<Unit> delete(String key);

    // --- static factory methods ---

    /**
     * @param system the actor system used to access the akka config file containing the kvs settings
     * @return an object containing the kvs settings
     */
    static EventServiceSettings getKvsSettings(ActorSystem system) {
        return EventServiceSettings.getEventServiceSettings(system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new TelemetryService
     */
    static ITelemetryService getTelemetryService(EventServiceSettings settings, ActorRefFactory system) {
        return new JTelemetryService(settings, system);
    }

    /**
     * @param system   Akka env required by RedisClient
     * @return a new TelemetryService
     */
    static ITelemetryService getTelemetryService(ActorSystem system) {
        return new JTelemetryService(getKvsSettings(system), system);
    }
}
