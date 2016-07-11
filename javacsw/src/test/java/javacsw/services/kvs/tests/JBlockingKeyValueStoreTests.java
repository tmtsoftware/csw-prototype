package javacsw.services.kvs.tests;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import csw.services.kvs.KvsSettings;
import csw.util.config.Configurations.*;
import csw.util.config.DoubleKey;
import csw.util.config.IntKey;
import csw.util.config.StringKey;
import javacsw.services.kvs.IBlockingKeyValueStore;
import javacsw.services.kvs.IKeyValueStore;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;

public class JBlockingKeyValueStoreTests {

    // Keys used in test
    private static final IntKey infoValue = new IntKey("infoValue");
    private static final StringKey infoStr = new StringKey("infoStr");
    private static final DoubleKey exposureTime = new DoubleKey("exposureTime");

    // Amount of time to wait for Redis server to answer
    private static Duration timeout = Duration.create(5, "seconds");

    private static ActorSystem system;

    // The target for this test
    private static IBlockingKeyValueStore<SetupConfig> kvs;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
        KvsSettings settings = IKeyValueStore.getKvsSettings(system);
        kvs = IBlockingKeyValueStore.getSetupConfigStore(timeout, settings, system);
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    /*
    @Test
    public void basicJavaTests() throws Exception {
        SetupConfig config1 = new SetupConfig("tcs.test")
                .jset(infoValue, 1)
                .jset(infoStr, "info 1");

        SetupConfig config2 = new SetupConfig("tcs.test")
                .jset(infoValue, 2)
                .jset(infoStr, "info 2");

        kvs.set("test1", config1);
        Optional<SetupConfig> val1Opt = kvs.get("test1");
        assertTrue(val1Opt.isPresent());
        SetupConfig val1 = val1Opt.get();
        assertTrue(val1.prefix().equals("tcs.test"));
        assertTrue(val1.jvalue(infoValue) == 1);
        assertTrue(Objects.equals(val1.jvalue(infoStr), "info 1"));

        kvs.set("test2", config2);
        Optional<SetupConfig> val2Opt = kvs.get("test2");
        assertTrue(val2Opt.isPresent());
        SetupConfig val2 = val2Opt.get();
        assertTrue(val2.prefix().equals("tcs.test"));
        assertTrue(val2.jvalue(infoValue) == 2);
        assertTrue(val2.jvalue(infoStr).equals("info 2"));

        assertTrue(kvs.delete("test1"));
        assertTrue(kvs.delete("test2"));

        assertTrue(!kvs.get("test1").isPresent());
        assertTrue(!kvs.get("test2").isPresent());

        assertTrue(!kvs.delete("test1"));
        assertTrue(!kvs.delete("test2"));
    }

    @Test
    public void TestSetGetAndGetHistory() throws Exception {
        SetupConfig config = new SetupConfig("tcs.testPrefix")
                .jset(exposureTime, 2.0);

        String key = "test";
        int n = 3;
        kvs.set(key, config.jset(exposureTime, 3.0), n);
        kvs.set(key, config.jset(exposureTime, 4.0), n);
        kvs.set(key, config.jset(exposureTime, 5.0), n);
        kvs.set(key, config.jset(exposureTime, 6.0), n);
        kvs.set(key, config.jset(exposureTime, 7.0), n);

        Optional<SetupConfig> v = kvs.get(key);
        assertTrue(v.isPresent());
        SetupConfig sc = v.get();
        Optional<Double> expTime = sc.jget(exposureTime, 0);
        assertTrue(expTime.isPresent());
        assertTrue(expTime.get() == 7.0);

        List<SetupConfig> h = kvs.getHistory(key, n + 1);
        assertTrue(h.size() == n + 1);
        for (int i = 0; i < n; i++) {
            System.out.println("History: " + i + ": " + h.get(i));
        }
        kvs.delete(key);
    }
    */
}
