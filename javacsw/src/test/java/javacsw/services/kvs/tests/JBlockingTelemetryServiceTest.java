package javacsw.services.kvs.tests;

import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import csw.services.events.KvsSettings;
import csw.util.config.DoubleKey;
import csw.util.config.IntKey;
import csw.util.config.StringKey;
import javacsw.services.kvs.IBlockingTelemetryService;
import javacsw.services.kvs.IKeyValueStore;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import static junit.framework.TestCase.assertTrue;

public class JBlockingTelemetryServiceTest {

    // Keys used in test
    private static final IntKey infoValue = new IntKey("infoValue");
    private static final StringKey infoStr = new StringKey("infoStr");
    private static final DoubleKey exposureTime = new DoubleKey("exposureTime");

    // Amount of time to wait for Redis server to answer
    private static FiniteDuration timeout = Duration.create(5, "seconds");

    private static ActorSystem system;

    // The target for this test
    private static IBlockingTelemetryService bts;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
        KvsSettings settings = IKeyValueStore.getKvsSettings(system);
        bts = IBlockingTelemetryService.getTelemetryService(timeout, settings, system);
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }
/*
    @Test
    public void basicJavaTests() throws Exception {
        StatusEvent config1 = new StatusEvent("tcs.test")
                .jset(infoValue, 1)
                .jset(infoStr, "info 1");

        StatusEvent config2 = new StatusEvent("tcs.test")
                .jset(infoValue, 2)
                .jset(infoStr, "info 2");

        bts.set(config1);
        Optional<StatusEvent> val1Opt = bts.get(config1.prefix());
        assertTrue(val1Opt.isPresent());
        StatusEvent val1 = val1Opt.get();
        assertTrue(val1.prefix().equals("tcs.test"));
        assertTrue(val1.jvalue(infoValue) == 1);
        assertTrue(Objects.equals(val1.jvalue(infoStr), "info 1"));

        bts.set(config2);
        Optional<StatusEvent> val2Opt = bts.get(config2.prefix());
        assertTrue(val2Opt.isPresent());
        StatusEvent val2 = val2Opt.get();
        assertTrue(val2.prefix().equals("tcs.test"));
        assertTrue(val2.jvalue(infoValue) == 2);
        assertTrue(val2.jvalue(infoStr).equals("info 2"));

        bts.delete(config1.prefix());
        bts.delete(config2.prefix());

        assertTrue(!bts.get(config1.prefix()).isPresent());
        assertTrue(!bts.get(config2.prefix()).isPresent());
    }

    @Test
    public void TestSetGetAndGetHistory() throws Exception {
        StatusEvent config = new StatusEvent("tcs.testPrefix")
                .jset(exposureTime, 2.0);

        int n = 3;
        bts.set(config.jset(exposureTime, 3.0), n);
        bts.set(config.jset(exposureTime, 4.0), n);
        bts.set(config.jset(exposureTime, 5.0), n);
        bts.set(config.jset(exposureTime, 6.0), n);
        bts.set(config.jset(exposureTime, 7.0), n);

        Optional<StatusEvent> v = bts.get(config.prefix());
        assertTrue(v.isPresent());
        StatusEvent sc = v.get();
        Optional<Double> expTime = sc.jget(exposureTime, 0);
        assertTrue(expTime.isPresent());
        assertTrue(expTime.get() == 7.0);

        List<StatusEvent> h = bts.getHistory(config.prefix(), n + 1);
        assertTrue(h.size() == n + 1);
        for (int i = 0; i < n; i++) {
            System.out.println("History: " + i + ": " + h.get(i));
        }
        bts.delete(config.prefix());
    }


    // Test subscribing to telemetry using a subscriber actor to receive status events
    @Test
    public void TestSubscribingToTelemetry() throws Exception {
        final String prefix1 = "tcs.test1";
        final String prefix2 = "tcs.test2";

        final StatusEvent event1 = new StatusEvent(prefix1)
                .jset(infoValue, 1)
                .jset(infoStr, "info 1");

        final StatusEvent event2 = new StatusEvent(prefix2)
                .jset(infoValue, 1)
                .jset(infoStr, "info 2");

        new JavaTestKit(system) {
            {
                LoggingAdapter log = Logging.getLogger(system, this);
                // See below for actor class
                ActorRef mySubscriber = system.actorOf(MySubscriber.props(prefix1, prefix2));

                // This is just to make sure the actor has time to subscribe before we proceed
                Thread.sleep(1000);

                bts.set(event1);
                bts.set(event1.jset(infoValue, 2));

                bts.set(event2);
                bts.set(event2.jset(infoValue, 2));
                bts.set(event2.jset(infoValue, 3));

                // Make sure subscriber actor has received all events before proceeding
                Thread.sleep(1000);

                mySubscriber.tell(MySubscriber.GetResults.instance, getRef());
                new Within(Duration.create(5, TimeUnit.SECONDS)) {
                    protected void run() {
                        MySubscriber.Results result = expectMsgClass(MySubscriber.Results.class);
                        assertTrue(result.getCount1() == 2);
                        assertTrue(result.getCount2() == 3);
                        log.debug("Java telemetry subscriber test passed!");
                    }
                };
            }
        };
    }

    // Subscriber class used to test the Java API for subscribing to telemetry: JTelemetrySubscriber
    static class MySubscriber extends JTelemetrySubscriber {
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
            subscribe(prefix1, prefix2);

            receive(ReceiveBuilder
                    .match(StatusEvent.class, this::handleStatusEvent)
                    .match(GetResults.class, this::getResults)
                    .matchAny(t -> log.warning("Unexpected message: " + t)
                    ).build());
        }

        private void handleStatusEvent(StatusEvent event) {
            if (Objects.equals(event.prefix(), prefix1)) {
                count1++;
                assertTrue(event.jvalue(infoValue).equals(count1));
                assertTrue(Objects.equals(event.jvalue(infoStr), "info 1"));
            } else if (Objects.equals(event.prefix(), prefix2)) {
                count2++;
                assertTrue(event.jvalue(infoValue).equals(count2));
                assertTrue(Objects.equals(event.jvalue(infoStr), "info 2"));
            }
        }

        private void getResults(GetResults msg) {
            sender().tell(new Results(count1, count2), self());
        }
    }
    */
}
