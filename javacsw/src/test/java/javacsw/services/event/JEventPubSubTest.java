package javacsw.services.event;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.JavaTestKit;
import csw.services.event.EventService;
import csw.services.event.EventServiceSettings;
import csw.util.config.*;
import csw.util.config.Events.ObserveEvent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.PartialFunction;
import scala.concurrent.duration.FiniteDuration;
import scala.runtime.BoxedUnit;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static javacsw.services.event.JEventPubSubTest.Msg.Publish;
import static javacsw.services.event.JEventPubSubTest.Msg.PublisherInfo;
import static javacsw.services.event.JEventPubSubTest.Msg.SubscriberAck;

/**
 * Java test for event service
 */
@SuppressWarnings("FieldCanBeLocal")
public class JEventPubSubTest {
    // --- configure this ---

    // delay between subscriber acknowledgement and the publishing of the next event
    // (Note that the timer resolution is not very accurate, so there is a big difference
    // between 0 and 1 nanosecond!)
    static final FiniteDuration delay = FiniteDuration.create(1000, TimeUnit.NANOSECONDS);

    // Sets the expiration time for a message (If this is too small, the subscriber can't keep up)
    static final FiniteDuration expire = FiniteDuration.create(1, TimeUnit.SECONDS);

    // Timeout waiting for a message
    static final FiniteDuration timeout = FiniteDuration.create(6, TimeUnit.SECONDS);

    // total number of events to publish (XXX FIXME: Seems to be some problem when using about 500 or more here...)
    static final int totalEventsToPublish = 100;

    static final DoubleKey exposureTime = new DoubleKey("exposureTime");

    // Define a key for an event id
    static final IntKey eventNum = new IntKey("eventNum");

    // Define a key for image data
    static final IntVectorKey imageData = new IntVectorKey("imageData");

    // Dummy image data
    static final JIntVector testImageData = JIntVector.fromArray(new int[10000]);

    // Prefix to use for the event
    static final String prefix = "tcs.mobie.red.dat.exposureInfo";

    // Local actor messages used
    public enum Msg {
        Publish, // Publisher should publish an event
        Done, // Test is done
        SubscriberAck, // Subscriber message to publisher
        PublisherInfo // initial publisher message to subscriber
    }

    // ---

    private static ActorSystem system;
    private static EventServiceSettings settings;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
        settings = new EventServiceSettings(system);
        if (settings.useEmbeddedHornetq()) {
            // Start an embedded HornetQ server, so no need to have it running externally!
            EventService.startEmbeddedHornetQ();
        }
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    /**
     * Starts the test by creating the subscriber and publisher actors and sending a Publish
     * message to the publisher.
     *
     * @throws Exception
     */
    @Test
    public void testEventService() throws Exception {
        new JavaTestKit(system) {
            {
                LoggingAdapter log = Logging.getLogger(system, this);
                ActorRef subscriber = system.actorOf(Subscriber.props());
                ActorRef publisher = system.actorOf(Publisher.props(subscriber));
                publisher.tell(Publish, getRef());
                log.info("Waiting for Done message...");

                new Within(JavaTestKit.duration("5 minutes")) {
                    protected void run() {
                        expectMsgEquals(Msg.Done);
                        system.terminate();
                    }
                };
            }
        };
    }


    // A test class that subscribes to events
    static class Subscriber extends JEventSubscriber {
        int count = 0;
        long startTime = 0L;
        LoggingAdapter log = Logging.getLogger(getContext().system(), this);

        public static Props props() {
            return Props.create(new Creator<Subscriber>() {
                private static final long serialVersionUID = 1L;

                @Override
                public Subscriber create() throws Exception {
                    return new Subscriber();
                }
            });
        }

        public Subscriber() {
            getContext().setReceiveTimeout(timeout);
            subscribe(prefix);
            receive(ReceiveBuilder.
                    matchEquals(PublisherInfo, m -> {
                        log.info("Subscriber starting");
                        getContext().become(working(sender()));
                    }).build());
        }

        // Actor state while working (after receiving the initial PublisherInfo message)
        PartialFunction<Object, BoxedUnit> working(ActorRef publisher) {
            return ReceiveBuilder.
                    match(ObserveEvent.class, e -> receivedObserveEvent(publisher, e)).
                    match(ReceiveTimeout.class, t -> receiveTimedOut()).
                    matchAny(t -> log.warning("Unknown message received: " + t)).
                    build();
        }

        private void receiveTimedOut() {
            log.error("Publisher seems to be blocked!");
            getContext().system().terminate();
        }

        private void receivedObserveEvent(ActorRef publisher, ObserveEvent event) {
            if (startTime == 0L) startTime = System.currentTimeMillis();
            Optional<Integer> numOpt = event.jget(eventNum, 0);
            if (!numOpt.isPresent()) {
                log.error("Missing eventNum key");
                getContext().system().terminate();
            } else {
                int num = numOpt.get();
                if (num != count) {
                    log.error("Subscriber missed event: " + num + " != " + count);
                    getContext().system().terminate();
                } else {
                    count = count + 1;
                    if (count % 100 == 0) {
                        double t = (System.currentTimeMillis() - startTime) / 1000.0;
                        log.info("Received {} events in {} seconds ({} per second)",
                                count, t, count * 1.0 / t);
                    }
                    publisher.tell(SubscriberAck, sender());
                }
            }
        }
    }


    // A test class that publishes events
    static class Publisher extends AbstractActor {
        LoggingAdapter log = Logging.getLogger(getContext().system(), this);
        EventServiceSettings settings = new EventServiceSettings(getContext().system());
        JEventService eventService = new JEventService(prefix, settings);
        int count = 0;

        @Override
        public void postStop() {
            log.info("Close connection to the event service");
            eventService.close();
        }

        // Returns the next event to publish
        private ObserveEvent nextEvent(int num) {
            return new ObserveEvent(prefix)
                    .jset(eventNum, num)
                    .jset(exposureTime, 1.0)
                    .jset(imageData, testImageData);
        }

        private void publish() {
            eventService.publish(nextEvent(count), expire);
            count += 1;
        }

        public static Props props(ActorRef subscriber) {
            return Props.create(new Creator<Publisher>() {
                private static final long serialVersionUID = 1L;

                @Override
                public Publisher create() throws Exception {
                    return new Publisher(subscriber);
                }
            });
        }

        public Publisher(ActorRef subscriber) {
            getContext().setReceiveTimeout(timeout);
            subscriber.tell(PublisherInfo, self());

            receive(ReceiveBuilder.
                    matchEquals(Publish, m -> {
                        publish();
                        getContext().become(publishing(sender()));
                    }).
                    matchAny(t -> log.warning("Unknown message received: " + t)).
                    build());
        }

        PartialFunction<Object, BoxedUnit> publishing(ActorRef testActor) {
            return ReceiveBuilder.
                    matchEquals(Publish, m -> publish()).
                    matchEquals(SubscriberAck, m -> handleSubscriberAck(testActor)).
                    match(ReceiveTimeout.class, t -> receiveTimedOut()).
                    matchAny(t -> log.warning("Unknown message received: " + t)).
                    build();
        }

        private void handleSubscriberAck(ActorRef testActor) {
//            log.info("Received subscriber ack: " + count);
            if (count < totalEventsToPublish) {
                try {
                    Thread.sleep(0L, (int) delay.toNanos());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                self().tell(Publish, self());
            } else {
                testActor.tell(Msg.Done, self());
            }
        }

        private void receiveTimedOut() {
            log.error("Subscriber did not reply!");
            getContext().system().terminate();
        }
    }

}
