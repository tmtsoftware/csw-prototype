package javacsw.services.events;

import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import csw.services.events.EventServiceSettings;
import csw.util.config.Configurations;
import csw.util.config.Events;
import csw.util.config.StateVariable;
import scala.Unit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A Java interface for a non-blocking key / value store. This class does not wait for operations to complete
 * and returns Futures as results.
 *
 * Note: Type T must extend EventService.EventFormatter, but due to type issues between Scala and Java,
 * that is not declared here.
 */
@SuppressWarnings("unused")
public interface IEventService<T> {
    //interface IEventService<T extends EventService.EventFormatter> {

    /**
     * Sets (and publishes) the value for the given key
     *
     * @param key   the key
     * @param value the value to store
     */
    CompletableFuture<Unit> set(String key, T value);

    /**
     * Sets (and publishes) the value for the given key
     *
     * @param key   the key
     * @param value the value to store
     * @param n     the max number of history values to keep (0 means no history)
     */
    CompletableFuture<Unit> set(String key, T value, int n);

    /**
     * Gets the value of the given key
     *
     * @param key the key
     * @return the result, None if the key was not found
     */
    CompletableFuture<Optional<T>> get(String key);

    /**
     * Returns a list containing up to the last n values for the given key
     *
     * @param key the key to use
     * @param n   max number of history values to return
     * @return list of the last n values
     */
    CompletableFuture<List<T>> getHistory(String key, int n);

    /**
     * Deletes the given key(s) from the store
     *
     * @param key the key to delete
     * @return the number of keys that were deleted
     */
    CompletableFuture<Boolean> delete(String key);

    /**
     * Sets a value for the given key, where the value itself is a map with keys and values.
     *
     * @param key   the key
     * @param value the map of values to store
     * @return the result (true if successful)
     */
    CompletableFuture<Boolean> hmset(String key, Map<String, String> value);

    /**
     * This method is mainly useful for testing hmset. It gets the value of the given field
     * in the map that is the value for the given key. The value is returned here as a String.
     *
     * @param key   the key
     * @param field the key for a value in the map
     * @return the result string value for the field, if found
     */
    CompletableFuture<Optional<String>> hmget(String key, String field);

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
     * @return a new IEventService for StatusEvent objects
     */
    static IEventService<Events.StatusEvent> getStatusEventStore(EventServiceSettings settings, ActorRefFactory system) {
        return JEventService.getStatusEventStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IEventService for ObserveEvent objects
     */
    static IEventService<Events.ObserveEvent> getObserveEventStore(EventServiceSettings settings, ActorRefFactory system) {
        return JEventService.getObserveEventStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IEventService for SystemEvent objects
     */
    static IEventService<Events.SystemEvent> getSystemEventStore(EventServiceSettings settings, ActorRefFactory system) {
        return JEventService.getSystemEventStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IEventService for SetupConfig objects
     */
    static IEventService<Configurations.SetupConfig> getSetupConfigStore(EventServiceSettings settings, ActorRefFactory system) {
        return JEventService.getSetupConfigStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IEventService for CurrentState objects
     */
    static IEventService<StateVariable.CurrentState> getCurrentStateStore(EventServiceSettings settings, ActorRefFactory system) {
        return JEventService.getCurrentStateStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IEventService for DemandState objects
     */
    static IEventService<StateVariable.DemandState> getDemandStateStore(EventServiceSettings settings, ActorRefFactory system) {
        return JEventService.getDemandStateStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IEventService for StateVariable objects
     */
    static IEventService<StateVariable.StateVariable> getStateVariableStore(EventServiceSettings settings, ActorRefFactory system) {
        return JEventService.getStateVariableStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IEventService for SetupConfigArg objects
     */
    static IEventService<Configurations.SetupConfigArg> getSetupConfigArgStore(EventServiceSettings settings, ActorRefFactory system) {
        return JEventService.getSetupConfigArgStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IEventService for EventServiceEvent objects
     */
    static IEventService<Events.EventServiceEvent> getEventServiceEventStore(EventServiceSettings settings, ActorRefFactory system) {
        return JEventService.getEventServiceEventStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IEventService for SequenceConfig objects
     */
    static IEventService<Configurations.SequenceConfig> getSequenceConfigStore(EventServiceSettings settings, ActorRefFactory system) {
        return JEventService.getSequenceConfigStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IEventService for ControlConfig objects
     */
    static IEventService<Configurations.ControlConfig> getControlConfigStore(EventServiceSettings settings, ActorRefFactory system) {
        return JEventService.getControlConfigStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IEventService for SequenceConfigArg objects
     */
    static IEventService<Configurations.SequenceConfigArg> getSequenceConfigArgStore(EventServiceSettings settings, ActorRefFactory system) {
        return JEventService.getSequenceConfigArgStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IEventService for ControlConfigArg objects
     */
    static IEventService<Configurations.ControlConfigArg> getControlConfigArgStore(EventServiceSettings settings, ActorRefFactory system) {
        return JEventService.getControlConfigArgStore(settings, system);
    }

}
