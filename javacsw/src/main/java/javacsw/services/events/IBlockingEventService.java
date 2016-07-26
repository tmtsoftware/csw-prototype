package javacsw.services.events;

import akka.actor.ActorRefFactory;
import csw.services.events.EventServiceSettings;
import csw.util.config.Configurations;
import csw.util.config.Events;
import csw.util.config.StateVariable;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Java interface for a blocking event service. This class blocks and waits for operations to complete
 * (rather than returning Futures as results).
 *
 * Note: Type T must extend EventService.EventFormatter, but due to type issues between Scala and Java,
 * that is not declared here.
 */
@SuppressWarnings("unused")
public interface IBlockingEventService<T> {
    //interface IBlockingEventService<T extends EventService.EventFormatter> {

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

    /**
     * Disconnects from the key/value store server
     */
    void  disconnect();

    /**
     * Shuts the key/value store server down
     */
    void shutdown();


    // --- factory methods ---

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for StatusEvent objects
     */
    static IBlockingEventService<Events.StatusEvent> getStatusEventStore(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return JBlockingEventService.getStatusEventStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for ObserveEvent objects
     */
    static IBlockingEventService<Events.ObserveEvent> getObserveEventStore(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return JBlockingEventService.getObserveEventStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for SystemEvent objects
     */
    static IBlockingEventService<Events.SystemEvent> getSystemEventStore(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return JBlockingEventService.getSystemEventStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for SetupConfig objects
     */
    static IBlockingEventService<Configurations.SetupConfig> getSetupConfigStore(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return JBlockingEventService.getSetupConfigStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for CurrentState objects
     */
    static IBlockingEventService<StateVariable.CurrentState> getCurrentStateStore(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return JBlockingEventService.getCurrentStateStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for DemandState objects
     */
    static IBlockingEventService<StateVariable.DemandState> getDemandStateStore(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return JBlockingEventService.getDemandStateStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for StateVariable objects
     */
    static IBlockingEventService<StateVariable.StateVariable> getStateVariableStore(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return JBlockingEventService.getStateVariableStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for SetupConfigArg objects
     */
    static IBlockingEventService<Configurations.SetupConfigArg> getSetupConfigArgStore(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return JBlockingEventService.getSetupConfigArgStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for EventServiceEvent objects
     */
    static IBlockingEventService<Events.EventServiceEvent> getEventServiceEventStore(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return JBlockingEventService.getEventServiceEventStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for SequenceConfig objects
     */
    static IBlockingEventService<Configurations.SequenceConfig> getSequenceConfigStore(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return JBlockingEventService.getSequenceConfigStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for ControlConfig objects
     */
    static IBlockingEventService<Configurations.ControlConfig> getControlConfigStore(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return JBlockingEventService.getControlConfigStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for SequenceConfigArg objects
     */
    static IBlockingEventService<Configurations.SequenceConfigArg> getSequenceConfigArgStore(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return JBlockingEventService.getSequenceConfigArgStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for ControlConfigArg objects
     */
    static IBlockingEventService<Configurations.ControlConfigArg> getControlConfigArgStore(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return JBlockingEventService.getControlConfigArgStore(timeout, settings, system);
    }


}
