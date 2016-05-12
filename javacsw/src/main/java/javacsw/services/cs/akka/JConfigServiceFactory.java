package javacsw.services.cs.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.util.Timeout;

import csw.services.cs.akka.ConfigServiceClient;

/**
 * Provides factory methods for accessing the config service.
 */
public class JConfigServiceFactory{

    /**
     * Returns a non-blocking client for accessing the config service
     * @param configServiceActor the config service actor to use (May need to look up with the location service)
     * @param system the akka actor system
     * @param timeout amount of time to wait for replies from the config service actor
     * @return a Java-friendly client for the config service
     */
    public static JConfigServiceClient getConfigServiceClient(ActorRef configServiceActor, ActorSystem system, Timeout timeout) {
        ConfigServiceClient client = new ConfigServiceClient(configServiceActor, "Config Service", system, timeout);
        return new JConfigServiceClient(client, system);
    }

    /**
     * Returns a blocking client for accessing the config service
     * @param configServiceActor the config service actor to use (May need to look up with the location service)
     * @param system the akka actor system
     * @param timeout amount of time to wait for replies from the config service actor
     * @return a Java-friendly client for the config service
     */
    public static JBlockingConfigServiceClient getBlockingConfigServiceClient(ActorRef configServiceActor, ActorSystem system, Timeout timeout) {
        ConfigServiceClient client = new ConfigServiceClient(configServiceActor, "Config Service", system, timeout);
        return new JBlockingConfigServiceClient(client, system);
    }

}
