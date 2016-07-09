package javacsw.services.kvs.tests;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import csw.util.config.DoubleKey;
import csw.util.config.Events.StatusEvent;
import csw.util.config.IntKey;
import csw.util.config.StringKey;
import javacsw.services.kvs.ITelemetryService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;

/**
 * Tests the Java API to the telemetry service
 */
public class JTelemetryServiceTest {
    private static final IntKey infoValue = new IntKey("infoValue");
    private static final StringKey infoStr = new StringKey("infoStr");
    private static final DoubleKey exposureTime = new DoubleKey("exposureTime");

    private static ActorSystem system;

    // The target for this test
    private static ITelemetryService ts;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
        ts = ITelemetryService.getTelemetryService(system);
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    // Note: In the tests below we just call .get() on the future results for simplicity.
    // In a real application, you could use other methods...

    @Test
    public void basicJavaTests() throws Exception {
        StatusEvent config1 = new StatusEvent("tcs.test")
                .jset(infoValue, 1)
                .jset(infoStr, "info 1");

        StatusEvent config2 = new StatusEvent("tcs.test")
                .jset(infoValue, 2)
                .jset(infoStr, "info 2");

        ts.set(config1).get();
        Optional<StatusEvent> val1Opt = ts.get(config1.prefix()).get();
        assertTrue(val1Opt.isPresent());
        StatusEvent val1 = val1Opt.get();
        assertTrue(val1.prefix().equals(config1.prefix()));
        assertTrue(val1.jvalue(infoValue) == 1);
        assertTrue(val1.jvalue(infoStr).equals("info 1"));

        ts.set(config2).get();
        Optional<StatusEvent> val2Opt = ts.get(config2.prefix()).get();
        assertTrue(val2Opt.isPresent());
        StatusEvent val2 = val2Opt.get();
        assertTrue(val2.prefix().equals(config2.prefix()));
        assertTrue(val2.jvalue(infoValue) == 2);
        assertTrue(val2.jvalue(infoStr).equals("info 2"));

       ts.delete(config1.prefix()).get();
        ts.delete(config2.prefix()).get();

        assertTrue(!ts.get(config1.prefix()).get().isPresent());
        assertTrue(!ts.get(config2.prefix()).get().isPresent());
    }

    @Test
    public void TestSetGetAndGetHistory() throws Exception {
        StatusEvent config = new StatusEvent("tcs.testPrefix")
                .jset(exposureTime, 2.0);

        int n = 3;
        ts.set(config.jset(exposureTime, 3.0), n).get();
        ts.set(config.jset(exposureTime, 4.0), n).get();
        ts.set(config.jset(exposureTime, 5.0), n).get();
        ts.set(config.jset(exposureTime, 6.0), n).get();
        ts.set(config.jset(exposureTime, 7.0), n).get();

        Optional<StatusEvent> v = ts.get(config.prefix()).get();
        assertTrue(v.isPresent());
        StatusEvent sc = v.get();
        Optional<Double> expTime = sc.jget(exposureTime, 0);
        assertTrue(expTime.isPresent());
        assertTrue(expTime.get() == 7.0);

        List<StatusEvent> h = ts.getHistory(config.prefix(), n + 1).get();
        assertTrue(h.size() == n + 1);
        for (int i = 0; i < n; i++) {
            System.out.println("History: " + i + ": " + h.get(i));
        }
        ts.delete(config.prefix()).get();
    }
}
