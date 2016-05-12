package javacsw.services.cs.core;

import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer;
import javacsw.services.cs.JConfigManager;
import javacsw.services.cs.akka.JTestRepo;
import org.junit.Test;

/**
 * Tests the JConfigManager class
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class JConfigManagerTests {

    // Test creating a ConfigManager, storing and retrieving some files
    @Test
    public void testConfigManager() throws Exception {
        JConfigManager manager = JTestRepo.getTestRepoConfigManager();
        JConfigManagerTestHelper.runTests(manager, false);

        // Start the config service annex http server and wait for it to be ready for connections
        // (In normal operations, this server would already be running)
        ConfigServiceAnnexServer annexServer = new ConfigServiceAnnexServer(false);
        JConfigManagerTestHelper.runTests(manager, true);
        annexServer.shutdown();
    }
}