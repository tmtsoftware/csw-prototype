package javacsw.services.events.tests;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import csw.services.events.EventService.*;
import csw.services.events.EventServiceSettings;

import static javacsw.util.config.JItems.*;

import csw.util.config.Events.*;
import csw.util.config.IntKey;
import csw.util.config.StringKey;
import javacsw.services.events.IEventService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Optional;

import static junit.framework.TestCase.assertTrue;

@SuppressWarnings({"OptionalGetWithoutIsPresent", "OptionalUsedAsFieldOrParameterType"})
public class JEventServiceTests {

  // Keys used in test
  private static final IntKey infoValue = new IntKey("infoValue");
  private static final StringKey infoStr = new StringKey("infoStr");
//  private static final DoubleKey exposureTime = new DoubleKey("exposureTime");

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
      eventService.publish(event).get();
      assertTrue(eventReceived.isPresent());
      assertTrue(eventReceived.get().equals(event));
    } finally {
      monitor.stop();
    }
  }

  @Test
  public void TestSubscribeAfterPublish() throws Exception {
    String prefix = "tcs.test5";
    StatusEvent event = StatusEvent(prefix)
      .add(jset(infoValue, 5))
      .add(jset(infoStr, "info 5"));
    eventService.publish(event).get();
    EventMonitor monitor = eventService.subscribe(Optional.empty(), Optional.of(eventHandler), prefix);
    Thread.sleep(500); // wait for actor to start
    try {
      assertTrue(eventReceived.isPresent());
      assertTrue(eventReceived.get().equals(event));
    } finally {
      monitor.stop();
    }
  }
}
