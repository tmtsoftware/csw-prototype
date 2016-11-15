package csw.examples.vsliceJava.assembly;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import akka.util.Timeout;
import csw.services.loc.LocationService;
import csw.util.config.DoubleItem;
import csw.util.config.Events;
import javacsw.services.events.IEventService;
import javacsw.services.events.ITelemetryService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.List;
import java.util.Optional;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static csw.examples.vsliceJava.assembly.AssemblyContext.*;
import static csw.examples.vsliceJava.assembly.AssemblyTestData.newRangeAndElData;
import static csw.examples.vsliceJava.assembly.AssemblyTestData.testZenithAngles;
import static csw.examples.vsliceJava.assembly.FollowActor.UpdatedEventData;
import static csw.util.config.Events.EventServiceEvent;
import static csw.util.config.Events.SystemEvent;
import static javacsw.util.config.JItems.jadd;
import static javacsw.util.config.JItems.jset;
import static junit.framework.TestCase.assertEquals;

/**
 * Diag Pubisher Tests
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused", "FieldCanBeLocal", "WeakerAccess"})
public class EventPublishTests extends JavaTestKit {

  @SuppressWarnings("WeakerAccess")
 /*
  * Test event service client, subscribes to some event
  */
  private static class TestSubscriber extends AbstractActor {
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    public static Props props() {
      return Props.create(new Creator<TestSubscriber>() {
        private static final long serialVersionUID = 1L;

        @Override
        public TestSubscriber create() throws Exception {
          return new TestSubscriber();
        }
      });
    }

    // --- Actor message classes ---
    static class GetResults {
    }

    static class Results {
      public final Vector<EventServiceEvent> msgs;

      public Results(Vector<EventServiceEvent> msgs) {
        this.msgs = msgs;
      }
    }

    Vector<EventServiceEvent> msgs = new Vector<>();

    public TestSubscriber() {
      receive(ReceiveBuilder.
        match(SystemEvent.class, event -> {
          msgs.add(event);
          log.info("-------->RECEIVED System " + event.info().source() + "  event: " + event);
        }).
        match(Events.StatusEvent.class, event -> {
          msgs.add(event);
          log.info("-------->RECEIVED Status " + event.info().source() + " event: " + event);
        }).
        match(GetResults.class, t -> sender().tell(new Results(msgs), self())).
        matchAny(t -> log.warning("Unknown message received: " + t)).
        build());
    }
  }

  private static ActorSystem system;
  private static LoggingAdapter logger;

//  private static double initialElevation = 90.0;

  private static Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(10, TimeUnit.SECONDS));

  private static AssemblyContext assemblyContext = AssemblyTestData.TestAssemblyContext;

  private static ITelemetryService telemetryService;

  private static IEventService eventService;

  // This def helps to make the test code look more like normal production code, where self() is defined in an actor class
  ActorRef self() {
    return getTestActor();
  }

  public EventPublishTests() {
    super(system);
  }

  @BeforeClass
  public static void setup() throws Exception {
    LocationService.initInterface();
    system = ActorSystem.create();
    logger = Logging.getLogger(system, system);

    telemetryService = ITelemetryService.getTelemetryService(ITelemetryService.defaultName, system, timeout)
      .get(5, TimeUnit.SECONDS);

    eventService = IEventService.getEventService(IEventService.defaultName, system, timeout)
      .get(5, TimeUnit.SECONDS);
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  DoubleItem initialElevation = naElevation(assemblyContext.calculationConfig.defaultInitialElevation);

  // Publisher behaves the same whether nss is in use or not so always nssNotInUse
  ActorRef newTestFollower(Optional<ActorRef> tromboneControl, Optional<ActorRef> publisher) {
    Props props = FollowActor.props(assemblyContext, initialElevation, assemblyContext.setNssInUse(false),
      tromboneControl, publisher, Optional.empty());
    return system.actorOf(props);
  }

  ActorRef newTestPublisher(Optional<IEventService> eventService, Optional<ITelemetryService> telemetryService) {
    Props testEventPublisherProps = TrombonePublisher.props(assemblyContext, eventService, telemetryService);
    return system.actorOf(testEventPublisherProps);
  }

  // --- Create follow actor with publisher and subscriber ---


    /**
     * Test Description: This test uses a "fakeSubscriber" which is simulating the subscription to TCS and RTC
     * events and ships UpdatedEventData messages to the FollowActor which calculates trombone positions and
     * other things and publishes events. This one checks for the events for AOESW in the form of
     * the System event for AOESW. One event for zenith angle 0 and focus error 0 is used for testing.
     * In this case range distance and elevation are the same, which is initial elevation in this case.
     */
    @Test
    public void test1() {
      // should allow publishing one event simulating event from fake TromboneEventSubscriber
      // Create a new publisher with no trombone position actor
      ActorRef pub = newTestPublisher(Optional.of(eventService), Optional.empty());
      ActorRef fol = newTestFollower(Optional.empty(), Optional.of(pub));

      ActorRef resultSubscriber = system.actorOf(TestSubscriber.props());
      eventService.subscribe(resultSubscriber, false, assemblyContext.aoSystemEventPrefix);
      expectNoMsg(duration("1 second")); // Wait for the connection

      TestProbe fakeTromboneEventSubscriber = new TestProbe(system);

      // This should result in two messages being sent, one to each actor in the given order
      fakeTromboneEventSubscriber.send(fol, new FollowActor.UpdatedEventData(
        za(0), fe(0),
        Events.getEventTime()));

      // This is to give actors time to run
      expectNoMsg(duration("100 milli"));

      // Ask our test subscriber for the published events
      resultSubscriber.tell(new TestSubscriber.GetResults(), self());

      TestSubscriber.Results result = expectMsgClass(TestSubscriber.Results.class);
      assertEquals(result.msgs.size(), 1);
      SystemEvent se = jadd(new SystemEvent(assemblyContext.aoSystemEventPrefix),
        naElevation(assemblyContext.calculationConfig.defaultInitialElevation),
        rd(assemblyContext.calculationConfig.defaultInitialElevation));
      Vector<SystemEvent> v = new Vector<>();
      v.add(se);
      assertEquals(result.msgs, v);
    }

    /**
     * Test Description: This test is similar to the last but a set of events are used that vary the zenith angle while holding
     * the focus error constant to see that multiple events are generated. The computed, expected values are computed with
     * AlgorithmData. If you change the algorithm you need to update the test helpers.
     */
    @Test
    public void test2() {
      // should allow publishing several events with fake tromboneEventSubscriber

      // Ignoring the messages for TrombonePosition (set to None)
      ActorRef pub = newTestPublisher(Optional.of(eventService), Optional.empty());
      ActorRef fol = newTestFollower(Optional.empty(), Optional.of(pub));

      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
      ActorRef resultSubscriber = system.actorOf(TestSubscriber.props());
      eventService.subscribe(resultSubscriber, false, assemblyContext.aoSystemEventPrefix);
      expectNoMsg(duration("1 second")); // Wait for the connection

      double testFE = 10.0;

      // These are fake messages for the CalculationActor from the EventSubscriber
      List<UpdatedEventData> events = testZenithAngles.stream().map(td ->
        new UpdatedEventData(za(td), fe(testFE), Events.getEventTime())).collect(Collectors.toList());

      // This should result in two messages being sent, one to each actor in the given order
      TestProbe fakeTromboneSubscriber = new TestProbe(system);
      events.forEach(ev -> fakeTromboneSubscriber.send(fol, ev));

      // This is to give actors time to run
      expectNoMsg(duration("100 milli"));

      resultSubscriber.tell(new TestSubscriber.GetResults(), self());
      // Check the events received through the Event Service
      TestSubscriber.Results result = expectMsgClass(TestSubscriber.Results.class);

      // Calculate expected events
      List<Pair<Double, Double>> testResult = newRangeAndElData(testFE);

      List<SystemEvent> aoeswExpected = testResult.stream().map(f ->
        jadd(new SystemEvent(assemblyContext.aoSystemEventPrefix),
          jset(naElevationKey, f.second()).withUnits(naElevationUnits),
          jset(naRangeDistanceKey, f.first()).withUnits(naRangeDistanceUnits)))
        .collect(Collectors.toList());

      //info("aowes: " + aoeswExpected)

      assertEquals(aoeswExpected, result.msgs);
    }

//    /**
//     * Test Description: This takes it one step further and replaced the fakeTromboneSubscriber with the actual TromboneEventSubscriber
//     * and uses the event service to publish events. The focus error of 10 is published then the set of data varying the zenith angle.
//     * The TromboneEventSubscriber receives the events forwards them to the follow actor which then sends out updates.
//     * Note that the EventSubscriber and FollowActor are separate so that the FollowActor can be tested as a standalone actor without the
//     * event service as is done in this and the previous tests.
//     */
//    it("should allow publishing several events through the event service") {
//      import AssemblyTestData._
//      // Ignoring the messages for TrombonePosition
//      // Create the trombone publisher for publishing SystemEvents to AOESW
//      val publisherActorRef = system.actorOf(TrombonePublisher.props(assemblyContext, Some(eventService), Some(telemetryService)))
//      // Create the calculator actor and give it the actor ref of the publisher for sending calculated events
//      val followActorRef = system.actorOf(FollowActor.props(assemblyContext, initialElevation, setNssInUse(false), None, Some(publisherActorRef)))
//      // create the subscriber that listens for events from TCS for zenith angle and focus error from RTC
//      val es = system.actorOf(TromboneEventSubscriber.props(assemblyContext, setNssInUse(false), Some(followActorRef), eventService))
//      // This injects the event service location
//      val evLocation = ResolvedTcpLocation(EventService.eventServiceConnection(), "localhost", 7777)
//      es ! evLocation
//
//      // This creates a local subscriber to get all aoSystemEventPrefix SystemEvents published for testing
//      val resultSubscriber = system.actorOf(TestSubscriber.props())
//      eventService.subscribe(resultSubscriber, postLastEvents = false, aoSystemEventPrefix)
//      expectNoMsg(1.second) // Wait for the connection
//
//      // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
//      val tcsRtc = eventService
//
//      val testFE = 10.0
//      // Publish a single focus error. This will generate a published event
//      tcsRtc.publish(SystemEvent(focusErrorPrefix).add(fe(testFE)))
//
//      // These are fake messages for the FollowActor that will be sent to simulate the TCS
//      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))
//
//      // This should result in the length of tcsEvents being published
//      tcsEvents.map { f =>
//        logger.info(s"Publish: $f")
//        tcsRtc.publish(f)
//      }
//
//      // This is to give actors time to run and subscriptions to register
//      expectNoMsg(500.milli)
//
//      // Ask the local subscriber for all the ao events published for testing
//      resultSubscriber ! GetResults
//      // Check the events received through the Event Service
//      val result = expectMsgClass(classOf[Results])
//      info("result: " + result)
//      info("resultsize: " + result.msgs.size)
//
//      // Calculate expected events
//      val testResult = newRangeAndElData(testFE)
//
//      val firstOne = SystemEvent(aoSystemEventPrefix).madd(naElevationKey -> testResult.head._2 withUnits naElevationUnits, naRangeDistanceKey -> testResult.head._1 withUnits naRangeDistanceUnits)
//      info("First: " + firstOne)
//
//      val zaExpected = testResult.map(f => SystemEvent(aoSystemEventPrefix).madd(naElevationKey -> f._2 withUnits naElevationUnits, naRangeDistanceKey -> f._1 withUnits naRangeDistanceUnits))
//      val aoeswExpected = firstOne +: zaExpected
//      info("aowes: " + aoeswExpected)
//      info("aoesw size: " + aoeswExpected.size)
//
//      // Here is the test for equality - total 16 messages
//      aoeswExpected should equal(result.msgs)
//    }
//
//    /**
//     * Test Description: This test simulates some status data for the publisher.
//     */
//    it("should allow publishing TromboneState to publisher") {
//      import TromboneStateActor._
//
//      val s1 = TromboneState(cmdItem(cmdUninitialized), moveItem(moveUnindexed), sodiumItem(false), nssItem(false))
//      val s2 = TromboneState(cmdItem(cmdReady), moveItem(moveUnindexed), sodiumItem(false), nssItem(false))
//      val s3 = TromboneState(cmdItem(cmdReady), moveItem(moveIndexing), sodiumItem(false), nssItem(false))
//      val s4 = TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false))
//
//      // Create a new publisher with no trombone position actor
//      val tp = newTestPublisher(None, Some(telemetryService))
//
//      val resultSubscriber = system.actorOf(TestSubscriber.props())
//      telemetryService.subscribe(resultSubscriber, postLastEvents = false, tromboneStateStatusEventPrefix)
//      expectNoMsg(1.second) // Wait for the connection
//
//      val fakeStateProducer = TestProbe()
//
//      def makeStatusEvent(ts: TromboneState): StatusEvent = StatusEvent(tromboneStateStatusEventPrefix).madd(ts.cmd, ts.move, ts.sodiumLayer, ts.nss)
//
//      // This should result in two messages being sent, one to each actor in the given order
//      fakeStateProducer.send(tp, s1)
//      fakeStateProducer.send(tp, s2)
//      fakeStateProducer.send(tp, s3)
//      fakeStateProducer.send(tp, s4)
//
//      // This is to give actors time to run
//      expectNoMsg(1.seconds)
//
//      // Ask our test subscriber for the published events
//      resultSubscriber ! GetResults
//
//      val result = expectMsgClass(classOf[Results])
//      result.msgs.size should be(4)
//      result.msgs should equal(Seq(s1, s2, s3, s4).map(makeStatusEvent(_)))
//    }
//  }
//
}