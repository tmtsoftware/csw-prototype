package javacsw.util.config;

import csw.util.config.Configurations.*;
import csw.util.config.Item;
import csw.util.config.StateVariable;
import csw.util.config.StateVariable.*;

import static javacsw.util.config.JItems.*;

/**
 * A Java DSL for working with configurations
 */
public class JConfigDSL {
    /**
     * Returns a new SetupConfig
     * @param configKey identifies the target subsystem
     * @param items one or more items (keys with values and units)
     */
    public static SetupConfig sc(String configKey, Item<?>... items) {
        return jadd((new SetupConfig(configKey)), items);
    }

    /**
     * Returns a new ObserveConfig
     * @param configKey identifies the target subsystem
     * @param items one or more items (keys with values and units)
     */
    public static ObserveConfig oc(String configKey, Item<?>... items) {
        return jadd((new ObserveConfig(configKey)), items);
    }

    /**
     * Returns a new CurrentState
     * @param configKey identifies the target subsystem
     * @param items one or more items (keys with values and units)
     */
    public static CurrentState cs(String configKey, Item<?>... items) {
        return jadd((new CurrentState(configKey)), items);
    }

    /**
     * Returns a new DemandState
     * @param configKey identifies the target subsystem
     * @param items one or more items (keys with values and units)
     */
    public static DemandState ds(String configKey, Item<?>... items) {
        return jadd((new DemandState(configKey)), items);
    }
}
