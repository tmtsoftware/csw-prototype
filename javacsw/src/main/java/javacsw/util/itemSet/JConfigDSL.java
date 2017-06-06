package javacsw.util.itemSet;

import csw.util.param.Parameters.*;
import csw.util.param.Parameter;
import csw.util.param.StateVariable.*;
import csw.util.param.Struct;

import static javacsw.util.itemSet.JItems.*;

/**
 * A Java DSL for working with configurations
 */
public class JConfigDSL {
    /**
     * Returns a new Setup
     * @param info information associated with the setup
     * @param itemSetKey identifies the target subsystem
     * @param parameters one or more items (keys with values and units)
     */
    public static Setup sc(CommandInfo info, String itemSetKey, Parameter<?>... parameters) {
        return jadd((new Setup(info, itemSetKey)), parameters);
    }

    /**
     * Returns a new Observe
     * @param info information associated with the setup
     * @param itemSetKey identifies the target subsystem
     * @param parameters one or more items (keys with values and units)
     */
    public static Observe oc(CommandInfo info, String itemSetKey, Parameter<?>... parameters) {
        return jadd((new Observe(info, itemSetKey)), parameters);
    }

    /**
     * Returns a new CurrentState
     * @param configKey identifies the target subsystem
     * @param parameters one or more items (keys with values and units)
     */
    public static CurrentState cs(String configKey, Parameter<?>... parameters) {
        return jadd((new CurrentState(configKey)), parameters);
    }

    /**
     * Returns a new DemandState
     * @param configKey identifies the target subsystem
     * @param parameters one or more items (keys with values and units)
     */
    public static DemandState ds(String configKey, Parameter<?>... parameters) {
        return jadd((new DemandState(configKey)), parameters);
    }

    /**
     * Returns a new DemandState
     * @param name the name of the struct
     * @param parameters one or more items (keys with values and units)
     */
    public static Struct struct(String name, Parameter<?>... parameters) {
        return jadd((new Struct(name)), parameters);
    }
}
