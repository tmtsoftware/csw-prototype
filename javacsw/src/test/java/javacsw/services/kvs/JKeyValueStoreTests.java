package javacsw.services.kvs;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import csw.services.kvs.KvsSettings;
import csw.util.config.Configurations.*;
import csw.util.config.DoubleKey;
import csw.util.config.IntKey;
import csw.util.config.StringKey;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;

public class JKeyValueStoreTests {

    // Keys used in test
    private static final IntKey infoValue = new IntKey("infoValue");
    private static final StringKey infoStr = new StringKey("infoStr");
    private static final DoubleKey exposureTime = new DoubleKey("exposureTime");

    private static ActorSystem system;

    // The target for this test
    private static IKeyValueStore<SetupConfig> kvs;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
        KvsSettings settings = IKeyValueStore.getKvsSettings(system);
        kvs = IKeyValueStore.getSetupConfigStore(settings, system);
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    // Note: In the tests below we just call .get() on the future results for simplicity.
    // In a real application, you could use other methods...
/*
    @Test
    public void basicJavaTests() throws Exception {
        SetupConfig config1 = new SetupConfig("tcs.test")
                .jset(infoValue, 1)
                .jset(infoStr, "info 1");

        SetupConfig config2 = new SetupConfig("tcs.test")
                .jset(infoValue, 2)
                .jset(infoStr, "info 2");

        kvs.set("test1", config1).get();
        Optional<SetupConfig> val1Opt = kvs.get("test1").get();
        assertTrue(val1Opt.isPresent());
        SetupConfig val1 = val1Opt.get();
        assertTrue(val1.prefix().equals("tcs.test"));
        assertTrue(val1.jvalue(infoValue) == 1);
        assertTrue(val1.jvalue(infoStr).equals("info 1"));

        kvs.set("test2", config2).get();
        Optional<SetupConfig> val2Opt = kvs.get("test2").get();
        assertTrue(val2Opt.isPresent());
        SetupConfig val2 = val2Opt.get();
        assertTrue(val2.prefix().equals("tcs.test"));
        assertTrue(val2.jvalue(infoValue) == 2);
        assertTrue(val2.jvalue(infoStr).equals("info 2"));

        assertTrue(kvs.delete("test1").get());
        assertTrue(kvs.delete("test2").get());

        assertTrue(!kvs.get("test1").get().isPresent());
        assertTrue(!kvs.get("test2").get().isPresent());

        assertTrue(!kvs.delete("test1").get());
        assertTrue(!kvs.delete("test2").get());
    }

    @Test
    public void TestSetGetAndGetHistory() throws Exception {
        SetupConfig config = new SetupConfig("tcs.testPrefix")
                .jset(exposureTime, 2.0);

        String key = "test";
        int n = 3;
        kvs.set(key, config.jset(exposureTime, 3.0), n).get();
        kvs.set(key, config.jset(exposureTime, 4.0), n).get();
        kvs.set(key, config.jset(exposureTime, 5.0), n).get();
        kvs.set(key, config.jset(exposureTime, 6.0), n).get();
        kvs.set(key, config.jset(exposureTime, 7.0), n).get();

        Optional<SetupConfig> v = kvs.get(key).get();
        assertTrue(v.isPresent());
        SetupConfig sc = v.get();
        Optional<Double> expTime = sc.jget(exposureTime, 0);
        assertTrue(expTime.isPresent());
        assertTrue(expTime.get() == 7.0);

        List<SetupConfig> h = kvs.getHistory(key, n + 1).get();
        assertTrue(h.size() == n + 1);
        for (int i = 0; i < n; i++) {
            System.out.println("History: " + i + ": " + h.get(i));
        }
        kvs.delete(key).get();
    }
    */
}
