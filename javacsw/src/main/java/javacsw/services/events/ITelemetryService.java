package javacsw.services.events;

import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import csw.services.events.EventServiceSettings;
import csw.util.config.Events.StatusEvent;
import scala.Unit;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Java API for the telemetry service
 */
@SuppressWarnings("unused")
public interface ITelemetryService {
    /**
     * Sets the value for the status event (key is based on the event's prefix)
     *
     * @param status the value to store
     * @return a future indicating if/when the operation has completed
     */
    CompletableFuture<Unit> set(StatusEvent status);

    /**
     * Sets the value for the status event (key is based on the event's prefix)
     *
     * @param status the value to store
     * @param history optional number of previous values to store
     * @return a future indicating if/when the operation has completed
     */
    CompletableFuture<Unit> set(StatusEvent status, int history);

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

    /**
     * Disconnects from the key/value store server
     */
    CompletableFuture<Unit>  disconnect();

    /**
     * Shuts the key/value store server down
     */
    CompletableFuture<Unit> shutdown();


    // --- static factory methods ---

    /**
     * @param system the actor system used to access the akka config file containing the kvs settings
     * @return an object containing the kvs settings
     */
    static EventServiceSettings getKvsSettings(ActorSystem system) {
        return EventServiceSettings.getKvsSettings(system);
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
