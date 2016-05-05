package javacsw.services.loc;

import csw.services.loc.ComponentId;
import csw.services.loc.ComponentId$;
import csw.services.loc.ComponentType;

/**
 * Java API to the location service ComponentId class, which is Used to identify a component.
 */
public class JComponentId {
    public static ComponentId getComponentId(String name, ComponentType componentType) {
        return new ComponentId(name, componentType);
    }

    /**
     * Gets a ComponentId from a string, as output by ComponentId.toString
     */
    public static ComponentId parse(String name) {
        return ComponentId$.MODULE$.apply(name).get();
    }
}
