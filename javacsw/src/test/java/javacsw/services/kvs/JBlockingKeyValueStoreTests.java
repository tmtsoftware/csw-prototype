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
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.Optional;

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

    @Test
    public void basicJavaTimeTests() throws Exception {
        SetupConfig config1 = new SetupConfig("tcs.test")
                .jset(infoValue, 1)
                .jset(infoStr, "info 1");

        SetupConfig config2 = new SetupConfig("tcs.test")
                .jset(infoValue, 2)
                .jset(infoStr, "info 2");

        kvs.set("test1", config1);
        Optional<SetupConfig> val1Opt = kvs.get("test1");
        assert(val1Opt.isPresent());
        SetupConfig val1 = val1Opt.get();
        assert(val1.prefix().equals("tcs.test"));
        assert(val1.get(infoValue).contains(1));
        assert(val1.get(infoStr).contains("info 1"));

        kvs.set("test2", config2);
        Optional<SetupConfig> val2Opt = kvs.get("test2");
        assert(val2Opt.isPresent());
        SetupConfig val2 = val2Opt.get();
        assert(val2.prefix().equals("tcs.test"));
        assert(val2.get(infoValue).contains(2));
        assert(val2.get(infoStr).contains("info 2"));

        assert(kvs.delete("test1"));
        assert(kvs.delete("test2"));

        assert(!kvs.get("test1").isPresent());
        assert(!kvs.get("test2").isPresent());

        assert(!kvs.delete("test1"));
        assert(!kvs.delete("test2"));
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
        assert(v.isPresent());
        SetupConfig sc = v.get();
        Optional<Double> expTime = sc.jget(exposureTime, 0);
        assert(expTime.isPresent());
        assert(expTime.get() == 7.0);

        List<SetupConfig> h = kvs.getHistory(key, n + 1);
        assert(h.size() == n + 1);
        for (int i = 0; i < n; i++) {
            System.out.println("History: " + i + ": " + h.get(i));
        }
        kvs.delete(key);
    }
}
