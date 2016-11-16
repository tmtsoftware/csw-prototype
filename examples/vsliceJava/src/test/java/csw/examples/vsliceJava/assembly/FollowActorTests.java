package csw.examples.vsliceJava.assembly;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import akka.util.Timeout;
import csw.examples.vsliceJava.hcd.TromboneHCD;
import csw.services.loc.LocationService;
import csw.services.pkg.Component;
import csw.services.pkg.Supervisor3;
import csw.util.config.BooleanItem;
import csw.util.config.DoubleItem;
import csw.util.config.Events;
import csw.util.config.JavaHelpers;
import javacsw.services.events.IEventService;
import javacsw.services.events.ITelemetryService;
import javacsw.services.pkg.JComponent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static csw.examples.vsliceJava.assembly.Algorithms.rangeDistanceToStagePosition;
import static csw.examples.vsliceJava.assembly.AssemblyContext.*;
import static csw.examples.vsliceJava.assembly.AssemblyTestData.*;
import static csw.examples.vsliceJava.assembly.FollowActor.UpdatedEventData;
import static csw.examples.vsliceJava.assembly.TromboneAssembly.UpdateTromboneHCD;
import static csw.examples.vsliceJava.assembly.TromboneControl.GoToStagePosition;
import static csw.examples.vsliceJava.assembly.TrombonePublisher.AOESWUpdate;
import static csw.examples.vsliceJava.assembly.TrombonePublisher.EngrUpdate;
import static csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisState.AXIS_IDLE;
import static csw.examples.vsliceJava.hcd.TromboneHCD.*;
import static csw.services.pkg.SupervisorExternal.LifecycleStateChanged;
import static csw.services.pkg.SupervisorExternal.SubscribeLifecycleCallback;
import static csw.util.config.Events.*;
import static csw.util.config.StateVariable.CurrentState;
import static javacsw.services.loc.JConnectionType.AkkaType;
import static javacsw.services.pkg.JComponent.DoNotRegister;
import static javacsw.services.pkg.JSupervisor3.LifecycleInitialized;
import static javacsw.services.pkg.JSupervisor3.LifecycleRunning;
import static javacsw.util.config.JItems.jadd;
import static javacsw.util.config.JItems.jset;
import static javacsw.util.config.JPublisherActor.Subscribe;
import static javacsw.util.config.JUnitsOfMeasure.*;
import static junit.framework.TestCase.assertEquals;

/**
 * Diag Pubisher Tests
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused", "FieldCanBeLocal", "WeakerAccess"})
public class FollowActorTests extends JavaTestKit {

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
          log.info("RECEIVED System " + event.info().source() + "  event: " + event);
        }).
        match(Events.StatusEvent.class, event -> {
          msgs.add(event);
          log.info("RECEIVED Status " + event.info().source() + " event: " + event);
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

  private static ITelemetryService telemetryService;

  private static IEventService eventService;

  private static AssemblyContext assemblyContext = AssemblyTestData.TestAssemblyContext;
  TromboneCalculationConfig calculationConfig = assemblyContext.calculationConfig;
  TromboneControlConfig controlConfig = assemblyContext.controlConfig;

  // This def helps to make the test code look more like normal production code, where self() is defined in an actor class
  ActorRef self() {
    return getTestActor();
  }

  public FollowActorTests() {
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

  TestActorRef<FollowActor> newFollower(BooleanItem usingNSS, ActorRef tromboneControl, ActorRef aoPublisher, ActorRef engPublisher) {
    // Used for creating followers
    DoubleItem initialElevation = iElevation(assemblyContext.calculationConfig.defaultInitialElevation);
    Props props = FollowActor.props(assemblyContext, initialElevation, usingNSS, Optional.of(tromboneControl),
      Optional.of(aoPublisher), Optional.of(engPublisher));
    return TestActorRef.create(system, props);
  }

  // --- Basic tests for connectivity ----

  TestProbe fakeTC = new TestProbe(system);
  TestProbe fakePub = new TestProbe(system);
  TestProbe fakeEng = new TestProbe(system);

  @Test
    public void test1() {
      // should allow creation with defaults
    TestActorRef<FollowActor> cal = newFollower(setNssInUse(false), fakeTC.ref(), fakePub.ref(), fakeEng.ref());

      assertEquals(cal.underlyingActor().initialElevation, iElevation(calculationConfig.defaultInitialElevation));

      fakeTC.expectNoMsg(duration("1 seconds"));
      system.stop(cal);
    }

    // --- Test set initial elevation ---

  @Test
  public void test2() {
    // should be default before
    TestActorRef<FollowActor> cal = newFollower(setNssInUse(false), fakeTC.ref(), fakePub.ref(), fakeEng.ref());

    assertEquals(cal.underlyingActor().initialElevation, iElevation(calculationConfig.defaultInitialElevation));

    system.stop(cal);
  }


  // -------------- The following set of tests use an actual tromboneHCD for testing  --------------------
  // The following are used to start a tromboneHCD for testing purposes
  ActorRef startHCD() {
    Component.HcdInfo testInfo = JComponent.hcdInfo(
      TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Collections.singleton(AkkaType), FiniteDuration.create(1, TimeUnit.SECONDS)
    );

    return Supervisor3.apply(testInfo);
  }

  // --- Test for handling of Update events ---

  @Test
  public void test3() {
  // should at least handle and send messages
    TestActorRef<FollowActor> cal = newFollower(setNssInUse(false), fakeTC.ref(), fakePub.ref(), fakeEng.ref());

      // This should result in two messages being sent, one to each actor in the given order
      cal.tell(new UpdatedEventData(za(0), fe(0), Events.getEventTime()), self());

      fakeTC.expectMsgClass(GoToStagePosition.class);
      fakePub.expectMsgClass(AOESWUpdate.class);
      system.stop(cal);
    }

    @Test
      public void test4(){
      // should ignore if units wrong
      TestActorRef<FollowActor> cal = newFollower(setNssInUse(false), fakeTC.ref(), fakePub.ref(), fakeEng.ref());

      // This should result in two messages being sent, one to each actor in the given order
      logger.info("Note: This produced an error message for improper units, which is okay!");
      cal.tell(new UpdatedEventData(jset(zenithAngleKey, 0.0), jset(focusErrorKey, 0.0),
        Events.getEventTime()), self());

      fakeTC.expectNoMsg(duration("100 milli"));
      system.stop(cal);
    }

    @Test
    public void test5() {
       // should ignore if inputs out of range
      TestActorRef<FollowActor> cal = newFollower(setNssInUse(false), fakeTC.ref(), fakePub.ref(), fakeEng.ref());

      // This should result in two messages being sent, one to each actor in the given order
      logger.info("Note: This produced two error messages for out of range data, which are okay!");
      cal.tell(new UpdatedEventData(za(-10), fe(0), Events.getEventTime()), self());
      fakeTC.expectNoMsg(duration("100 milli"));

      cal.tell(new UpdatedEventData(za(0.0), fe(42.0), Events.getEventTime()), self());
      fakeTC.expectNoMsg(duration("100 milli"));
      system.stop(cal);
    }

  /*
   * Test Description: This test provides simulated UpdatedEventData events to the FollowActor and then tests that the
   * FollowActor sends the expected messages out including:
   * Events for AOESW
   * Positions for Trombone Stage
   * Engineering Status event
   * The events are received by "fake" actors played by TestProbes
   */

  // --- Test for reasonable results when setNssInUse(false) ---

  @Test
  public void test6() {
    // should work when only changing zenith angle
    TestActorRef<FollowActor> follower = newFollower(setNssInUse(false), fakeTC.ref(), fakePub.ref(), fakeEng.ref());

    // Generate a list of fake event updates for a range of zenith angles and focus error 10mm
    double testFocusError = 10.0;
    // testZenithAngles is in AlgorithmData
    List<UpdatedEventData> events = testZenithAngles.stream().map(f -> new UpdatedEventData(za(f), fe(testFocusError), Events.getEventTime()))
      .collect(Collectors.toList());

    // Send the events to the follow actor
    events.forEach(f -> follower.tell(f, self()));

    // XXX Note: The TestKit.receiveN calls below get a bit verbose due to the conversion from Scala to Java collections

    // Expect a set of AOESWUpdate messages to the fake publisher
    List<?> aoEvts = scala.collection.JavaConversions.asJavaCollection(
      fakePub.receiveN(testZenithAngles.size())).stream().collect(Collectors.toList());

    // Expect a set of HCDTrombonePosition messages to the fake trombone sender
    List<?> trPos = scala.collection.JavaConversions.asJavaCollection(
      fakeTC.receiveN(testZenithAngles.size())).stream().collect(Collectors.toList());

    // sender of eng msgs
    List<?> engMsgs = scala.collection.JavaConversions.asJavaCollection(
      fakeEng.receiveN(testZenithAngles.size())).stream().collect(Collectors.toList());

    // The following assumes we have models for what is to come out of the assembly.  Here we are just
    // reusing the actual equations to test that the events are proper
    List<Pair<Double, Double>> testdata = newRangeAndElData(testFocusError);

    // This checks the events for AO ESW event
    List<AOESWUpdate> aoeswExpected = testdata.stream().map(f -> new AOESWUpdate(
      jset(naElevationKey, f.second()).withUnits(kilometers),
      jset(naRangeDistanceKey, f.first()).withUnits(kilometers)))
      .collect(Collectors.toList());

    assertEquals(aoeswExpected, aoEvts);

    // state position is total elevation in mm
    List<GoToStagePosition> stageExpected = testdata.stream().map(f ->
      new GoToStagePosition(
        jset(stagePositionKey, f.first()).withUnits(stagePositionUnits)))
      .collect(Collectors.toList());

    assertEquals(stageExpected, trPos);

    List<AssemblyTestData.TestValue> calcTestData = AssemblyTestData.calculatedTestData(calculationConfig, controlConfig, testFocusError);

    List<EngrUpdate> engExpected = calcTestData.stream().map(f ->
      new EngrUpdate(
        jset(focusErrorKey, testFocusError).withUnits(micrometers),
        jset(stagePositionKey, rangeDistanceToStagePosition(gettrd(f))).withUnits(millimeters),
        jset(zenithAngleKey, getza(f)).withUnits(degrees)))
      .collect(Collectors.toList());

    assertEquals(engExpected, engMsgs);
    system.stop(follower);
  }

  @Test
  public void test7() {
    // should get other events when nssInUse but not aoesw events

    TestActorRef<FollowActor> follower = newFollower(setNssInUse(true), fakeTC.ref(), fakePub.ref(), fakeEng.ref());

    // Generate a list of fake event updates for a range of zenith angles and focus error 10mm
    double testFocusError = 10.0;
    // testZenithAngles is in AlgorithmData
    List<UpdatedEventData> events = testZenithAngles.stream().map(f ->
      new UpdatedEventData(za(f), fe(testFocusError), Events.getEventTime()))
      .collect(Collectors.toList());

    // Send the events to the follow actor
    events.forEach(f -> follower.tell(f, self()));

    // Expect a set of HCDTrombonePosition messages to the fake trombone sender
    List<?> trPos = scala.collection.JavaConversions.asJavaCollection(
      fakeTC.receiveN(testZenithAngles.size()))
      .stream().collect(Collectors.toList());

    // sender of eng msgs
    List<?> engMsgs = scala.collection.JavaConversions.asJavaCollection(
      fakeEng.receiveN(testZenithAngles.size()))
      .stream().collect(Collectors.toList());

    // The following assumes we have models for what is to come out of the assembly.  Here we are just
    // reusing the actual equations to test that the events are proper
    List<Pair<Double, Double>> testdata = newRangeAndElData(testFocusError);

    // Expect no AOESWUpdate messages to the fake publisher when nssInuse, wait a bit to check that no messages arrive
    fakePub.expectNoMsg(duration("200 milli"));

    // state position is total elevation in mm
    List<GoToStagePosition> stageExpected = testdata.stream().map(f -> new GoToStagePosition(
      jset(stagePositionKey, f.first()).withUnits(stagePositionUnits))).collect(Collectors.toList());

    assertEquals(stageExpected, trPos);

    List<TestValue> calcTestData = calculatedTestData(calculationConfig, controlConfig, testFocusError);

    List<EngrUpdate> engExpected = calcTestData.stream().map(f ->
      new EngrUpdate(
        jset(focusErrorKey, testFocusError).withUnits(micrometers),
        jset(stagePositionKey, rangeDistanceToStagePosition(gettrd(f))).withUnits(millimeters),
        jset(zenithAngleKey, getza(f)).withUnits(degrees)))
      .collect(Collectors.toList());

    assertEquals(engExpected, engMsgs);
    system.stop(follower);
  }

    /**
     * This expect message will absorb CurrentState messages as long as the current is not equal the desired destination
     * Then it collects the one where it is the destination and the end message
     *
     * @param tp   TestProbe that is receiving the CurrentState messages
     * @param dest a TestProbe acting as the assembly
     *
     * @return A sequence of CurrentState messages
     */
    List<CurrentState> expectMoveMsgsWithDest(TestProbe tp, int dest) {
      final CurrentState[] msgs =
        new ReceiveWhile<CurrentState>(CurrentState.class, duration("5 seconds")) {
          protected CurrentState match(Object in) {
            if (in instanceof CurrentState) {
              CurrentState cs = (CurrentState) in;
              if ((cs.prefix().contains(TromboneHCD.axisStatePrefix) && !JavaHelpers.jvalue(cs, positionKey).equals(dest))
                || cs.prefix().equals(TromboneHCD.axisStatsPrefix))
                return cs;
            }
            throw noMatch();
          }
        }.get(); // this extracts the received messages

      CurrentState fmsg1 = tp.expectMsgClass(CurrentState.class); // last one with current == target
      CurrentState fmsg2 = tp.expectMsgClass(CurrentState.class); // the the end event with IDLE
      List<CurrentState> allmsgs = Arrays.asList(msgs);
      allmsgs.add(fmsg1);
      allmsgs.add(fmsg2);
      return allmsgs;
    }

    /**
     * Test Description: This test creates a trombone HCD to receive events from the FollowActor when nssNotInUse.
     * This tests the entire path with fake TCS sending events through Event Service, which are received by
     * TromboneSubscriber and then processed by FollowActor, which sends them to TromboneControl
     * which sends them to the TromboneHCD, which replies with StateUpdates.
     * The FollowActor is also publishing eng and sodiumLayer StatusEvents, which are published to the event service
     * and subscribed to by test clients, that collect their events for checking at the end
     * The first part is about starting the HCD and waiting for it to reach the runing lifecycle state where it can receive events
     */
    @Test
    public void test8() {
      // creates fake TCS/RTC events with Event Service through FollowActor and back to HCD instance
      ActorRef tromboneHCD = startHCD();

      TestProbe fakeAssembly = new TestProbe(system);

      tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));

      // This has HCD sending updates back to this Assembly
      fakeAssembly.send(tromboneHCD, Subscribe);

      // Ignoring the messages for TrombonePosition
      // Create the trombone publisher for publishing SystemEvents to AOESW
      ActorRef publisherActorRef = system.actorOf(TrombonePublisher.props(assemblyContext, Optional.of(eventService), Optional.of(telemetryService)));

      // Ignoring the messages for AO for the moment
      // Create the trombone publisher for publishing SystemEvents to AOESW
      ActorRef tromboneControl = system.actorOf(TromboneControl.props(assemblyContext, Optional.empty()));
      tromboneControl.tell(new UpdateTromboneHCD(Optional.of(tromboneHCD)), self());

      BooleanItem nssUsage = setNssInUse(false);
      // Create the follow actor and give it the actor ref of the publisher for sending calculated events
      // The following uses the same publisher actor for both AOESW and Eng events
      TestActorRef<FollowActor> followActor = newFollower(nssUsage, tromboneControl, publisherActorRef, publisherActorRef);

      // create the subscriber that receives events from TCS for zenith angle and focus error from RTC
      ActorRef tromboneEventSubscriber = system.actorOf(TromboneEventSubscriber.props(assemblyContext, nssUsage, Optional.of(followActor), eventService));

      // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
      Optional<IEventService> tcsRtc = Optional.of(eventService);

      double testFE = 20.0;
      // Publish a single focus error. This will generate a published event
      tcsRtc.ifPresent(f -> f.publish(new SystemEvent(focusErrorPrefix).add(fe(testFE))));

      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
      ActorRef resultSubscriber1 = system.actorOf(TestSubscriber.props());
      eventService.subscribe(resultSubscriber1, false, assemblyContext.aoSystemEventPrefix);

      ActorRef resultSubscriber2 = system.actorOf(TestSubscriber.props());
      eventService.subscribe(resultSubscriber2, false, assemblyContext.engStatusEventPrefix);

      // These are fake messages for the FollowActor that will be sent to simulate the TCS updating ZA
      List<SystemEvent> tcsEvents = testZenithAngles.stream().map(f ->
        new SystemEvent(zaConfigKey.prefix()).add(za(f))).collect(Collectors.toList());

      // This should result in the length of tcsEvents being published, which is 15
      tcsEvents.forEach(f -> {
        logger.info("Publish: " + f);
        tcsRtc.ifPresent(t -> t.publish(f));
        // The following is not required, but is added to make the event timing more interesting
        // Varying this delay from 50 to 10 shows completion of moves and at 10 update of move positions before finishing
        try {
          Thread.sleep(500); // 500 makes it seem more interesting to watch, but is not needed for proper operation
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      });

      // ---- Everything from here on is about gathering the data and checking

      // The following constructs the expected messages that contain the encoder positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are working properly.
      // First keep focus error fixed at 10 um
      List<TestValue> testdata = calculatedTestData(calculationConfig, controlConfig, testFE);

      // This uses the total elevation to get expected values for encoder position
      int encExpected = getenc(testdata.get(testdata.size()-1));
      logger.info("encExpected1: " + encExpected);

      // This gets the first set of CurrentState messages for moving to the FE 10 mm position
      List<CurrentState> msgs = expectMoveMsgsWithDest(fakeAssembly, encExpected);
      CurrentState last = msgs.get(msgs.size()-1);
      assertEquals(JavaHelpers.jvalue(last, positionKey), Integer.valueOf(encExpected));
      assertEquals(JavaHelpers.jvalue(last, stateKey), AXIS_IDLE);
      assertEquals(JavaHelpers.jvalue(last, inLowLimitKey), Boolean.valueOf(false));
      assertEquals(JavaHelpers.jvalue(last, inHighLimitKey), Boolean.valueOf(false));

      // Check that nothing is happening - not needed
      fakeAssembly.expectNoMsg(duration("200 milli"));

      resultSubscriber1.tell(new TestSubscriber.GetResults(), self());
      // Check the events received through the Event Service
      TestSubscriber.Results result1 = expectMsgClass(TestSubscriber.Results.class);
      //logger.info("Result 1: " + result1)

      // Calculate expected events
      List<Pair<Double, Double>> testResult = newRangeAndElData(testFE);

      SystemEvent firstOne = jadd(new SystemEvent(assemblyContext.aoSystemEventPrefix),
        jset(naElevationKey, testResult.get(0).second()).withUnits(naElevationUnits),
        jset(naRangeDistanceKey, testResult.get(0).first()).withUnits(naRangeDistanceUnits));

      List<SystemEvent> zaExpected = testResult.stream().map(f ->
        jadd(new SystemEvent(assemblyContext.aoSystemEventPrefix),
          jset(naElevationKey, f.second()).withUnits(naElevationUnits),
          jset(naRangeDistanceKey, f.first()).withUnits(naRangeDistanceUnits)))
        .collect(Collectors.toList());

      List<SystemEvent> aoeswExpected = new ArrayList<>();
      aoeswExpected.add(firstOne);
      aoeswExpected.addAll(zaExpected);
      assertEquals(result1.msgs, aoeswExpected);

      resultSubscriber2.tell(new TestSubscriber.GetResults(), self());
      // Check the events received through the Event Service
      TestSubscriber.Results result2 = expectMsgClass(TestSubscriber.Results.class);

      List<TestValue> calcTestData = calculatedTestData(calculationConfig, controlConfig, testFE);

      double firstStage = rangeDistanceToStagePosition(gettrd(calcTestData.get(0)));
      double firstZA = getza(calcTestData.get(0));

      StatusEvent firstEng = jadd(new StatusEvent(assemblyContext.engStatusEventPrefix),
        jset(focusErrorKey, testFE).withUnits(focusErrorUnits),
        jset(stagePositionKey, firstStage).withUnits(stagePositionUnits),
        jset(zenithAngleKey, firstZA).withUnits(zenithAngleUnits));

      List<StatusEvent> zaEngExpected = calcTestData.stream().map(f ->
        jadd(new StatusEvent(assemblyContext.engStatusEventPrefix),
          jset(focusErrorKey, testFE).withUnits(focusErrorUnits),
          jset(stagePositionKey, rangeDistanceToStagePosition(gettrd(f))).withUnits(stagePositionUnits),
          jset(zenithAngleKey, getza(f)).withUnits(zenithAngleUnits)))
        .collect(Collectors.toList());

      List<StatusEvent> engExpected = new ArrayList<>();
      engExpected.add(firstEng);
      engExpected.addAll(zaEngExpected);
      assertEquals(result2.msgs, engExpected);

      tromboneHCD.tell(PoisonPill.getInstance(), self());
      system.stop(publisherActorRef);
      system.stop(tromboneControl);
      system.stop(followActor);
      system.stop(tromboneEventSubscriber);
      system.stop(resultSubscriber1);
      system.stop(resultSubscriber2);
    }
}
