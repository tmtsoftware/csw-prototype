package javacsw.services.ts.tests;

import akka.actor.*;
import akka.japi.Creator;
import akka.testkit.javadsl.TestKit;
import csw.services.ts.AbstractTimeServiceScheduler;
import javacsw.services.ts.JTimeService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import java.time.*;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.ExecutionContextExecutor;

/**
 * Tests using the time service from Java.
 */
public class JTimeServiceTest {

  private static ActorSystem system;
  private static LoggingAdapter log;


  @BeforeClass
  public static void setup() {
    system = ActorSystem.create();
    log = Logging.getLogger(system, "JTimeServiceTest");
  }

  @AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void basicJavaTimeTests() throws Exception {
    LocalTime hwNow = JTimeService.hawaiiLocalTimeNow();
    LocalTime now = JTimeService.localTimeNow();

    // Try to determine that we have Hawaii time
    assertTrue(!hwNow.equals(now)); // Not the same, good

    LocalTime utcNow = JTimeService.UTCTimeNow();
    LocalTime taiNow = JTimeService.TAITimeNow();

    assertTrue(taiNow.isAfter(utcNow));
  }

  @Test
  public void callSchedulerOnce() throws Exception {
    new TestKit(system) {
      {
        ActorRef timerTest = system.actorOf(JTestScheduler.props("tester", getRef()));
        timerTest.tell("once", getRef());

        within(duration("10 seconds"), () -> expectMsgEquals("done"));
      }
    };
  }

  @Test
  public void with5CountsIn5Seconds() throws Exception {
    LoggingAdapter logger = Logging.getLogger(system, this);
    new TestKit(system) {
      {
        ActorRef timerTest = system.actorOf(JTestScheduler.props("tester", getRef()));
        timerTest.tell("five", getRef());

        within(duration("10 seconds"), () -> {
            Cancellable cancellable = expectMsgClass(Cancellable.class);
            logger.info("Received cancellable: " + cancellable);
            int count = expectMsgClass(Integer.class);
            logger.info("Executed " + count + " scheduled messages");
            assertTrue(count == 5);
            cancellable.cancel();
            return null;
        });
      }
    };
  }

  // Actor used in above test
  private static class JTestScheduler extends AbstractTimeServiceScheduler {
    private final String name;
    private final ActorRef caller;
    private int count = 0;

    JTestScheduler(String name, ActorRef caller) {
      this.name = name;
      this.caller = caller;
    }

    static Props props(final String name, final ActorRef caller) {
      return Props.create(new Creator<JTestScheduler>() {
        private static final long serialVersionUID = 1L;

        @Override
        public JTestScheduler create() throws Exception {
          return new JTestScheduler(name, caller);
        }
      });
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(String.class, message -> {
            switch (message) {
              case "once":
                log.info(name + ": Received once start");
                scheduleOnce(JTimeService.localTimeNow().plusSeconds(5), context().self(), "once-done");
                break;
              case "five":
                log.info(name + ": Received multi start");
                Cancellable c = schedule(JTimeService.localTimeNow().plusSeconds(1), Duration.ofSeconds(1), context().self(), "count");
                caller.tell(c, self()); //Return the cancellable


                break;
              case "count":
                count = count + 1;
                log.info(name + ": Count: " + count);
                if (count >= 5) caller.tell(count, self());

                break;
              case "once-done":
                log.info(name + ": Received Done");
                caller.tell("done", self());
                break;
            }
          })
          .build();

    }

    @Override
    public ExecutionContextExecutor ec() {
      return getContext().dispatcher();
    }
  }
}

