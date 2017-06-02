package javacsw.util.itemSet;

import csw.util.itemSet.ItemSets.*;
import csw.util.itemSet.Item;
import csw.util.itemSet.StateVariable.*;
import csw.util.itemSet.Struct;

import static javacsw.util.itemSet.JItems.*;

/**
 * A Java DSL for working with configurations
 */
public class JConfigDSL {
    /**
     * Returns a new SetupConfig
     * @param configKey identifies the target subsystem
     * @param items one or more items (keys with values and units)
     */
    public static Setup sc(String configKey, Item<?>... items) {
        return jadd((new Setup(configKey)), items);
    }

    /**
     * Returns a new ObserveConfig
     * @param configKey identifies the target subsystem
     * @param items one or more items (keys with values and units)
     */
    public static Observe oc(String configKey, Item<?>... items) {
        return jadd((new Observe(configKey)), items);
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

    /**
     * Returns a new DemandState
     * @param name the name of the struct
     * @param items one or more items (keys with values and units)
     */
    public static Struct struct(String name, Item<?>... items) {
        return jadd((new Struct(name)), items);
    }
}
