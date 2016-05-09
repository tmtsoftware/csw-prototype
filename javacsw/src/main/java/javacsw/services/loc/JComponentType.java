package javacsw.services.loc;

import csw.services.loc.ComponentType;
import csw.services.loc.ComponentType$;

/**
 * Java API for location service component types.
 */
@SuppressWarnings("unused")
public class JComponentType {

    /**
     * A container for components (assemblies and HCDs)
     */
    public static final ComponentType Container = ComponentType.Container$.MODULE$;

    /**
     * A component that controls a hardware device
     */
    public static final ComponentType HCD = ComponentType.HCD$.MODULE$;

    /**
     * A component that controls one or more HCDs or assemblies
     */
    public static final ComponentType Assembly = ComponentType.Assembly$.MODULE$;

    /**
     * A general purpose service component (actor and/or web service application)
     */
    public static final ComponentType Service = ComponentType.Service$.MODULE$;

    /**
     * Returns the named component type or an UnknownComponentTypeException exception if not known
     * @param name the simple name of the component type ("Container", "Assembly", "HCD", etc.)
     * @return the component type
     */
    public static ComponentType parse(String name) {
        return ComponentType$.MODULE$.apply(name).get();
    }
}
