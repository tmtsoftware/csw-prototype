package javacsw.services.cs.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import csw.services.cs.akka.ConfigServiceClient;

import java.util.concurrent.ExecutionException;

/**
 * Provides factory methods for accessing the config service.
 */
public class JConfigServiceFactory {

    /**
     * Returns a non-blocking client for accessing the config service
     *
     * @param configServiceActor the config service actor to use (May need to look up with the location service)
     * @param system             the akka actor system
     * @param timeout            amount of time to wait for replies from the config service actor
     * @return a Java-friendly client for the config service
     */
    public static JConfigServiceClient getConfigServiceClient(ActorRef configServiceActor, ActorSystem system, Timeout timeout) {
        ConfigServiceClient client = new ConfigServiceClient(configServiceActor, "Config Service", system, timeout);
        return new JConfigServiceClient(client, system);
    }

    /**
     * Looks up a running config service by name with the location service and, if found, returns a non-blocking client
     * for accessing it.
     *
     * @param name    the name of the config service actor to use (used to look up with the location service)
     * @param system  the akka actor system
     * @param timeout amount of time to wait for replies from the config service actor
     * @return a Java-friendly client for the config service
     */
    public static JConfigServiceClient getConfigServiceClient(String name, ActorSystem system, Timeout timeout)
            throws ExecutionException, InterruptedException {
        ActorRef configServiceActor = JConfigService.locateConfigService(name, system, timeout).get();
        return getConfigServiceClient(configServiceActor, system, timeout);
    }

    /**
     * Looks up the default running config service by name with the location service and, if found, returns a non-blocking client
     * for accessing it.
     *
     * @param system  the akka actor system
     * @param timeout amount of time to wait for replies from the config service actor
     * @return a Java-friendly client for the config service
     */
    public static JConfigServiceClient getConfigServiceClient( ActorSystem system, Timeout timeout)
            throws ExecutionException, InterruptedException {
        ActorRef configServiceActor = JConfigService.locateConfigService(system, timeout).get();
        return getConfigServiceClient(configServiceActor, system, timeout);
    }

    /**
     * Returns a blocking client for accessing the config service
     *
     * @param configServiceActor the config service actor to use (May need to look up with the location service)
     * @param system             the akka actor system
     * @param timeout            amount of time to wait for replies from the config service actor
     * @return a Java-friendly client for the config service
     */
    public static JBlockingConfigServiceClient getBlockingConfigServiceClient(ActorRef configServiceActor, ActorSystem system, Timeout timeout) {
        ConfigServiceClient client = new ConfigServiceClient(configServiceActor, "Config Service", system, timeout);
        return new JBlockingConfigServiceClient(client, system);
    }

    /**
     * Looks up a running config service by name with the location service and, if found, returns a blocking client
     * for accessing it.
     *
     * @param name    the name of the config service actor to use (used to look up with the location service)
     * @param system  the akka actor system
     * @param timeout amount of time to wait for replies from the config service actor
     * @return a Java-friendly, blocking client for the config service
     */
    public static JBlockingConfigServiceClient getBlockingConfigServiceClient(String name, ActorSystem system, Timeout timeout)
            throws ExecutionException, InterruptedException {
        ActorRef configServiceActor = JConfigService.locateConfigService(name, system, timeout).get();
        return getBlockingConfigServiceClient(configServiceActor, system, timeout);
    }

    /**
     * Looks up the default running config service with the location service and, if found, returns a blocking client
     * for accessing it.
     *
     * @param system  the akka actor system
     * @param timeout amount of time to wait for replies from the config service actor
     * @return a Java-friendly, blocking client for the config service
     */
    public static JBlockingConfigServiceClient getBlockingConfigServiceClient(ActorSystem system, Timeout timeout)
            throws ExecutionException, InterruptedException {
        ActorRef configServiceActor = JConfigService.locateConfigService(system, timeout).get();
        return getBlockingConfigServiceClient(configServiceActor, system, timeout);
    }
}
