package javacsw.services.cs.core.tests;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer;
import javacsw.services.cs.JConfigManager;
import javacsw.services.cs.akka.tests.JTestRepo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests the JConfigManager class
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class JConfigManagerTests {

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

    // Test creating a ConfigManager, storing and retrieving some files
    @Test
    public void testConfigManager() throws Exception {
        JConfigManager manager = JTestRepo.getTestRepoConfigManager(system);
        JConfigManagerTestHelper.runTests(manager, false);

        // Start the config service annex http server and wait for it to be ready for connections
        // (In normal operations, this server would already be running)
        ConfigServiceAnnexServer annexServer = new ConfigServiceAnnexServer(false);
        JConfigManagerTestHelper.runTests(manager, true);
        annexServer.shutdown();
    }
}