package javacsw.services.events.tests;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import csw.services.events.EventService.*;
import csw.services.events.EventServiceSettings;
import csw.util.config.DoubleKey;

import static javacsw.util.config.JItems.*;

import csw.util.config.Events.*;
import csw.util.config.IntKey;
import csw.util.config.StringKey;
import javacsw.services.events.IEventService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

@SuppressWarnings({"OptionalGetWithoutIsPresent", "OptionalUsedAsFieldOrParameterType"})
public class JEventServiceTests {

  // Keys used in test
  private static final IntKey infoValue = new IntKey("infoValue");
  private static final StringKey infoStr = new StringKey("infoStr");
  private static final DoubleKey exposureTime = new DoubleKey("exposureTime");

  private static ActorSystem system;

  // The target for this test
  private static IEventService eventService;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create();
    EventServiceSettings settings = IEventService.getEventServiceSettings(system);
    eventService = IEventService.getEventService(settings, system);
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

    eventService.publish(event1).get();
    StatusEvent val1 = (StatusEvent) eventService.get(prefix1).get().get();
    assertEquals(val1.prefix(), prefix1);
    assertEquals(val1.get(infoValue).get().head(), 1);
    assertEquals(val1.get(infoStr).get().head(), "info 1");

    eventService.publish(event2).get();
    StatusEvent val2 = (StatusEvent) eventService.get(prefix2).get().get();
    assertEquals(val2.prefix(), prefix2);
    assertEquals(val2.get(infoValue).get().head(), 2);
    assertEquals(val2.get(infoStr).get().head(), "info 2");

    eventService.delete(prefix1).get();
    eventService.delete(prefix2).get();
    assertTrue(!eventService.get(prefix1).get().isPresent());
    assertTrue(!eventService.get(prefix2).get().isPresent());
  }

  @Test
  public void TestSetGetAndGetHistory() throws Exception {
    String prefix = "tcs.testPrefix";
    ObserveEvent event = ObserveEvent(prefix)
      .add(jset(exposureTime, 2.0));

    int n = 3;
    eventService.publish(event.add(jset(exposureTime, 3.0)), n).get();
    eventService.publish(event.add(jset(exposureTime, 4.0)), n).get();
    eventService.publish(event.add(jset(exposureTime, 5.0)), n).get();
    eventService.publish(event.add(jset(exposureTime, 6.0)), n).get();
    eventService.publish(event.add(jset(exposureTime, 7.0)), n).get();

    Optional<EventServiceEvent> v = eventService.get(prefix).get();
    assertTrue(v.isPresent());
    ObserveEvent ev = ((ObserveEvent) v.get());
    Double expTime = jvalue(jitem(ev, exposureTime));
    assertTrue(expTime == 7.0);

    List<EventServiceEvent> h = eventService.getHistory(prefix, n + 1).get();
    assertTrue(h.size() == n + 1);
    for (int i = 0; i < n; i++) {
      System.out.println("History: " + i + ": " + h.get(i));
    }
    eventService.delete(prefix).get();
  }

  // Used to test that event handler was called
  static Optional<EventServiceEvent> eventReceived = Optional.empty();

  // Called when an event is received
  static IEventService.EventHandler eventHandler = ev -> eventReceived = Optional.of(ev);

  @Test
  public void TestSubscribeMethod() throws Exception {
    String prefix = "tcs.test4";
    StatusEvent event = StatusEvent(prefix)
      .add(jset(infoValue, 4))
      .add(jset(infoStr, "info 4"));
    EventMonitor monitor = eventService.subscribe(Optional.empty(), Optional.of(eventHandler), prefix);
    try {
      Thread.sleep(500); // wait for actor to start
      eventService.publish(event);
      Thread.sleep(500); // wait for redis to react
      assertTrue(eventReceived.isPresent());
      assertTrue(eventReceived.get().equals(event));
    } finally {
      monitor.stop();
    }
 }
}
