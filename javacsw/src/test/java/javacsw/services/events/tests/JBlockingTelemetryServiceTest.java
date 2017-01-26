package javacsw.services.events.tests;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;
import csw.services.loc.LocationService;
import csw.util.config.DoubleKey;
import csw.util.config.Events.*;
import csw.util.config.IntKey;
import csw.util.config.StringKey;
import javacsw.services.events.IBlockingTelemetryService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static javacsw.util.config.JItems.*;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class JBlockingTelemetryServiceTest {

  // Amount of time to wait for Redis server to answer
  private static Timeout timeout = new Timeout(FiniteDuration.create(15, TimeUnit.SECONDS));
  private static ActorSystem system;

  // Keys used in test
  private static final IntKey infoValue = new IntKey("infoValue");
  private static final StringKey infoStr = new StringKey("infoStr");
  private static final DoubleKey exposureTime = new DoubleKey("exposureTime");

  // The target for this test
  private static IBlockingTelemetryService bts;

  @BeforeClass
  public static void setup() {
    LocationService.initInterface();
    system = ActorSystem.create();

    // Get the telemetry service by looking up the name with the location service
    bts = IBlockingTelemetryService.getTelemetryService(IBlockingTelemetryService.defaultName, system, timeout);
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void testSetandGet() throws Exception {
    String prefix1 = "tcs.telem.test1";
    StatusEvent event1 = StatusEvent(prefix1)
      .add(jset(infoValue, 1))
      .add(jset(infoStr, "info 1"));

    String prefix2 = "tcs.telem.test2";
    StatusEvent event2 = StatusEvent(prefix2)
      .add(jset(infoValue, 2))
      .add(jset(infoStr, "info 2"));

    bts.publish(event1);
    StatusEvent val1 = bts.get(prefix1).get();
    assertEquals(val1.prefix(), prefix1);
    assertEquals(val1.get(infoValue).get().head(), 1);
    assertEquals(val1.get(infoStr).get().head(), "info 1");

    bts.publish(event2);
    StatusEvent val2 = bts.get(prefix2).get();
    assertEquals(val2.prefix(), prefix2);
    assertEquals(val2.get(infoValue).get().head(), 2);
    assertEquals(val2.get(infoStr).get().head(), "info 2");

    bts.delete(prefix1);
    bts.delete(prefix2);
    assertTrue(!bts.get(prefix1).isPresent());
    assertTrue(!bts.get(prefix2).isPresent());
  }

  @Test
  public void TestSetGetAndGetHistory() throws Exception {
    String prefix = "tcs.telem.testPrefix";
    StatusEvent event = StatusEvent(prefix)
      .add(jset(exposureTime, 2.0));

    int n = 3;
    bts.publish(event.add(jset(exposureTime, 3.0)), n);
    bts.publish(event.add(jset(exposureTime, 4.0)), n);
    bts.publish(event.add(jset(exposureTime, 5.0)), n);
    bts.publish(event.add(jset(exposureTime, 6.0)), n);
    bts.publish(event.add(jset(exposureTime, 7.0)), n);

    Optional<StatusEvent> v = bts.get(prefix);
    assertTrue(v.isPresent());
    StatusEvent ev = v.get();
    Double expTime = jvalue(jitem(ev, exposureTime));
    assertTrue(expTime == 7.0);

    List<StatusEvent> h = bts.getHistory(prefix, n + 1);
    assertTrue(h.size() == n + 1);
    for (int i = 0; i < n; i++) {
      System.out.println("History: " + i + ": " + h.get(i));
    }
    bts.delete(prefix);
  }


  // Test subscribing to telemetry using a subscriber actor to receive status events
  @Test
  public void TestSubscribingToTelemetry() throws Exception {
    final String prefix1 = "tcs.telem.test1";
    final String prefix2 = "tcs.telem.test2";

    final StatusEvent event1 = StatusEvent(prefix1)
      .add(jset(infoValue, 1))
      .add(jset(infoStr, "info 1"));

    final StatusEvent event2 = StatusEvent(prefix2)
      .add(jset(infoValue, 1))
      .add(jset(infoStr, "info 2"));

    new JavaTestKit(system) {
      {
        LoggingAdapter log = Logging.getLogger(system, this);
        // See below for actor class
        ActorRef mySubscriber = system.actorOf(MySubscriber.props(prefix1, prefix2));
        bts.subscribe(mySubscriber, true, prefix1, prefix2);

        // This is just to make sure the actor has time to subscribe before we proceed
        Thread.sleep(1000);

        bts.publish(event1);
        bts.publish(event1.add(jset(infoValue, 2)));

        bts.publish(event2);
        bts.publish(event2.add(jset(infoValue, 2)));
        bts.publish(event2.add(jset(infoValue, 3)));

        // Make sure subscriber actor has received all events before proceeding
        Thread.sleep(1000);

        mySubscriber.tell(MySubscriber.GetResults.instance, getRef());
        new Within(Duration.create(5, TimeUnit.SECONDS)) {
          protected void run() {
            MySubscriber.Results result = expectMsgClass(MySubscriber.Results.class);
            assertTrue(result.getCount1() == 2);
            assertTrue(result.getCount2() == 3);
            system.stop(mySubscriber);
            log.debug("Java telemetry subscriber test passed!");
          }
        };
      }
    };
  }

  // Subscriber class used to test the Java API for subscribing to telemetry: JTelemetrySubscriber
  static class MySubscriber extends AbstractActor {
    // Message to get the results
    public static class GetResults {
      public static final GetResults instance = new GetResults();

      private GetResults() {
      }
    }

    // Message holding the results
    public static class Results {
      int count1;
      int count2;

      public Results(int count1, int count2) {
        this.count1 = count1;
        this.count2 = count2;
      }

      public int getCount1() {
        return count1;
      }

      public int getCount2() {
        return count2;
      }
    }

    private final String prefix1;
    private final String prefix2;
    private int count1 = 0;
    private int count2 = 0;

    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    public static Props props(String prefix1, String prefix2) {
      return Props.create(new Creator<MySubscriber>() {
        private static final long serialVersionUID = 1L;

        @Override
        public MySubscriber create() throws Exception {
          return new MySubscriber(prefix1, prefix2);
        }
      });
    }

    public MySubscriber(String prefix1, String prefix2) {
      this.prefix1 = prefix1;
      this.prefix2 = prefix2;

      receive(ReceiveBuilder
        .match(StatusEvent.class, this::handleStatusEvent)
        .match(GetResults.class, this::getResults)
        .matchAny(t -> log.warning("Unexpected message: " + t)
        ).build());
    }

    private void handleStatusEvent(StatusEvent event) {
      if (event.prefix().equals(prefix1)) {
        count1++;
        assertEquals(event.get(infoValue).get().head(), count1);
        assertEquals(event.get(infoStr).get().head(), "info 1");
      } else if (event.prefix().equals(prefix2)) {
        count2++;
        assertEquals(event.get(infoValue).get().head(), count2);
        assertEquals(event.get(infoStr).get().head(), "info 2");
      }
    }

    private void getResults(GetResults msg) {
      sender().tell(new Results(count1, count2), self());
    }
  }
}
