package javacsw.services.loc;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import csw.services.loc.ComponentId;
import csw.services.loc.Connection;
import csw.services.loc.LocationService;
import csw.services.loc.LocationService.*;
import csw.services.loc.LocationTrackerWorker;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import static scala.compat.java8.FutureConverters.*;

/**
 * Java API for the Location Service
 *
 * The Location Service is based on Multicast DNS (AppleTalk, Bonjour) and can be used to register and lookup
 * akka and http based services in the local network.
 *
 * Every application using the location service should call the initInterface() method once at startup,
 * before creating any actors.
 *
 * Note: On a mac, you can use the command line tool dns-sd to browse the registered services.
 * On Linux use avahi-browse.
 */
@SuppressWarnings("unused")
public class JLocationService {

    /**
     * Sets the "akka.remote.netty.tcp.hostname" and net.mdns.interface system properties, if not already
     * set on the command line (with -D), so that any services or akka actors created will use and publish the correct IP address.
     * This method should be called before creating any actors or web services that depend on the location service.
     * <p>
     * Note that calling this method overrides any setting for akka.remote.netty.tcp.hostname in the akka config file.
     * Since the application config is immutable and cached once it is loaded, I can't think of a way to take the config
     * setting into account here. This should not be a problem, since we don't want to hard code host names anyway.
     */
    public static void initInterface() {
        LocationService.initInterface();
    }

    /**
     * Represents a registered connection to an Akka service
     *
     * @param connection describes the connection
     * @param component the component being registered
     * @param prefix the component's prefix
     * @return the registration object
     */
    public static AkkaRegistration getAkkaRegistration(Connection.AkkaConnection connection, ActorRef component, String prefix) {
        return new AkkaRegistration(connection, component, prefix);
    }

    /**
     * Represents a registered connection to a HTTP service
     *
     * @param connection describes the connection
     * @param port the http port number
     * @param path the path to use for the URL
     * @return the registration object
     */
    public static HttpRegistration getHttpRegistration(Connection.HttpConnection connection, int port, String path) {
        return new HttpRegistration(connection, port, path);
    }

    /**
     * Registers a component connection with the location sevice.
     * The component will automatically be unregistered when the vm exists or when
     * unregister() is called on the result of this method.
     *
     * @param reg    component registration information
     * @param system akka system
     * @return a future result that completes when the registration has completed and can be used to unregister later
     */
    public static CompletableFuture<RegistrationResult> register(Registration reg, ActorSystem system) {
        return toJava(LocationService.register(reg, system)).toCompletableFuture();
    }

    /**
     * Registers the given service for the local host and the given port
     * (The full name of the local host will be used)
     *
     * @param componentId describes the component or service
     * @param actorRef    the actor reference for the actor being registered
     * @param prefix      indicates the part of a command service config that this service is interested in
     * @param system      the actor system (needed to manage the future)
     * @return            a future object that can be used to close the connection and unregister the service
     */
    public static CompletableFuture<RegistrationResult> registerAkkaConnection(ComponentId componentId, ActorRef actorRef,
                                                                               String prefix, ActorSystem system) {
        return toJava(LocationService.registerAkkaConnection(componentId, actorRef, prefix, system)).toCompletableFuture();
    }

    /**
     * Registers the given service for the local host and the given port
     * (The full name of the local host will be used)
     *
     * @param componentId describes the component or service
     * @param port        the port the service is running on
     * @param path        the path part of the URI (default: empty)
     * @param system      the actor system (needed to manage the future)
     * @return            a future object that can be used to close the connection and unregister the service
     */
    public static CompletableFuture<RegistrationResult> registerHttpConnection(ComponentId componentId, int port,
                                                                               String path, ActorSystem system) {
        return toJava(LocationService.registerHttpConnection(componentId, port, path, system)).toCompletableFuture();
    }

    /**
     * Unregisters the connection from the location service
     * (Note: it can take some time before the service is removed from the list: see
     * comments in registry.unregisterService())
     *
     * @param connection the connection to unregister
     */
    public static void unregisterConnection(Connection connection) {
        LocationService.unregisterConnection(connection);
    }

    /**
     * Convenience method that gets the location service information for a given set of services.
     *
     * @param connections set of requested connections
     * @param system      the caller's actor system
     * @param timeout     amount of time to wait before timing out
     * @return a future object describing the services found
     */
    public static CompletableFuture<LocationTrackerWorker.LocationsReady> resolve(Set<Connection> connections, ActorSystem system, Timeout timeout) {
        return toJava(JLocationServiceSup.resolve(connections, system, timeout)).toCompletableFuture();
    }

}
