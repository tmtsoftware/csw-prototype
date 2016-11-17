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
import csw.services.pkg.Supervisor;
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
import static csw.examples.vsliceJava.hcd.TromboneHCD.*;
import static csw.services.pkg.SupervisorExternal.LifecycleStateChanged;
import static csw.services.pkg.SupervisorExternal.SubscribeLifecycleCallback;
import static csw.util.config.Events.*;
import static csw.util.config.StateVariable.CurrentState;
import static javacsw.services.loc.JConnectionType.AkkaType;
import static javacsw.services.pkg.JComponent.DoNotRegister;
import static javacsw.services.pkg.JSupervisor.LifecycleInitialized;
import static javacsw.services.pkg.JSupervisor.LifecycleRunning;
import static javacsw.util.config.JItems.jadd;
import static javacsw.util.config.JItems.jset;
import static javacsw.util.config.JPublisherActor.Subscribe;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Diag Pubisher Tests
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused", "FieldCanBeLocal", "WeakerAccess"})
public class FollowCommandTests extends JavaTestKit {

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

  public FollowCommandTests() {
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

  // Used for creating followers
  DoubleItem initialElevation = naElevation(assemblyContext.calculationConfig.defaultInitialElevation);

  TestActorRef<FollowCommand> newTestFollowCommand(BooleanItem nssInUse, Optional<ActorRef> tromboneHCD, Optional<ActorRef> eventPublisher) {
    Props props = FollowCommand.props(assemblyContext, initialElevation, nssInUse, tromboneHCD, eventPublisher, eventService);
    return TestActorRef.create(system, props);
  }

  ActorRef newFollowCommand(BooleanItem isNssInUse, Optional<ActorRef> tromboneHCD, Optional<ActorRef> eventPublisher) {
    Props props = FollowCommand.props(assemblyContext, initialElevation, isNssInUse, tromboneHCD, eventPublisher, eventService);
    return system.actorOf(props);
  }

  // The following are used to start a tromboneHCD for testing purposes
  ActorRef startHCD() {
    Component.HcdInfo testInfo = JComponent.hcdInfo(
      TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Collections.singleton(AkkaType), FiniteDuration.create(1, TimeUnit.SECONDS)
    );

    return Supervisor.apply(testInfo);
  }


  /**
   * This expect message will absorb CurrentState messages as long as the current is not equal the desired destination
   * Then it collects the one where it is the destination and the end message
   *
   * @param dest a TestProbe acting as the assembly
   * @return A sequence of CurrentState messages
   */
  List<CurrentState> expectMoveMsgsWithDest(int dest) {
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

    CurrentState fmsg1 = expectMsgClass(CurrentState.class); // last one with current == target
    CurrentState fmsg2 = expectMsgClass(CurrentState.class); // the the end event with IDLE
    List<CurrentState> allmsgs = new ArrayList<>();
    allmsgs.addAll(Arrays.asList(msgs));
    allmsgs.add(fmsg1);
    allmsgs.add(fmsg2);
    return allmsgs;
  }

  /**
   * This will accept CurrentState messages until a state value is AXIS_IDLE
   * This is useful when you know there is one move and it will end without being updated
   *
   * @return a Sequence of CurrentState messages
   */
  List<CurrentState> waitForMoveMsgs() {
    final CurrentState[] msgs =
      new ReceiveWhile<CurrentState>(CurrentState.class, duration("5 seconds")) {
        protected CurrentState match(Object in) {
          if (in instanceof CurrentState) {
            CurrentState cs = (CurrentState) in;
            if ((cs.prefix().contains(TromboneHCD.axisStatePrefix) && JavaHelpers.jvalue(cs, stateKey).equals(TromboneHCD.AXIS_MOVING))
              || cs.prefix().equals(TromboneHCD.axisStatsPrefix))
              return cs;
          }
          throw noMatch();
        }
      }.get(); // this extracts the received messages

    CurrentState fmsg = expectMsgClass(CurrentState.class); // last one with current == target
    List<CurrentState> allmsgs = new ArrayList<>();
    allmsgs.addAll(Arrays.asList(msgs));
    allmsgs.add(fmsg);
    return allmsgs;
  }

  // --- basic event command setup ---

  @Test
  public void test0() {
    // should be created with no issues
    TestProbe fakeTromboneHCD = new TestProbe(system);

    TestActorRef<FollowCommand> fc = newTestFollowCommand(setNssInUse(false), Optional.of(fakeTromboneHCD.ref()), Optional.empty());

    assertEquals(fc.underlyingActor().nssInUseIn, setNssInUse(false));
    assertEquals(fc.underlyingActor().tromboneHCDIn, Optional.of(fakeTromboneHCD.ref()));

    fc.tell(new FollowCommand.StopFollowing(), self());
    fakeTromboneHCD.expectNoMsg(duration("250 milli"));
  }

  // --- Tests of the overall collection of actors in follow commmand ---

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
  public void test1() {
    // 1 creates fake TCS/RTC events with Event Service through FollowActor and back to HCD instance - nssNotInUse
    ActorRef tromboneHCD = startHCD();

//    TestProbe fakeAssembly = new TestProbe(system);
    // XXX Using self() here in the Java version, since you can't easily call receiveWhile on a TestProbe from Java
    ActorRef fakeAssembly = self();

    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly), self());
    expectMsgEquals(new LifecycleStateChanged(LifecycleInitialized));
    expectMsgEquals(new LifecycleStateChanged(LifecycleRunning));

    // This has HCD sending updates back to this Assembly
    tromboneHCD.tell(Subscribe, fakeAssembly);

    ActorRef eventPublisher = system.actorOf(TrombonePublisher.props(assemblyContext, Optional.of(eventService), Optional.of(telemetryService)));
    ActorRef fc = newFollowCommand(setNssInUse(false), Optional.of(tromboneHCD), Optional.of(eventPublisher));

    // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
    IEventService tcsRtc = eventService;

    double testFE = 20.0;
    // Publish a single focus error. This will generate a published event
    tcsRtc.publish(new SystemEvent(focusErrorPrefix).add(fe(testFE)));

    // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
    ActorRef resultSubscriber1 = system.actorOf(TestSubscriber.props());
    eventService.subscribe(resultSubscriber1, false, assemblyContext.aoSystemEventPrefix);

    ActorRef resultSubscriber2 = system.actorOf(TestSubscriber.props());
    eventService.subscribe(resultSubscriber2, false, assemblyContext.engStatusEventPrefix);

//    expectNoMsg(duration("1 second")); // Wait for subscriptions to happen

    // These are fake messages for the FollowActor that will be sent to simulate the TCS updating ZA
    List<SystemEvent> tcsEvents = testZenithAngles.stream().map(f -> new SystemEvent(zaConfigKey.prefix()).add(za(f)))
      .collect(Collectors.toList());

    // This should result in the length of tcsEvents being published, which is 15
    tcsEvents.forEach(f -> {
      logger.info("Publish: " + f);
      tcsRtc.publish(f);
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
    int encExpected = getenc(testdata.get(testdata.size() - 1));
    logger.info("encExpected1: " + encExpected);

    // This gets the first set of CurrentState messages for moving to the FE 10 mm position
    List<CurrentState> msgs = expectMoveMsgsWithDest(encExpected);
    CurrentState last = msgs.get(msgs.size() - 1);
    assertEquals(JavaHelpers.jvalue(last, positionKey), Integer.valueOf(encExpected));
    assertEquals(JavaHelpers.jvalue(last, stateKey), AXIS_IDLE);
    assertEquals(JavaHelpers.jvalue(last, inLowLimitKey), Boolean.valueOf(false));
    assertEquals(JavaHelpers.jvalue(last, inHighLimitKey), Boolean.valueOf(false));

    // Check that nothing is happening - not needed
    expectNoMsg(duration("200 milli"));

    // Stop this follow command
    system.stop(fc);
    system.stop(eventPublisher);

    resultSubscriber1.tell(new TestSubscriber.GetResults(), self());
    // Check the events received through the Event Service
    TestSubscriber.Results result1 = expectMsgClass(TestSubscriber.Results.class);

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
    system.stop(resultSubscriber1);
    system.stop(resultSubscriber2);
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
  public void test2() {
    // 2 creates fake TCS/RTC events with Event Service through FollowActor and back to HCD instance - nssInUse") {
    ActorRef tromboneHCD = startHCD();

//    TestProbe fakeAssembly = new TestProbe(system);
    // For Java API use self(), to make working with receiveWhile easier
    ActorRef fakeAssembly = self();

    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly), self());
    expectMsgEquals(new LifecycleStateChanged(LifecycleInitialized));
    expectMsgEquals(new LifecycleStateChanged(LifecycleRunning));

    // This has HCD sending updates back to this Assembly
    tromboneHCD.tell(Subscribe, fakeAssembly);

    // First set it up so we can ensure initial za
    ActorRef eventPublisher = system.actorOf(TrombonePublisher.props(assemblyContext, Optional.of(eventService), Optional.of(telemetryService)));
    ActorRef fc = newFollowCommand(setNssInUse(true), Optional.of(tromboneHCD), Optional.of(eventPublisher));

    // Initialize the fe and za
    double testZA = 30.0;
    double testFE = 10.0;
    fc.tell(new FollowCommand.UpdateZAandFE(za(testZA), fe(testFE)), self());
    waitForMoveMsgs();

    // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
    IEventService tcsRtc = eventService;

    // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
    ActorRef resultSubscriber1 = system.actorOf(TestSubscriber.props());
    eventService.subscribe(resultSubscriber1, false, assemblyContext.aoSystemEventPrefix);

    ActorRef resultSubscriber2 = system.actorOf(TestSubscriber.props());
    eventService.subscribe(resultSubscriber2, false, assemblyContext.engStatusEventPrefix);

    // These are fake messages for the FollowActor that will be sent to simulate the TCS updating ZA
    List<SystemEvent> tcsEvents = testZenithAngles.stream().limit(3).map(f ->
      new SystemEvent(zaConfigKey.prefix()).add(za(f))).collect(Collectors.toList());

    // This should result in the length of tcsEvents being published, which is 15
    // Since nss is in use, no events will be published because za is not subscribed to
    tcsEvents.forEach(f -> {
      logger.info("Publish: " + f);
      tcsRtc.publish(f);
      // The following is not required, but is added to make the event timing more interesting
      // Varying this delay from 50 to 10 shows completion of moves and at 10 update of move positions before finishing
      try {
        Thread.sleep(500); // 500 makes it seem more interesting to watch, but is not needed for proper operation
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    // Check that nothing is happening - i.e. no events nor is anything coming back from the follow actor or HCD
//    fakeAssembly.expectNoMsg(duration("500 milli"));
    resultSubscriber1.tell(new TestSubscriber.GetResults(), self());
    TestSubscriber.Results result1 = expectMsgClass(TestSubscriber.Results.class);
    assertEquals(result1.msgs.size(), 0);

    // Now create some fe events
    // Publish a set of focus errors. This will generate published events but ZA better be 0
    List<SystemEvent> feEvents = testFocusErrors.stream().map(f -> new SystemEvent(focusErrorPrefix).add(fe(f)))
      .collect(Collectors.toList());

    feEvents.forEach(f -> {
      logger.info("Publish FE: " + f);
      tcsRtc.publish(f);
      // The following is not required, but is added to make the event timing more interesting
      // Varying this delay from 50 to 10 shows completion of moves and at 10 update of move positions before finishing
      try {
        Thread.sleep(500); // 500 makes it seem more interesting to watch, but is not needed for proper operation
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    List<TestValue> calcData = calculatedTestData(calculationConfig, controlConfig, testFocusErrors.get(testFocusErrors.size() - 1));
    int encExpected = getenc(calcData.get(0));
    logger.info("encEx: " + encExpected);

    // This gets the first set of CurrentState messages for moving to the FE 10 mm position
    List<CurrentState> msgs = expectMoveMsgsWithDest(encExpected);
    CurrentState last = msgs.get(msgs.size() - 1);
    assertEquals(JavaHelpers.jvalue(last, positionKey), Integer.valueOf(encExpected));
    assertEquals(JavaHelpers.jvalue(last, stateKey), AXIS_IDLE);
    assertEquals(JavaHelpers.jvalue(last, inLowLimitKey), Boolean.valueOf(false));
    assertEquals(JavaHelpers.jvalue(last, inHighLimitKey), Boolean.valueOf(false));

    // Stop this follow command
    system.stop(fc);
    system.stop(eventPublisher);

    // verify that the eng messages are the right number and that za is always 0
    // Still no AOESW events
    resultSubscriber1.tell(new TestSubscriber.GetResults(), self());
    TestSubscriber.Results result1b = expectMsgClass(TestSubscriber.Results.class);
    assertTrue(result1b.msgs.isEmpty());

    // Now get the engr telemetry
    resultSubscriber2.tell(new TestSubscriber.GetResults(), self());
    TestSubscriber.Results results = expectMsgClass(TestSubscriber.Results.class);
    List<StatusEvent> engs = results.msgs.stream().map(f -> (StatusEvent) f)
      .collect(Collectors.toList());

    // Verify that the za is always 0.0 when inNssMode
    List<Double> l = engs.stream().map(f -> (double)JavaHelpers.jvalue(f, zenithAngleKey)).filter(f -> !f.equals(0.0))
      .collect(Collectors.toList());

    assertTrue(l.isEmpty());

    tromboneHCD.tell(PoisonPill.getInstance(), self());
    system.stop(resultSubscriber1);
    system.stop(resultSubscriber2);
  }

  /**
   * Test Description: This test creates a trombone HCD to receive events from the FollowActor when nssNotInUse.
   * This tests the entire path with fake TCS sending events through Event Service, which are received by
   * TromboneSubscriber and then processed by FollowActor, which sends them to TromboneControl
   * which sends them to the TromboneHCD, which replies with StateUpdates.
   * The FollowActor is also publishing eng and sodiumLayer StatusEvents, which are published to the event service
   * and subscribed to by test clients, that collect their events for checking at the end
   * The first part is about starting the HCD and waiting for it to reach the runing lifecycle state where it can receive events.
   * This test verifies that the updatehcd message works by sending None which causes the position updates to stop
   */
  @Test
  public void test3() {
    // 3 creates fake TCS/RTC events with Event Service through FollowActor and back to HCD instance - check that update HCD works") {
    ActorRef tromboneHCD = startHCD();

//    TestProbe fakeAssembly = new TestProbe(system);
    // Akka Java API makes it difficult to use a TestProbe with receiveWhile, so using self() instead
    ActorRef fakeAssembly = self();

    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly), self());
    expectMsgEquals(new LifecycleStateChanged(LifecycleInitialized));
    expectMsgEquals(new LifecycleStateChanged(LifecycleRunning));

    // This has HCD sending updates back to this Assembly
    tromboneHCD.tell(Subscribe, fakeAssembly);

    // First set it up so we can ensure initial za
    ActorRef eventPublisher = system.actorOf(TrombonePublisher.props(assemblyContext, Optional.of(eventService), Optional.of(telemetryService)));
    ActorRef fc = newFollowCommand(setNssInUse(false), Optional.of(tromboneHCD), Optional.of(eventPublisher));

    // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
    IEventService tcsRtc = eventService;

    // These are fake messages for the FollowActor that will be sent to simulate the TCS updating ZA
    List<SystemEvent> tcsEvents = testZenithAngles.stream().limit(3).map(f ->
      new SystemEvent(zaConfigKey.prefix()).add(za(f))).collect(Collectors.toList());

    // This should result in the length of tcsEvents being published, which is 15
    tcsEvents.forEach(f -> {
      logger.info("Publish: " + f);
      tcsRtc.publish(f);
      // The following is not required, but is added to make the event timing more interesting
      // Varying this delay from 50 to 10 shows completion of moves and at 10 update of move positions before finishing
      try {
        Thread.sleep(500); // 500 makes it seem more interesting to watch, but is not needed for proper operation
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    double testFE = 0.0;
    List<TestValue> testdata = calculatedTestData(calculationConfig, controlConfig, testFE);

    // This uses the total elevation to get expected values for encoder position
    expectMoveMsgsWithDest(getenc(testdata.get(testdata.size() - 1)));

    // Now update to tromboneHCD = none
    fc.tell(new TromboneAssembly.UpdateTromboneHCD(Optional.empty()), self());

    tcsEvents.forEach(f -> {
      logger.info("Publish: " + f);
      tcsRtc.publish(f);
      // The following is not required, but is added to make the event timing more interesting
      // Varying this delay from 50 to 10 shows completion of moves and at 10 update of move positions before finishing
      try {
        Thread.sleep(500); // 500 makes it seem more interesting to watch, but is not needed for proper operation
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    // Should get no messages
//    fakeAssembly.expectNoMsg(duration("200 milli"));

    // Stop this follow command
    system.stop(fc);
    system.stop(eventPublisher);
    tromboneHCD.tell(PoisonPill.getInstance(), self());
  }

}
