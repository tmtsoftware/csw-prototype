package javacsw.services.loc;

import csw.services.loc.ComponentId;
import csw.services.loc.Connection;
import csw.services.loc.Connection.*;
import csw.services.loc.ConnectionType;

/**
 * Java API for location service connections
 */
@SuppressWarnings("unused")
public class JConnection {
    /**
     * Represents a connection to a remote akka actor based component
     * @param componentId the id of the target component
     * @return the connection object
     */
    public static AkkaConnection akkaConnection(ComponentId componentId) {
        return new AkkaConnection(componentId);
    }

    /**
     * A connection to a remote http based component
     *
     * @param componentId the id of the target component
     * @return the connection object
     */
    public static HttpConnection httpConnection(ComponentId componentId) {
        return new HttpConnection(componentId);
    }

    /**
     * A connection to a remote tcp based component
     *
     * @param componentId the id of the target component
     * @return the connection object
     */
    public static TcpConnection tcpConnection(ComponentId componentId) {
        return new TcpConnection(componentId);
    }

    /**
     * Gets a Connection from a string as output by Connection.toString
     *
     * @param s a string in the format output by Connection.toString
     * @return the Connection object
     */
    public static Connection parseConnection(String s) {
        return csw.services.loc.Connection$.MODULE$.apply(s).get();
    }

    /**
     * Gets a Connection based on the component id and connection type
     *
     * @param componentId the component id
     * @param connectionType the connection type
     * @return the connection object
     */
    public static Connection createConnection(ComponentId componentId, ConnectionType connectionType) {
        return csw.services.loc.Connection$.MODULE$.apply(componentId, connectionType);
    }
}
