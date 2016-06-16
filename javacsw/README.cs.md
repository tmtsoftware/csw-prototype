Java API for the Config Service
===============================

The Java API for the config service provides synchronous/blocking and asynchronous/non-blocking versions.

Here is an example of how to use the Java blocking API to create, update and get files:

```
    public static void runTests(JBlockingConfigManager manager, Boolean oversize) {

        // Add, then update the file twice
        ConfigId createId1 = manager.create(path1, JConfigData.create(contents1), oversize, comment1);
        ConfigId createId2 = manager.create(path2, JConfigData.create(contents1), oversize, comment1);
        ConfigId updateId1 = manager.update(path1, JConfigData.create(contents2), comment2);
        ConfigId updateId2 = manager.update(path1, JConfigData.create(contents3), comment3);

        // Check that we can access each version
        JBlockingConfigData data1 = manager.get(path1).get();
        assert (data1.toString().equals(contents3));

        JBlockingConfigData data2 = manager.get(path1, createId1).get();
        assert (data2.toString().equals(contents1));

        JBlockingConfigData data3 = manager.get(path1, updateId1).get();
        assert (data3.toString().equals(contents2));

        JBlockingConfigData data4 = manager.get(path1, updateId2).get();
        assert (data4.toString().equals(contents3));

        JBlockingConfigData data5 = manager.get(path2).get();
        assert (data5.toString().equals(contents1));

        JBlockingConfigData data6 = manager.get(path2, createId2).get();
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

        // Test listing the files in the config service
        checkConfigFileInfo(manager.list());
    }
```