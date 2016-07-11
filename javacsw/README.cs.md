Java API for the Config Service
===============================

See the [cs](../cs) project for an overview.

The Java API for the config service provides synchronous/blocking and asynchronous/non-blocking versions.

Here is an example of how to use the Java blocking API to create, update and get files:

```
        // Lookup already running default config service with the location service
        JBlockingConfigServiceClient client = JConfigServiceFactory.getBlockingConfigServiceClient(system, timeout);
        Boolean oversize = false; // Set to true to for special handling of large files

        // Add, then update the file twice
        ConfigId createId1 = manager.create(path1, JConfigData.create(contents1), oversize, comment1);
        ConfigId createId2 = manager.create(path2, JConfigData.create(contents1), oversize, comment1);
        ConfigId updateId1 = manager.update(path1, JConfigData.create(contents2), comment2);
        ConfigId updateId2 = manager.update(path1, JConfigData.create(contents3), comment3);

        // Check that we can access each version
        JBlockingConfigData data1 = manager.get(path1).get();
        JBlockingConfigData data2 = manager.get(path1, createId1).get();
        JBlockingConfigData data3 = manager.get(path1, updateId1).get();
        JBlockingConfigData data4 = manager.get(path1, updateId2).get();
        JBlockingConfigData data5 = manager.get(path2).get();
        JBlockingConfigData data6 = manager.get(path2, createId2).get();

        // Get the file history()
        List<ConfigFileHistory> historyList1 = manager.history(path1);
        List<ConfigFileHistory> historyList2 = manager.history(path2);

        // Get a list of all the files in the config service
        List<ConfigFileInfo> list = manager.list();
```