package javacsw.services.kvs;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import csw.services.kvs.KvsSettings;
import csw.util.cfg.Configurations.*;
import csw.util.cfg.Key;
import javacsw.util.cfg.JConfigurations;
import javacsw.util.cfg.JSetupConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static javacsw.util.cfg.JStandardKeys.*;

public class JKeyValueStoreTests {

    // Keys used in test
    private static final Key infoValue = Key.createIntKey("infoValue");
    private static final Key infoStr = Key.createStringKey("infoStr");

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

    @Test
    public void basicJavaTimeTests() throws Exception {
        SetupConfig config1 = JConfigurations.createSetupConfig("tcs.test")
                .set(infoValue, 1)
                .set(infoStr, "info 1")
                .configType();

        SetupConfig config2 = JConfigurations.createSetupConfig("tcs.test")
                .set(infoValue, 2)
                .set(infoStr, "info 2")
                .configType();

        kvs.set("test1", config1).get();
        Optional<SetupConfig> val1Opt = kvs.get("test1").get();
        assert(val1Opt.isPresent());
        SetupConfig val1 = val1Opt.get();
        assert(val1.prefix().equals("tcs.test"));
        assert(val1.get(infoValue).contains(1));
        assert(val1.get(infoStr).contains("info 1"));

        kvs.set("test2", config2).get();
        Optional<SetupConfig> val2Opt = kvs.get("test2").get();
        assert(val2Opt.isPresent());
        SetupConfig val2 = val2Opt.get();
        assert(val2.prefix().equals("tcs.test"));
        assert(val2.get(infoValue).contains(2));
        assert(val2.get(infoStr).contains("info 2"));

        assert(kvs.delete("test1").get());
        assert(kvs.delete("test2").get());

        assert(!kvs.get("test1").get().isPresent());
        assert(!kvs.get("test2").get().isPresent());

        assert(!kvs.delete("test1").get());
        assert(!kvs.delete("test2").get());
    }

    @Test
    public void TestSetGetAndGetHistory() throws Exception {
        JSetupConfig config = JConfigurations.createSetupConfig("tcs.testPrefix")
                .set(exposureTime, 2.0);

        String key = "test";
        int n = 3;
        kvs.set(key, config.set(exposureTime, 3.0).configType(), n).get();
        kvs.set(key, config.set(exposureTime, 4.0).configType(), n).get();
        kvs.set(key, config.set(exposureTime, 5.0).configType(), n).get();
        kvs.set(key, config.set(exposureTime, 6.0).configType(), n).get();
        kvs.set(key, config.set(exposureTime, 7.0).configType(), n).get();

        Optional<SetupConfig> v = kvs.get(key).get();
        assert(v.isPresent());
        JSetupConfig sc = new JSetupConfig(v.get());
        OptionalDouble expTime = sc.getAsDouble(exposureTime);
        assert(expTime.isPresent());
        assert(expTime.getAsDouble() == 7.0);

        List<SetupConfig> h = kvs.getHistory(key, n + 1).get();
        assert(h.size() == n + 1);
        for (int i = 0; i < n; i++) {
            System.out.println("History: " + i + ": " + h.get(i));
        }
        kvs.delete(key).get();
    }
}
