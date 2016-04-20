package csw.services.ts;

import akka.actor.*;
import akka.japi.Creator;
import akka.testkit.JavaTestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.*;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.ExecutionContextExecutor;

/**
 * Tests using the time service from Java.
 * (This is a java version of the Scala TimeServiceTest.)
 */
public class JTimeServiceTest {

    private static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void basicJavaTimeTests() throws Exception {
        // Assume an eastern time zone for tests
//        Clock eclock = Clock.system(ZoneId.of("America/New_York"));
//        int hoursFromNyToHI = 6;

        // Can't do much test to see now equal implying bad clocks
//        LocalTime nyNow = LocalTime.now(eclock);
        LocalTime hwNow = TimeService.hawaiiLocalTimeNow();
        LocalTime now = TimeService.localTimeNow();

        // Try to determine that we have Hawaii time
        assert (!hwNow.equals(now)); // Not the same, good

        LocalTime utcNow = TimeService.UTCTimeNow();
//        LocalTime gpsNow = TimeService.GPSTimeNow();
        LocalTime taiNow = TimeService.TAITimeNow();

//        assert (gpsNow.isAfter(utcNow));
        assert (taiNow.isAfter(utcNow));
//        assert (taiNow.isAfter(gpsNow));
    }

    @Test
    public void callSchedulerOnce() throws Exception {
        new JavaTestKit(system) {
            {
                ActorRef timerTest = system.actorOf(JTestScheduler.props("tester", getRef()));
                timerTest.tell("once", getRef());

                new Within(duration("10 seconds")) {
                    protected void run() {
                        expectMsgEquals("done");
                    }
                };
            }
        };
    }

    @Test
    public void with5CountsIn5Seconds() throws Exception {
        LoggingAdapter logger = Logging.getLogger(system, this);
        new JavaTestKit(system) {
            {
                ActorRef timerTest = system.actorOf(JTestScheduler.props("tester", getRef()));
                timerTest.tell("five", getRef());

                new Within(duration("10 seconds")) {
                    protected void run() {
                        Cancellable cancellable = expectMsgClass(Cancellable.class);
                        logger.info("Received cancellable: " + cancellable);
                        int count = expectMsgClass(Integer.class);
                        logger.info("Executed " + count + " scheduled messages");
                        assert (count == 5);
                        cancellable.cancel();
                    }
                };
            }
        };
    }

    private static class JTestScheduler extends TimeService.JTimeServiceScheduler {
        LoggingAdapter log = log();

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

        public void onReceive(Object message) throws Exception {
            if (message instanceof String) {
                if (message.equals("once")) {
                    log.info("Received once start");
                    scheduleOnce(TimeService.localTimeNow().plusSeconds(5), context().self(), "once-done");
                } else if (message.equals("five")) {
                    log.info("Received multi start");
                    Cancellable c = schedule(TimeService.localTimeNow().plusSeconds(1), java.time.Duration.ofSeconds(1), context().self(), "count");
                    caller.tell(c, self()); //Return the cancellable

                } else if (message.equals("count")) {
                    count = count + 1;
                    log.info("Count: " + count);
                    if (count >= 5) caller.tell(count, self());

                } else if (message.equals("once-done")) {
                    log.info("Received Done");
                    caller.tell("done", self());
                }
            } else
                unhandled(message);
        }

        @Override
        public ExecutionContextExecutor ec() {
            return getContext().dispatcher();
        }
    }
}

