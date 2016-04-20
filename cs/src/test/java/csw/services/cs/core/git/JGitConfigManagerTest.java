package csw.services.cs.core.git;

import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer;
import csw.services.cs.JConfigData;
import csw.services.cs.JConfigManager;
import csw.services.cs.akka.TestGitRepo;
import csw.services.cs.core.ConfigFileHistory;
import csw.services.cs.core.ConfigFileInfo;
import csw.services.cs.core.ConfigId;
import csw.services.cs.core.ConfigString;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;
import java.util.concurrent.TimeUnit;

import java.io.File;
import java.util.List;

/**
 * Test access to GitConfigManager from java
 */
public class JGitConfigManagerTest {
    private static final File path1 = new File("some/test1/TestConfig1");
    private static final File path2 = new File("some/test2/TestConfig2");

    private static final String contents1 = "Contents of some file...\n";
    private static final String contents2 = "New contents of some file...\n";
    private static final String contents3 = "Even newer contents of some file...\n";

    private static final String comment1 = "create comment";
    private static final String comment2 = "update 1 comment";
    private static final String comment3 = "update 2 comment";

//    FiniteDuration timeout = new FiniteDuration(5, TimeUnit.SECONDS);

    // Test creating a GitConfigManager, storing and retrieving some files
    @Test
    public void testGitConfigManager() throws Exception {
        runTests(null, false);

        // Start the config service annex http server and wait for it to be ready for connections
        // (In normal operations, this server would already be running)
        ConfigServiceAnnexServer server = new ConfigServiceAnnexServer(false);
        runTests(server, true);
        server.shutdown();
    }

    void runTests(ConfigServiceAnnexServer annexServer, Boolean oversize) {
        JConfigManager manager = TestGitRepo.getJConfigManager();

        if (manager.exists(path1)) {
            manager.delete(path1, "deleted");
        }
        if (manager.exists(path2)) {
            manager.delete(path2, "deleted");
        }

        // Add, then update the file twice
        ConfigId createId1 = manager.create(path1, new ConfigString(contents1), oversize, comment1);
        ConfigId createId2 = manager.create(path2, new ConfigString(contents1), oversize, comment1);
        ConfigId updateId1 = manager.update(path1, new ConfigString(contents2), comment2);
        ConfigId updateId2 = manager.update(path1, new ConfigString(contents3), comment3);

        // Check that we can access each version
        JConfigData data1 = manager.get(path1);
        assert (data1.toString().equals(contents3));

        JConfigData data2 = manager.get(path1, createId1);
        assert (data2.toString().equals(contents1));

        JConfigData data3 = manager.get(path1, updateId1);
        assert (data3.toString().equals(contents2));

        JConfigData data4 = manager.get(path1, updateId2);
        assert (data4.toString().equals(contents3));

        JConfigData data5 = manager.get(path2);
        assert (data5.toString().equals(contents1));

        JConfigData data6 = manager.get(path2, createId2);
        assert (data6.toString().equals(contents1));

        // test history()
        List<ConfigFileHistory> historyList1 = manager.history(path1);
        List<ConfigFileHistory> historyList2 = manager.history(path2);
        assert (historyList1.size() >= 3);
        assert (historyList2.size() >= 1);
        assert (historyList1.get(0).comment().equals(comment3));
        assert (historyList2.get(0).comment().equals(comment1));
        assert (historyList1.get(1).comment().equals(comment2));
        assert (historyList1.get(2).comment().equals(comment1));

        // Test list()
        List<ConfigFileInfo> list = manager.list();
        assert (list.size() == 2 + 1); // +1 for README added by default when creating bare main repo
        for (ConfigFileInfo info : list) {
            if (info.path().equals(path1)) {
                assert (info.comment().equals(comment3));
            } else if (info.path().equals(path2)) {
                assert (info.comment().equals(comment1));
            } else if (!info.path().getName().equals("README")) {
                throw new Error("Test failed for " + info);
            }
        }

        if (annexServer != null) {
            System.out.println("Shutting down annex server");
            annexServer.shutdown();
        }
    }
}

