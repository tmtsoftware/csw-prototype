package javacsw.services.kvs;

import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import csw.services.kvs.KvsSettings;
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
 * Note: Type T must extend KeyValueStore.KvsFormatter, but due to type issues between Scala and Java,
 * that is not declared here.
 */
@SuppressWarnings("unused")
public interface IKeyValueStore<T> {
    //interface IKeyValueStore<T extends KeyValueStore.KvsFormatter> {

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

    
    // --- static factory methods ---
    
    /**
     * @param system the actor system used to access the akka config file containing the kvs settings
     * @return an object containing the kvs settings
     */
    static KvsSettings getKvsSettings(ActorSystem system) {
        return KvsSettings.getKvsSettings(system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for StatusEvent objects
     */
    static IKeyValueStore<Events.StatusEvent> getStatusEventStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getStatusEventStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for ObserveEvent objects
     */
    static IKeyValueStore<Events.ObserveEvent> getObserveEventStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getObserveEventStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for SystemEvent objects
     */
    static IKeyValueStore<Events.SystemEvent> getSystemEventStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getSystemEventStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for SetupConfig objects
     */
    static IKeyValueStore<Configurations.SetupConfig> getSetupConfigStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getSetupConfigStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for CurrentState objects
     */
    static IKeyValueStore<StateVariable.CurrentState> getCurrentStateStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getCurrentStateStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for DemandState objects
     */
    static IKeyValueStore<StateVariable.DemandState> getDemandStateStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getDemandStateStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for StateVariable objects
     */
    static IKeyValueStore<StateVariable.StateVariable> getStateVariableStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getStateVariableStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for SetupConfigArg objects
     */
    static IKeyValueStore<Configurations.SetupConfigArg> getSetupConfigArgStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getSetupConfigArgStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for EventServiceEvent objects
     */
    static IKeyValueStore<Events.EventServiceEvent> getEventServiceEventStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getEventServiceEventStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for SequenceConfig objects
     */
    static IKeyValueStore<Configurations.SequenceConfig> getSequenceConfigStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getSequenceConfigStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for ControlConfig objects
     */
    static IKeyValueStore<Configurations.ControlConfig> getControlConfigStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getControlConfigStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for SequenceConfigArg objects
     */
    static IKeyValueStore<Configurations.SequenceConfigArg> getSequenceConfigArgStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getSequenceConfigArgStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for ControlConfigArg objects
     */
    static IKeyValueStore<Configurations.ControlConfigArg> getControlConfigArgStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getControlConfigArgStore(settings, system);
    }

}
