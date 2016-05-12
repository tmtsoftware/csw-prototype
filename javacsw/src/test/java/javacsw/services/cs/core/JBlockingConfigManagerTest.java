package javacsw.services.cs.core;

import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer;
import javacsw.services.cs.JBlockingConfigManager;
import javacsw.services.cs.akka.TestRepo;
import org.junit.Test;

/**
 * Tests synchronous access to the config service from java
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class JBlockingConfigManagerTest {

    // Test creating a ConfigManager, storing and retrieving some files
    @Test
    public void testConfigManager() throws Exception {
        JBlockingConfigManager manager = TestRepo.getTestRepoBlockingConfigManager();
        JConfigManagerTestHelper.runTests(manager, false);

        // Start the config service annex http server and wait for it to be ready for connections
        // (In normal operations, this server would already be running)
        ConfigServiceAnnexServer annexServer = new ConfigServiceAnnexServer(false);
        JConfigManagerTestHelper.runTests(manager, true);
        annexServer.shutdown();
    }

}

