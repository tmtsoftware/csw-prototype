package javacsw.services.kvs;

import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import csw.services.kvs.KvsSettings;
import csw.util.cfg.Configurations.*;
import csw.util.cfg.Events.*;
import csw.util.cfg.StateVariable;
import csw.util.cfg.StateVariable.*;
import scala.concurrent.duration.Duration;

/**
 * A factory API for Java for creating key/value stores.
 *
 * Note: You can also call the Scala object versions directly: These methods
 * document the API for Java.
 */
@SuppressWarnings("unused")
public class JKeyValueStoreFactory {

    /**
     * @param system the actor system used to access the akka config file containing the kvs settings
     * @return an object containing the kvs settings
     */
    public static KvsSettings getKvsSettings(ActorSystem system) {
        return new KvsSettings(system);
    }

    // --- Non-blocking key/value stores ---

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for StatusEvent objects
     */
    public static IKeyValueStore<StatusEvent> getStatusEventStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getStatusEventStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for ObserveEvent objects
     */
    public static IKeyValueStore<ObserveEvent> getObserveEventStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getObserveEventStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for SystemEvent objects
     */
    public static IKeyValueStore<SystemEvent> getSystemEventStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getSystemEventStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for SetupConfig objects
     */
    public static IKeyValueStore<SetupConfig> getSetupConfigStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getSetupConfigStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for CurrentState objects
     */
    public static IKeyValueStore<CurrentState> getCurrentStateStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getCurrentStateStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for DemandState objects
     */
    public static IKeyValueStore<DemandState> getDemandStateStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getDemandStateStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for StateVariable objects
     */
    public static IKeyValueStore<StateVariable> getStateVariableStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getStateVariableStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for SetupConfigArg objects
     */
    public static IKeyValueStore<SetupConfigArg> getSetupConfigArgStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getSetupConfigArgStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for EventServiceEvent objects
     */
    public static IKeyValueStore<EventServiceEvent> getEventServiceEventStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getEventServiceEventStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for SequenceConfig objects
     */
    public static IKeyValueStore<SequenceConfig> getSequenceConfigStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getSequenceConfigStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for ControlConfig objects
     */
    public static IKeyValueStore<ControlConfig> getControlConfigStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getControlConfigStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for SequenceConfigArg objects
     */
    public static IKeyValueStore<SequenceConfigArg> getSequenceConfigArgStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getSequenceConfigArgStore(settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for ControlConfigArg objects
     */
    public static IKeyValueStore<ControlConfigArg> getControlConfigArgStore(KvsSettings settings, ActorRefFactory system) {
        return JKeyValueStore.getControlConfigArgStore(settings, system);
    }


    // --- Blocking key/value stores ---


    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new BlockingKeyValueStore for StatusEvent objects
     */
    public static IBlockingKeyValueStore<StatusEvent> getBlockingStatusEventStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getStatusEventStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for ObserveEvent objects
     */
    public static IBlockingKeyValueStore<ObserveEvent> getBlockingObserveEventStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getObserveEventStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for SystemEvent objects
     */
    public static IBlockingKeyValueStore<SystemEvent> getBlockingSystemEventStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getSystemEventStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for SetupConfig objects
     */
    public static IBlockingKeyValueStore<SetupConfig> getBlockingSetupConfigStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getSetupConfigStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for CurrentState objects
     */
    public static IBlockingKeyValueStore<CurrentState> getBlockingCurrentStateStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getCurrentStateStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for DemandState objects
     */
    public static IBlockingKeyValueStore<DemandState> getBlockingDemandStateStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getDemandStateStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for StateVariable objects
     */
    public static IBlockingKeyValueStore<StateVariable> getBlockingStateVariableStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getStateVariableStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for SetupConfigArg objects
     */
    public static IBlockingKeyValueStore<SetupConfigArg> getBlockingSetupConfigArgStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getSetupConfigArgStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for EventServiceEvent objects
     */
    public static IBlockingKeyValueStore<EventServiceEvent> getBlockingEventServiceEventStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getEventServiceEventStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for SequenceConfig objects
     */
    public static IBlockingKeyValueStore<SequenceConfig> getBlockingSequenceConfigStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getSequenceConfigStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for ControlConfig objects
     */
    public static IBlockingKeyValueStore<ControlConfig> getBlockingControlConfigStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getControlConfigStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for SequenceConfigArg objects
     */
    public static IBlockingKeyValueStore<SequenceConfigArg> getBlockingSequenceConfigArgStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getSequenceConfigArgStore(timeout, settings, system);
    }

    /**
     * @param settings Redis server settings
     * @param system Akka env required by RedisClient
     * @return a new KeyValueStore for ControlConfigArg objects
     */
    public static IBlockingKeyValueStore<ControlConfigArg> getBlockingControlConfigArgStore(Duration timeout, KvsSettings settings, ActorRefFactory system) {
        return JBlockingKeyValueStore.getControlConfigArgStore(timeout, settings, system);
    }

}
