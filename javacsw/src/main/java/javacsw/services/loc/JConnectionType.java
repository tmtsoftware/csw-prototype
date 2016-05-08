package javacsw.services.loc;

import csw.services.loc.ConnectionType;
import csw.services.loc.ConnectionType$;

/**
 * Java API for location service connection type: Indicates if it is an http server or an akka actor.
 */
@SuppressWarnings("unused")
public class JConnectionType {
    /**
     * Connection type of an Akka actor based service
     */
    public static final ConnectionType AkkaType = ConnectionType.AkkaType$.MODULE$;


    /**
     * Connection type of a HTTP based service
     */
    public static final ConnectionType HttpType = ConnectionType.HttpType$.MODULE$;

    /**
     * Gets a ConnectionType from the string value ("akka" or "http") or an UnknownConnectionTypeException
     */
    public static ConnectionType parse(String name) {
        return ConnectionType$.MODULE$.apply(name).get();
    }
}
