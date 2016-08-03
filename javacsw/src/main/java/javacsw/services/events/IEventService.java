package javacsw.services.events;

import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import csw.services.events.EventServiceSettings;
import csw.util.config.Events.EventServiceEvent;
import scala.Unit;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A Java interface for a non-blocking key / value store. This class does not wait for operations to complete
 * and returns Futures as results.
 */
@SuppressWarnings("unused")
public interface IEventService {
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
     * @return a new IEventService for StatusEvent objects
     */
    static IEventService getEventService(EventServiceSettings settings, ActorRefFactory system) {
        return new JEventService(settings, system);
    }


    /**
     * Publishes the value for the given key
     *
     * @param event   the event to publish
     */
    CompletableFuture<Unit> publish(EventServiceEvent event);

    /**
     * Sets (and publishes) the value for the given key
     *
     * @param event the value to store
     * @param n     the max number of history values to keep (0 means no history)
     */
    CompletableFuture<Unit> publish(EventServiceEvent event, int n);

    /**
     * Gets the value of the given key
     *
     * @param key the key
     * @return the result, None if the key was not found
     */
    CompletableFuture<Optional<EventServiceEvent>> get(String key);

    /**
     * Returns a list containing up to the last n values for the given key
     *
     * @param key the key to use
     * @param n   max number of history values to return
     * @return list of the last n values
     */
    CompletableFuture<List<EventServiceEvent>> getHistory(String key, int n);

    /**
     * Deletes the given key(s) from the store
     *
     * @param key the key to delete
     * @return the number of keys that were deleted
     */
    CompletableFuture<Boolean> delete(String key);


    /**
     * Disconnects from the key/value store server
     */
    CompletableFuture<Unit>  disconnect();

    /**
     * Shuts the key/value store server down
     */
    CompletableFuture<Unit> shutdown();
}
