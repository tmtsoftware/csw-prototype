package javacsw.services.cs.core.tests;

import csw.services.cs.core.ConfigFileHistory;
import csw.services.cs.core.ConfigFileInfo;
import csw.services.cs.core.ConfigId;
import javacsw.services.cs.IBlockingConfigData;
import javacsw.services.cs.IConfigData;
import javacsw.services.cs.IBlockingConfigManager;
import javacsw.services.cs.IConfigManager;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;


/**
 * Common test code for classes that implement the IConfigManager interface
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class JConfigManagerTestHelper {
  private static final File path1 = new File("some/test1/TestConfig1");
  private static final File path2 = new File("some/test2/TestConfig2");

  private static final String contents1 = "Contents of some file...\n";
  private static final String contents2 = "New contents of some file...\n";
  private static final String contents3 = "Even newer contents of some file...\n";

  private static final String comment1 = "create comment";
  private static final String comment2 = "update 1 comment";
  private static final String comment3 = "update 2 comment";

  public static void runTests(IConfigManager manager, Boolean oversize) throws ExecutionException, InterruptedException {

    // Note: In the tests below we just call .get() on the future results for simplicity.
    // In a real application, you could use other methods...

    if (manager.exists(path1).get()) {
      manager.delete(path1, "deleted").get();
    }
    if (manager.exists(path2).get()) {
      manager.delete(path2, "deleted").get();
    }

    // Add, then update the file twice
    Date date1 = new Date();
    Thread.sleep(100);
    ConfigId createId1 = manager.create(path1, IConfigData.create(contents1), oversize, comment1).get();
    ConfigId createId2 = manager.create(path2, IConfigData.create(contents1), oversize, comment1).get();
    Date date1a = new Date();
    Thread.sleep(100); // make sure date is different
    ConfigId updateId1 = manager.update(path1, IConfigData.create(contents2), comment2).get();
    Date date2 = new Date();
    Thread.sleep(100); // make sure date is different
    ConfigId updateId2 = manager.update(path1, IConfigData.create(contents3), comment3).get();
    Date date3 = new Date();

    // Check that we can access each version
    IConfigData data1 = manager.get(path1).get().get();
    assertTrue(data1.toFutureString().get().equals(contents3));

    IConfigData data2 = manager.get(path1, createId1).get().get();
    assertTrue(data2.toFutureString().get().equals(contents1));

    IConfigData data3 = manager.get(path1, updateId1).get().get();
    assertTrue(data3.toFutureString().get().equals(contents2));

    IConfigData data4 = manager.get(path1, updateId2).get().get();
    assertTrue(data4.toFutureString().get().equals(contents3));

    IConfigData data5 = manager.get(path2).get().get();
    assertTrue(data5.toFutureString().get().equals(contents1));

    IConfigData data6 = manager.get(path2, createId2).get().get();
    assertTrue(data6.toFutureString().get().equals(contents1));

    assertEquals(manager.get(path1, date1).get().get().toFutureString().get(), contents1);
    assertEquals(manager.get(path1, date1a).get().get().toFutureString().get(), contents1);
    assertEquals(manager.get(path1, date2).get().get().toFutureString().get(), contents2);
    assertEquals(manager.get(path1, date3).get().get().toFutureString().get(), contents3);

    // test history()
    List<ConfigFileHistory> historyList1 = manager.history(path1).get();
    List<ConfigFileHistory> historyList2 = manager.history(path2).get();
    assertTrue(historyList1.size() >= 3);
    assertTrue(historyList2.size() >= 1);
    assertTrue(historyList1.get(0).comment().equals(comment3));
    assertTrue(historyList2.get(0).comment().equals(comment1));
    assertTrue(historyList1.get(1).comment().equals(comment2));
    assertTrue(historyList1.get(2).comment().equals(comment1));

    // Test list()
    checkConfigFileInfo(manager.list().get());
  }


  public static void runTests(IBlockingConfigManager manager, Boolean oversize) throws InterruptedException {
    if (manager.exists(path1)) {
      manager.delete(path1, "deleted");
    }
    if (manager.exists(path2)) {
      manager.delete(path2, "deleted");
    }

    // Add, then update the file twice
    Date date1 = new Date();
    Thread.sleep(100);
    ConfigId createId1 = manager.create(path1, IConfigData.create(contents1), oversize, comment1);
    ConfigId createId2 = manager.create(path2, IConfigData.create(contents1), oversize, comment1);
    Date date1a = new Date();
    Thread.sleep(100); // make sure date is different
    ConfigId updateId1 = manager.update(path1, IConfigData.create(contents2), comment2);
    Date date2 = new Date();
    Thread.sleep(100); // make sure date is different
    ConfigId updateId2 = manager.update(path1, IConfigData.create(contents3), comment3);
    Date date3 = new Date();

    // Check that we can access each version
    IBlockingConfigData data1 = manager.get(path1).get();
    assertTrue(data1.toString().equals(contents3));

    IBlockingConfigData data2 = manager.get(path1, createId1).get();
    assertTrue(data2.toString().equals(contents1));

    IBlockingConfigData data3 = manager.get(path1, updateId1).get();
    assertTrue(data3.toString().equals(contents2));

    IBlockingConfigData data4 = manager.get(path1, updateId2).get();
    assertTrue(data4.toString().equals(contents3));

    IBlockingConfigData data5 = manager.get(path2).get();
    assertTrue(data5.toString().equals(contents1));

    IBlockingConfigData data6 = manager.get(path2, createId2).get();
    assertTrue(data6.toString().equals(contents1));

    assertEquals(manager.get(path1, date1).get().toString(), contents1);
    assertEquals(manager.get(path1, date1a).get().toString(), contents1);
    assertEquals(manager.get(path1, date2).get().toString(), contents2);
    assertEquals(manager.get(path1, date3).get().toString(), contents3);

    // test history()
    List<ConfigFileHistory> historyList1 = manager.history(path1);
    List<ConfigFileHistory> historyList2 = manager.history(path2);
    assertTrue(historyList1.size() >= 3);
    assertTrue(historyList2.size() >= 1);
    assertTrue(historyList1.get(0).comment().equals(comment3));
    assertTrue(historyList2.get(0).comment().equals(comment1));
    assertTrue(historyList1.get(1).comment().equals(comment2));
    assertTrue(historyList1.get(2).comment().equals(comment1));

    // Test list()
    checkConfigFileInfo(manager.list());
  }

  private static void checkConfigFileInfo(List<ConfigFileInfo> list) {
    assertTrue(list.size() == 2); // XXX add +1 for README added by default when creating a git main repo
    for (ConfigFileInfo info : list) {
      if (info.path().equals(path1)) {
        assertTrue(info.comment().equals(comment3));
      } else if (info.path().equals(path2)) {
        assertTrue(info.comment().equals(comment1));
      }
    }
  }

}
