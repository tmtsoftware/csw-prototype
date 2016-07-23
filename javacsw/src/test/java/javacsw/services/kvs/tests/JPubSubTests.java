package javacsw.services.kvs.tests;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.JavaTestKit;
import csw.services.kvs.KvsSettings;
import csw.util.config.Configurations.*;
import csw.util.config.DoubleKey;
import javacsw.services.kvs.IKeyValueStore;
import javacsw.services.kvs.JSubscriber;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import akka.japi.Creator;

import java.util.concurrent.TimeUnit;

/**
 * Tests Java API to subscribe to the key/value store.
 */
public class JPubSubTests {
    static final DoubleKey exposureTime = new DoubleKey("exposureTime");

    // Timeout waiting for a message
    static final FiniteDuration timeout = FiniteDuration.create(6, TimeUnit.SECONDS);

    private static ActorSystem system;

    // number of seconds to run
    private int numSecs = 10;


    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    // Test runs for numSecs seconds, continuously publishing SetupConfig objects and
    // receiving them in the subscriber.
    /*
    @Test
    public void testJavaKeyValueStore() throws Exception {
        new JavaTestKit(system) {
            {
                LoggingAdapter log = Logging.getLogger(system, this);
                ActorRef subscriber = system.actorOf(JPubSubTests.TestSubscriber.props("Subscriber-1"));
                system.actorOf(JPubSubTests.TestPublisher.props(getRef(), numSecs));
                log.debug("Waiting for Done message...");

                new Within(Duration.create(numSecs + 10, TimeUnit.SECONDS)) {
                    protected void run() {
                        expectMsgEquals("done");
                        subscriber.tell("done", getRef());
                        int count = expectMsgClass(Integer.class);
                        double msgPerSec = count / numSecs;
                        log.debug("Recieved " + count + " configs in " + numSecs + " seconds (" + msgPerSec + " per second)");
                    }
                };
            }
        };
    }

    // A test class that publishes configs
    static class TestPublisher extends AbstractActor {
        private final double expTime = 1.0;
        private final ActorSystem system = getContext().system();
        private final KvsSettings settings = KvsSettings.getKvsSettings(system);
        private final IKeyValueStore<SetupConfig> kvs = IKeyValueStore.getSetupConfigStore(settings, system);
        private final String prefix = "tcs.mobie.red.dat.exposureInfo";
        private final LoggingAdapter log = Logging.getLogger(system, this);

        private int nextId = 0;
        private boolean done = false;

        @Override
        public void postStop() {
            log.debug("Close connection to the kvs");
            kvs.disconnect();
        }

        public static Props props(ActorRef caller, int numSecs) {
            return Props.create(new Creator<JPubSubTests.TestPublisher>() {
                private static final long serialVersionUID = 1L;

                @Override
                public JPubSubTests.TestPublisher create() throws Exception {
                    return new JPubSubTests.TestPublisher(caller, numSecs);
                }
            });
        }

        // Publisher constructor: continuously publishes configs for numSecs seconds
        public TestPublisher(final ActorRef caller, final int numSecs) {
            system.scheduler().scheduleOnce(Duration.create(numSecs, TimeUnit.SECONDS), () -> {
                caller.tell("done", self());
                done = true;
            }, system.dispatcher());

            receive(ReceiveBuilder.
                    matchAny(t -> log.warning("Unknown message received: " + t)).
                    build());

            while (!done) {
                kvs.set(prefix, nextConfig());
                Thread.yield(); // don't want to hog the cpu here
            }
        }

        // Gets the next config
        private SetupConfig nextConfig() {
            nextId = nextId + 1;
            return new SetupConfig(prefix).jset(exposureTime, expTime);
        }
    }

    // ----

    // A test class that subscribes to configs
    static class TestSubscriber extends JSubscriber.SetupConfigSubscriber {
        private int count = 0;
        private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
        private final String name;

        public static Props props(String name) {
            return Props.create(new Creator<JPubSubTests.TestSubscriber>() {
                private static final long serialVersionUID = 1L;

                @Override
                public JPubSubTests.TestSubscriber create() throws Exception {
                    return new JPubSubTests.TestSubscriber(name);
                }
            });
        }

        public TestSubscriber(String name) {
            this.name = name;
            getContext().setReceiveTimeout(timeout);

            subscribe("tcs.mobie.red.dat.*");

            receive(ReceiveBuilder
                    .match(SetupConfig.class, this::handleSetupConfig)
                    .matchEquals("done", m -> sender().tell(count, self()))
                    .match(ReceiveTimeout.class, t -> receiveTimedOut())
                    .matchAny(t -> log.warning("Unexpected message: " + t)
                    ).build());
        }

        private void handleSetupConfig(SetupConfig config) {
            if (++count % 10000 == 0)
                log.debug("Received " + count + " configs so far: " + config);
        }

        private void receiveTimedOut() {
            log.error("Publisher seems to be blocked!");
            getContext().system().terminate();
        }
    }
    */
}

