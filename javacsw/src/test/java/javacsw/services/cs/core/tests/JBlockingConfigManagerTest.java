package javacsw.services.cs.core.tests;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer;
import javacsw.services.cs.IBlockingConfigManager;
import javacsw.services.cs.akka.tests.JTestRepo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests synchronous access to the config service from java
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class JBlockingConfigManagerTest {

    private static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    // Test creating a ConfigManager, storing and retrieving some files
    @Test
    public void testConfigManager() throws Exception {
        IBlockingConfigManager manager = JTestRepo.getTestRepoBlockingConfigManager(system);
        JConfigManagerTestHelper.runTests(manager, false);

        // Start the config service annex http server and wait for it to be ready for connections
        // (In normal operations, this server would already be running)
        ConfigServiceAnnexServer annexServer = new ConfigServiceAnnexServer(false);
        JConfigManagerTestHelper.runTests(manager, true);
        annexServer.shutdown();
    }

}

