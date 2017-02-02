package javacsw.services.events.tests;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;
import csw.services.events.EventService.*;

import static javacsw.util.config.JItems.*;

import csw.services.loc.LocationService;
import csw.util.config.Events.*;
import csw.util.config.IntKey;
import csw.util.config.StringKey;
import javacsw.services.events.IEventService;
import javacsw.services.events.IEventServiceAdmin;
import javacsw.services.events.JEventServiceAdmin;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertTrue;

@SuppressWarnings({"OptionalGetWithoutIsPresent", "OptionalUsedAsFieldOrParameterType"})
public class JEventServiceTests {

  private static Timeout timeout = new Timeout(FiniteDuration.create(15, TimeUnit.SECONDS));

  // Keys used in test
  private static final IntKey infoValue = new IntKey("infoValue");
  private static final StringKey infoStr = new StringKey("infoStr");

  private static ActorSystem system;

  // The target for this test
  private static IEventService eventService;

  @BeforeClass
  public static void setup() throws ExecutionException, InterruptedException {
    LocationService.initInterface();
    system = ActorSystem.create();

    // Get the event service by looking up the name with the location service
    eventService = IEventService.getEventService(IEventService.defaultName, system, timeout).get();
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
    String prefix = "tcs.test5";
    SystemEvent event = SystemEvent(prefix)
      .add(jset(infoValue, 5))
      .add(jset(infoStr, "info 5"));
    eventService.publish(event).get();
    EventMonitor monitor = eventService.subscribe(eventHandler, false, prefix);
    Thread.sleep(500); // wait for actor to start
    try {
      assertTrue(eventReceived.isPresent());
      assertTrue(eventReceived.get().equals(event));
    } finally {
      monitor.stop();
    }
  }
}
