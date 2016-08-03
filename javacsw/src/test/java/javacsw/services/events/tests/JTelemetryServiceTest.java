package javacsw.services.events.tests;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import csw.util.config.DoubleKey;
import csw.util.config.Events.*;
import csw.util.config.IntKey;
import csw.util.config.StringKey;
import javacsw.services.events.ITelemetryService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static javacsw.util.config.JItems.*;
import static javacsw.util.config.JItems.jitem;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Tests the Java API to the telemetry service
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
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
  public void testSetandGet() throws Exception {
    String prefix1 = "tcs.test1";
    StatusEvent event1 = StatusEvent(prefix1)
      .add(jset(infoValue, 1))
      .add(jset(infoStr, "info 1"));

    String prefix2 = "tcs.test2";
    StatusEvent event2 = StatusEvent(prefix2)
      .add(jset(infoValue, 2))
      .add(jset(infoStr, "info 2"));

    ts.publish(event1).get();
    StatusEvent val1 = ts.get(prefix1).get().get();
    assertEquals(val1.prefix(), prefix1);
    assertEquals(val1.get(infoValue).get().head(), 1);
    assertEquals(val1.get(infoStr).get().head(), "info 1");

    ts.publish(event2).get();
    StatusEvent val2 = ts.get(prefix2).get().get();
    assertEquals(val2.prefix(), prefix2);
    assertEquals(val2.get(infoValue).get().head(), 2);
    assertEquals(val2.get(infoStr).get().head(), "info 2");

    ts.delete(prefix1).get();
    ts.delete(prefix2).get();
    assertTrue(!ts.get(prefix1).get().isPresent());
    assertTrue(!ts.get(prefix2).get().isPresent());
  }

  @Test
  public void TestSetGetAndGetHistory() throws Exception {
    String prefix = "tcs.testPrefix";
    StatusEvent event = StatusEvent(prefix)
      .add(jset(exposureTime, 2.0));

    int n = 3;
    ts.publish(event.add(jset(exposureTime, 3.0)), n).get();
    ts.publish(event.add(jset(exposureTime, 4.0)), n).get();
    ts.publish(event.add(jset(exposureTime, 5.0)), n).get();
    ts.publish(event.add(jset(exposureTime, 6.0)), n).get();
    ts.publish(event.add(jset(exposureTime, 7.0)), n).get();

    Optional<StatusEvent> v = ts.get(prefix).get();
    assertTrue(v.isPresent());
    StatusEvent ev = v.get();
    Double expTime = jvalue(jitem(ev, exposureTime));
    assertTrue(expTime == 7.0);

    List<StatusEvent> h = ts.getHistory(prefix, n + 1).get();
    assertTrue(h.size() == n + 1);
    for (int i = 0; i < n; i++) {
      System.out.println("History: " + i + ": " + h.get(i));
    }
    ts.delete(prefix).get();
  }
}
