package javacsw.services.cs.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer;
import csw.services.cs.akka.ConfigServiceActor;
import javacsw.services.cs.JConfigManager;
import javacsw.services.cs.core.JConfigManagerTestHelper;
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

    @BeforeClass
    public static void setup() {
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
        ActorRef configServiceActor = system.actorOf(ConfigServiceActor.props(repoManager.getManager()));
        Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(60, TimeUnit.SECONDS));
        JConfigServiceClient client = JConfigServiceFactory.getConfigServiceClient(configServiceActor, system, timeout);

        JConfigManagerTestHelper.runTests(client, false);

        // Start the config service annex http server and wait for it to be ready for connections
        // (In normal operations, this server would already be running)
        ConfigServiceAnnexServer annexServer = new ConfigServiceAnnexServer(false);
        JConfigManagerTestHelper.runTests(client, true);
        annexServer.shutdown();
    }

    // Test creating a JBlockingConfigServiceClient, storing and retrieving some files
    @Test
    public void blockingConfigServiceClientTests() throws Exception {
        JConfigManager repoManager = JTestRepo.getTestRepoConfigManager(system);
        // Note: For this test we create the actor here, normally you would look it up with the location service
        ActorRef configServiceActor = system.actorOf(ConfigServiceActor.props(repoManager.getManager()));
        Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(60, TimeUnit.SECONDS));
        JBlockingConfigServiceClient client = JConfigServiceFactory.getBlockingConfigServiceClient(configServiceActor, system, timeout);

        JConfigManagerTestHelper.runTests(client, false);

        // Start the config service annex http server and wait for it to be ready for connections
        // (In normal operations, this server would already be running)
        ConfigServiceAnnexServer annexServer = new ConfigServiceAnnexServer(false);
        JConfigManagerTestHelper.runTests(client, true);
        annexServer.shutdown();
    }
}