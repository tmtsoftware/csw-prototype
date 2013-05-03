package org.tmt.csw.cs.core.git;

import org.junit.*;
import org.tmt.csw.cs.core.ConfigData;
import org.tmt.csw.cs.core.ConfigFileHistory;
import org.tmt.csw.cs.core.ConfigFileInfo;
import org.tmt.csw.cs.core.ConfigString;

import java.io.File;
import java.util.List;

/**
 * Test access to GitConfigManager from java
 */
public class TestJGitConfigManager {
    private static final String path1 = "some/test1/TestConfig1";
    private static final String path2 = "some/test2/TestConfig2";

    private static final String contents1 = "Contents of some file...\n";
    private static final String contents2 = "New contents of some file...\n";
    private static final String contents3 = "Even newer contents of some file...\n";

    private static final String comment1 = "create comment";
    private static final String comment2 = "update 1 comment";
    private static final String comment3 = "update 2 comment";

    // Test creating a GitConfigManager, storing and retrieving some files
    @Test
    public void testGitConfigManager() {

        String tmpDir = System.getProperty("java.io.tmpdir");
        File gitDir = new File(tmpDir, "cstest");
        File gitMainRepo = new File(tmpDir, "cstestMainRepo");
        System.out.println("Local repo = " + gitDir + ", remote = " + gitMainRepo);

        // Delete the main and local test repositories (Only use this in test cases!)
        GitConfigManager.deleteLocalRepo(gitMainRepo);
        GitConfigManager.initBareRepo(gitMainRepo);
        GitConfigManager.deleteLocalRepo(gitDir);

        // create a new repository
        JGitConfigManager manager = new JGitConfigManager(gitDir, gitMainRepo.getPath());
        if (manager.exists(path1)) {
            manager.delete(path1, "deleted");
        }
        if (manager.exists(path2)) {
            manager.delete(path2, "deleted");
        }

        // Add, then update the file twice
        String createId1 = manager.create(path1, new ConfigString(contents1), comment1);
        String createId2 = manager.create(path2, new ConfigString(contents1), comment1);
        String updateId1 = manager.update(path1, new ConfigString(contents2), comment2);
        String updateId2 = manager.update(path1, new ConfigString(contents3), comment3);

        // Check that we can access each version
        ConfigData data1 = manager.get(path1);
        assert (data1.toString().equals(contents3));

        ConfigData data2 = manager.get(path1, createId1);
        assert (data2.toString().equals(contents1));

        ConfigData data3 = manager.get(path1, updateId1);
        assert (data3.toString().equals(contents2));

        ConfigData data4 = manager.get(path1, updateId2);
        assert (data4.toString().equals(contents3));

        ConfigData data5 = manager.get(path2);
        assert (data5.toString().equals(contents1));

        ConfigData data6 = manager.get(path2, createId2);
        assert (data6.toString().equals(contents1));

        // test history()
        List<ConfigFileHistory> historyList1 = manager.history(path1);
        List<ConfigFileHistory> historyList2 = manager.history(path2);
        assert (historyList1.size() >= 3);
        assert (historyList2.size() >= 1);
        assert (historyList1.get(0).comment().equals(comment1));
        assert (historyList2.get(0).comment().equals(comment1));
        assert (historyList1.get(1).comment().equals(comment2));
        assert (historyList1.get(2).comment().equals(comment3));

        // Test list()
        List<ConfigFileInfo> list = manager.list();
        assert (list.size() == 2);
        for (ConfigFileInfo info : list) {
            if (info.path().equals(path1)) {
                assert (info.comment().equals(comment3));
            } else if (info.path().equals(path2)) {
                assert (info.comment().equals(comment1));
            } else {
                throw new Error("Test failed for " + info);
            }
        }
    }
}

