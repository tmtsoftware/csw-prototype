package javacsw.services.kvs;

import akka.actor.ActorRefFactory;
import csw.services.kvs.KvsSettings;
import csw.util.cfg.Configurations;
import csw.util.cfg.Events;
import csw.util.cfg.StateVariable;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Java interface for a blocking key / value store. This class blocks and waits for operations to complete
 * (rather than returning Futures as results).
 *
 * Note: Type T must extend KeyValueStore.KvsFormatter, but due to type issues between Scala and Java,
 * that is not declared here.
 */
@SuppressWarnings("unused")
public interface IBlockingKeyValueStore<T> {
    //interface IBlockingKeyValueStore<T extends KeyValueStore.KvsFormatter> {

    /**
     * Sets (and publishes) the value for the given key
     *
     * @param key   the key
     * @param value the value to store
     */
    void set(String key, T value);

    /**
     * Sets (and publishes) the value for the given key
     *
     * @param key   the key
     * @param value the value to store
     * @param n     the max number of history values to keep (0 means no history)
     */
    void set(String key, T value, int n);

    /**
     * Gets the value of the given key
     *
     * @param key the key
     * @return the result, None if the key was not found
     */
    Optional<T> get(String key);

    /**
     * Returns a list containing up to the last n values for the given key
     *
     * @param key the key to use
     * @param n   max number of history values to return
     * @return list of the last n values
     */
    List<T> getHistory(String key, int n);

    /**
     * Deletes the given key(s) from the store
     *
     * @param key the key to delete
     * @return the number of keys that were deleted
     */
    boolean delete(String key);

    /**
     * Sets a value for the given key, where the value itself is a map with keys and values.
     *
     * @param key   the key
     * @param value the map of values to store
     * @return the result (true if successful)
     */
    Boolean hmset(String key, Map<String, String> value);

    /**
     * This method is mainly useful for testing hmset. It gets the value of the given field
     * in the map that is the value for the given key. The value is returned here as a String.
     *
     * @param key   the key
     * @param field the key for a value in the map
     * @return the result string value for the field, if found
     */
    Optional<String> hmget(String key, String field);

    // --- factory methods ---
    
    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new BlockingKeyValueStore for StatusEvent objects
     */
    static IBlockingKeyValueStore<Events.StatusEvent> getStatusEventStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getStatusEventStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for ObserveEvent objects
     */
    static IBlockingKeyValueStore<Events.ObserveEvent> getObserveEventStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getObserveEventStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for SystemEvent objects
     */
    static IBlockingKeyValueStore<Events.SystemEvent> getSystemEventStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getSystemEventStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for SetupConfig objects
     */
    static IBlockingKeyValueStore<Configurations.SetupConfig> getSetupConfigStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getSetupConfigStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for CurrentState objects
     */
    static IBlockingKeyValueStore<StateVariable.CurrentState> getCurrentStateStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getCurrentStateStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for DemandState objects
     */
    static IBlockingKeyValueStore<StateVariable.DemandState> getDemandStateStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getDemandStateStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for StateVariable objects
     */
    static IBlockingKeyValueStore<StateVariable> getStateVariableStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getStateVariableStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for SetupConfigArg objects
     */
    static IBlockingKeyValueStore<Configurations.SetupConfigArg> getSetupConfigArgStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getSetupConfigArgStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for EventServiceEvent objects
     */
    static IBlockingKeyValueStore<Events.EventServiceEvent> getEventServiceEventStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getEventServiceEventStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for SequenceConfig objects
     */
    static IBlockingKeyValueStore<Configurations.SequenceConfig> getSequenceConfigStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getSequenceConfigStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for ControlConfig objects
     */
    static IBlockingKeyValueStore<Configurations.ControlConfig> getControlConfigStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getControlConfigStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for SequenceConfigArg objects
     */
    static IBlockingKeyValueStore<Configurations.SequenceConfigArg> getSequenceConfigArgStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getSequenceConfigArgStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new KeyValueStore for ControlConfigArg objects
     */
    static IBlockingKeyValueStore<Configurations.ControlConfigArg> getControlConfigArgStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getControlConfigArgStore(timeout, settings, system);
    }


}
