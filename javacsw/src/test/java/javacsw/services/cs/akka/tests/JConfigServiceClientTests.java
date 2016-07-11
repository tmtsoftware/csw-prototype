package javacsw.services.cs.akka.tests;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer;
import csw.services.cs.akka.ConfigServiceActor;
import csw.services.loc.LocationService;
import javacsw.services.cs.JConfigManager;
import javacsw.services.cs.akka.JBlockingConfigServiceClient;
import javacsw.services.cs.akka.JConfigServiceClient;
import javacsw.services.cs.akka.JConfigServiceFactory;
import javacsw.services.cs.core.tests.JConfigManagerTestHelper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

/**
 * Tests the JConfigManager class
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class JConfigServiceClientTests {

    private static ActorSystem system;
    Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(60, TimeUnit.SECONDS));

    @BeforeClass
    public static void setup() {
        LocationService.initInterface();
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    // Test creating a JConfigServiceClient, storing and retrieving some files
    @Test
    public void configServiceClientTests() throws Exception {
        JConfigManager repoManager = JTestRepo.getTestRepoConfigManager(system);
        // Note: For this test we create the actor here, normally you would look it up with the location service
        ActorRef configServiceActor = system.actorOf(ConfigServiceActor.props(repoManager.getManager(), false));
        JConfigServiceClient client = JConfigServiceFactory.getConfigServiceClient(configServiceActor, system, timeout);

        JConfigManagerTestHelper.runTests(client, false);

        // Start the config service annex http server and wait for it to be ready for connections
        // (In normal operations, this server would already be running)
        ConfigServiceAnnexServer annexServer = new ConfigServiceAnnexServer(false);
        JConfigManagerTestHelper.runTests(client, true);
        annexServer.shutdown();
        system.stop(configServiceActor);
    }

    // Test creating a JBlockingConfigServiceClient, storing and retrieving some files
    @Test
    public void blockingConfigServiceClientTests() throws Exception {
        JConfigManager repoManager = JTestRepo.getTestRepoConfigManager(system);
        // Note: For this test we create the actor here, normally you would look it up with the location service
        ActorRef configServiceActor = system.actorOf(ConfigServiceActor.props(repoManager.getManager(), false));
        JBlockingConfigServiceClient client = JConfigServiceFactory.getBlockingConfigServiceClient(configServiceActor, system, timeout);

        JConfigManagerTestHelper.runTests(client, false);

        // Start the config service annex http server and wait for it to be ready for connections
        // (In normal operations, this server would already be running)
        ConfigServiceAnnexServer annexServer = new ConfigServiceAnnexServer(false);
        JConfigManagerTestHelper.runTests(client, true);
        annexServer.shutdown();
        system.stop(configServiceActor);
    }

    // FIXME This test passes when run alone, but times out when all javacsw tests are run.
//    // Test looking up a running config service by name with the location service
//    @Test
//    public void locationServiceLookupTests() throws Exception {
//        // Note: For this test we create the actor here, normally you would just look it up with the location service
//        JConfigManager repoManager = JTestRepo.getTestRepoConfigManager(system);
//        ActorRef configServiceActor = system.actorOf(ConfigServiceActor.props(repoManager.getManager(), true));
//
//        // Client code to get the default config service from the location service and wrap it in a blocking client
//        JBlockingConfigServiceClient client = JConfigServiceFactory.getBlockingConfigServiceClient(system, timeout);
//
//        JConfigManagerTestHelper.runTests(client, false);
//        system.stop(configServiceActor);
//    }
}