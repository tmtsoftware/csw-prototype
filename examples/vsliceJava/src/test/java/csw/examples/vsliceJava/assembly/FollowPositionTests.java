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
import csw.examples.vsliceJava.TestEnv;
import csw.examples.vsliceJava.assembly.TromboneAssembly.UpdateTromboneHCD;
import csw.examples.vsliceJava.assembly.TromboneControl.GoToStagePosition;
import csw.examples.vsliceJava.hcd.TromboneHCD;
import csw.services.ccs.HcdController.Submit;
import csw.services.loc.LocationService;
import csw.services.pkg.Component;
import csw.services.pkg.Supervisor;
import csw.services.pkg.SupervisorExternal;
import csw.util.config.BooleanItem;
import csw.util.config.DoubleItem;
import csw.util.config.Events;
import csw.util.config.JavaHelpers;
import csw.util.config.StateVariable.CurrentState;
import javacsw.services.events.IEventService;
import javacsw.services.pkg.JComponent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static csw.examples.vsliceJava.assembly.Algorithms.*;
import static csw.examples.vsliceJava.assembly.AssemblyContext.*;
import static csw.examples.vsliceJava.assembly.AssemblyTestData.*;
import static csw.examples.vsliceJava.assembly.FollowActor.UpdatedEventData;
import static csw.examples.vsliceJava.hcd.TromboneHCD.*;
import static csw.util.config.Configurations.SetupConfig;
import static csw.util.config.Events.EventServiceEvent;
import static csw.util.config.Events.SystemEvent;
import static javacsw.services.loc.JConnectionType.AkkaType;
import static javacsw.services.pkg.JComponent.DoNotRegister;
import static javacsw.services.pkg.JSupervisor.HaltComponent;
import static javacsw.services.pkg.JSupervisor.LifecycleInitialized;
import static javacsw.services.pkg.JSupervisor.LifecycleRunning;
import static javacsw.util.config.JItems.jadd;
import static javacsw.util.config.JItems.jset;
import static javacsw.util.config.JPublisherActor.Subscribe;
import static javacsw.util.config.JUnitsOfMeasure.degrees;
import static javacsw.util.config.JUnitsOfMeasure.micrometers;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused", "FieldCanBeLocal", "WeakerAccess"})
public class FollowPositionTests extends JavaTestKit {

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
          log.info("Received event: " + event);
        }).
        match(GetResults.class, t -> sender().tell(new Results(msgs), self())).
        matchAny(t -> log.warning("Unknown message received: " + t)).
        build());
    }
  }

  private static ActorSystem system;
  private static LoggingAdapter logger;

  private static Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(10, TimeUnit.SECONDS));

  private static IEventService eventService;

  private static AssemblyContext assemblyContext = AssemblyTestData.TestAssemblyContext;
  TromboneCalculationConfig calculationConfig = assemblyContext.calculationConfig;
  TromboneControlConfig controlConfig = assemblyContext.controlConfig;

  // This def helps to make the test code look more like normal production code, where self() is defined in an actor class
  ActorRef self() {
    return getTestActor();
  }

  public FollowPositionTests() {
    super(system);
  }

  @BeforeClass
  public static void setup() throws Exception {
    LocationService.initInterface();
    system = ActorSystem.create();
    logger = Logging.getLogger(system, system);
    TestEnv.createTromboneAssemblyConfig(system);

    eventService = IEventService.getEventService(IEventService.defaultName, system, timeout)
      .get(5, TimeUnit.SECONDS);
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  DoubleItem pos(double position) {
    return jset(stagePositionKey, position).withUnits(stagePositionUnits);
  }

  // Used for creating followers
  DoubleItem initialElevation = naElevation(assemblyContext.calculationConfig.defaultInitialElevation);

  TestActorRef<FollowActor> newFollower(Optional<ActorRef> tromboneControl, Optional<ActorRef> publisher) {
    Props props = FollowActor.props(assemblyContext, initialElevation, setNssInUse(false), tromboneControl, publisher, Optional.empty());
    return TestActorRef.create(system, props);
  }

  TestActorRef<FollowActor> newTestElPublisher(Optional<ActorRef> tromboneControl) {
    Props testEventServiceProps = TrombonePublisher.props(assemblyContext, Optional.of(eventService), Optional.empty());
    ActorRef publisherActorRef = system.actorOf(testEventServiceProps);
    return newFollower(tromboneControl, Optional.of(publisherActorRef));
  }

  /**
   * Shortcut for creating zenith angle DoubleItem
   *
   * @param angle angle in degrees
   * @return DoubleItem with value and degrees
   */
  DoubleItem za(double angle) {
    return jset(zenithAngleKey, angle).withUnits(degrees);
  }

  /**
   * Shortcut for creating focus error DoubleItem
   *
   * @param error focus error in millimeters
   * @return DoubleItem with value and millimeters units
   */
  DoubleItem fe(double error) {
    return jset(focusErrorKey, error).withUnits(micrometers);
  }

  // Stop any actors created for a test to avoid conflict with other tests
  private void cleanup(Optional<ActorRef> tromboneHCDOpt, ActorRef... a) {
    TestProbe monitor = new TestProbe(system);
    for(ActorRef actorRef : a) {
      monitor.watch(actorRef);
      system.stop(actorRef);
      monitor.expectTerminated(actorRef, timeout.duration());
    }

    tromboneHCDOpt.ifPresent(tromboneHCD -> {
      monitor.watch(tromboneHCD);
      tromboneHCD.tell(HaltComponent, self());
      monitor.expectTerminated(tromboneHCD, timeout.duration());
    });
  }

  /*
   * Test Description: This test tests the CalculatorActor to a fake TromboneHCD to inspect the messages
   * provided by the CalculatorActor.  fakeTromboneEventSubscriber sends an UpdatedEventData event to
   * CalculatorActor, which after performing a calculation provides an HCDTromboneUpdate message to the
   * fakeTrombonePublisher. This tests input/output of CalculatorActor.
   */

  // --- connect output of calculator actor to the trombone publisher ---

  @Test
  public void test1() {
    // tests total RD to encoder is within values
    int maxEncoder = stagePositionToEncoder(controlConfig, rangeDistanceToStagePosition(maxTotalRD));
    int minEncoder = stagePositionToEncoder(controlConfig, rangeDistanceToStagePosition(minTotalRD));

    assertTrue(minEncoder > controlConfig.minEncoderLimit);
    assertTrue(maxEncoder < controlConfig.maxEncoderLimit);
  }

  // This isn't a great test, but a real system would know this transformation and test it
  // Using expected encoder values for test inputs
  @Test
  public void test2() {
    // encoder values should test
    List<Integer> result = encoderTestValues.stream().map(Pair::first).map(f -> stagePositionToEncoder(controlConfig, f))
      .collect(Collectors.toList());
    List<Integer> answers = encoderTestValues.stream().map(Pair::second)
      .collect(Collectors.toList());

    assertEquals(result, answers);
  }

  /**
   * Test Description: This test uses a fake trombone event subscriber to send an UpdatedEventData message to the
   * followActor to see that it generates a RangeDistance message to send to the trombone hardware HCD
   */
  @Test
  public void test3() {
    // should allow one update

    TestProbe fakeTromboneControl = new TestProbe(system);

    TestProbe fakeTromboneEventSubscriber = new TestProbe(system);

    // The following Optional.empty() ingores the events for AOESW from calculator
    TestActorRef<FollowActor> followActor = newFollower(Optional.of(fakeTromboneControl.ref()), Optional.empty());

    // This should result in two messages being sent, one to each actor in the given order
    // zenith angle 0 = 94 km, fe 0 = 0 so total is default initial value
    fakeTromboneEventSubscriber.send(followActor, new UpdatedEventData(za(0), fe(0), Events.getEventTime()));

    GoToStagePosition msg = fakeTromboneControl.expectMsgClass(GoToStagePosition.class);
    assertEquals(msg, new GoToStagePosition(jset(stagePositionKey, calculationConfig.defaultInitialElevation).withUnits(stagePositionUnits)));

    cleanup(Optional.empty(), followActor);
  }

  /**
   * Test Description: Similar to previous test, but with many values to test calculation and event flow.
   * Values are precalculated to it's not testing algorithms, it's testing the flow from input events to output
   */
  @Test
  public void test4() {
    // should create a proper set of HCDPositionUpdate messages

    TestProbe fakeTromboneControl = new TestProbe(system);

    // The following Optional.empty() ingores the events for AOESW from calculator
    TestActorRef<FollowActor> followActor = newFollower(Optional.of(fakeTromboneControl.ref()), Optional.empty());

    double testFE = 10.0;
    List<Pair<Double, Double>> testdata = newRangeAndElData(testFE);
    // These are the events that will be sent to the calculator to trigger position updates - range of ZA
    List<UpdatedEventData> updateMessages = testZenithAngles.stream().map(f -> new UpdatedEventData(za(f), fe(testFE), Events.getEventTime()))
      .collect(Collectors.toList());

    // fakeTromboneEventSubscriber simulates the events recevied from the Event Service and sent to CalculatorActor
    // This should result in two messages being sent, one to each actor in the given order
    TestProbe fakeTromboneEventSubscriber = new TestProbe(system);
    updateMessages.forEach(ev -> fakeTromboneEventSubscriber.send(followActor, ev));

    // The following constructs the expected messages that contain the stage positions
    // The following assumes we have models for what is to come out of the assembly.  Here we are just
    // reusing the actual equations to test that the events are proper
    // First keep focus error fixed at 10 um

    // This uses the new range values from above to create RangeDistance messages that are being delivered tothe trombonecontrol actor
    List<GoToStagePosition> msgsExpected = testdata.stream().map(p ->
      new GoToStagePosition(jset(stagePositionKey, p.first()).withUnits(stagePositionUnits)))
      .collect(Collectors.toList());

    // This collects the messages from the calculator setup above that are generated by the updateMessages.foreach above
    List<?> msgs = scala.collection.JavaConversions.asJavaCollection(
      fakeTromboneControl.receiveN(msgsExpected.size())).stream().collect(Collectors.toList());

    // The two should be equal
    assertEquals(msgsExpected, msgs);

    cleanup(Optional.empty(), followActor);
  }

  /**
   * Test Description: This adds the use of the Event Service. The test sends Zenith Angle updates from the
   * "TCS" through event service and generates trombone positions that are received by the fakeTromboneControl.
   */
  @Test
  public void test5() {
    // should create a proper published events from fake TCS flowing through Event Service to produce HCD encoder motion updates

    // Fake actor that handles sending to HCD
    TestProbe fakeTromboneControl = new TestProbe(system);

    BooleanItem nssUse = setNssInUse(false);
    // Create the follow actor and give it the actor ref of the publisher for sending calculated events
    ActorRef followActor = system.actorOf(FollowActor.props(assemblyContext, initialElevation, nssUse, Optional.of(fakeTromboneControl.ref()),
      Optional.empty(), Optional.empty()));
    // create the subscriber that listens for events from TCS for zenith angle and focus error from RTC
    ActorRef tromboneEventSubscriber = system.actorOf(TromboneEventSubscriber.props(assemblyContext, nssUse, Optional.of(followActor), eventService));

    // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
    IEventService tcsRtc = eventService;

    double testFE = 10.0;
    // Publish a single focus error. This will generate a published event
    tcsRtc.publish(new SystemEvent(focusErrorPrefix).add(fe(testFE)));

    // These are fake messages for the FollowActor that will be sent to simulate the TCS updating ZA
    List<SystemEvent> tcsEvents = testZenithAngles.stream().map(f -> new SystemEvent(zaConfigKey.prefix()).add(za(f)))
      .collect(Collectors.toList());

    // This should result in the length of tcsEvents being published, which is 15
    tcsEvents.forEach(tcsRtc::publish);

    // This is to give actors time to run and subscriptions to register
    expectNoMsg(duration("100 milli"));

    // The following constructs the expected messages that contain the encoder positions
    // The following assumes we have models for what is to come out of the assembly.  Here we are just
    // reusing the actual equations to test that the events are proper
    // First keep focus error fixed at 10 um
    List<Pair<Double, Double>> testdata = newRangeAndElData(testFE);
    // This uses the new range values from above to create RangeDistance messages that are being delivered tothe trombonecontrol actor
    List<GoToStagePosition> msgsExpected = testdata.stream().map(p ->
      new GoToStagePosition(jset(stagePositionKey, p.first()).withUnits(stagePositionUnits)))
      .collect(Collectors.toList());

    // Expect one message for the setting fe
    fakeTromboneControl.expectMsg(duration("10 seconds"), msgsExpected.get(0));
    // This collects the messages from the calculator setup above
    List<?> msgs = scala.collection.JavaConversions.asJavaCollection(
      fakeTromboneControl.receiveN(msgsExpected.size())).stream().collect(Collectors.toList());

    // The two should be equal
    assertEquals(msgsExpected, msgs);

    cleanup(Optional.empty(), followActor, tromboneEventSubscriber);
  }

  /**
   * Test Description: This test sends one upate through FollowActor to a fakeTromboneHCD,
   * through the actual TromboneControl actor that converts stage position to encoder units and commands for HCD.
   */

  // --- check output of follow actor to the TromboneHCD through the trombone control sending one event ---
  @Test
  public void test6() {
    // should allow one update

    TestProbe fakeTromboneHCD = new TestProbe(system);

    // Create the trombone control actor with the fake tromboneHCD
    ActorRef tromboneControl = system.actorOf(TromboneControl.props(assemblyContext, Optional.empty()));
    tromboneControl.tell(new UpdateTromboneHCD(Optional.of(fakeTromboneHCD.ref())), self());

    // This is simulating the events that are received from RTC and TCS
    TestProbe fakeTromboneEventSubscriber = new TestProbe(system);

    // The following Optional.empty() ingores the events for AOESW from calculator
    TestActorRef<FollowActor> followActor = newFollower(Optional.of(tromboneControl), Optional.empty());

    // This should result in one message being sent to the fakeTromboneHCD
    double testFE = 0.0;
    double testZA = 0.0;
    fakeTromboneEventSubscriber.send(followActor, new UpdatedEventData(za(testZA), fe(testFE), Events.getEventTime()));

    double totalRange = focusZenithAngleToElevationAndRangeDistance(calculationConfig, calculationConfig.defaultInitialElevation, testFE, testZA).first();

    int expectedEnc = stagePositionToEncoder(controlConfig, rangeDistanceToStagePosition(totalRange));

    // Difference here is that fakeTromboneHCD receives a Submit commaand with an encoder value only
    Submit msg = fakeTromboneHCD.expectMsgClass(Submit.class);
    assertEquals(msg, new Submit(jadd(new SetupConfig(axisMoveCK.prefix()),
      jset(positionKey, expectedEnc).withUnits(TromboneHCD.positionUnits))));

    cleanup(Optional.empty(), tromboneControl, followActor);
  }

  /**
   * Test Description: This test creates a set of UpdatedEventData messages, sends them to FollowActor, which
   * passes them to the TromboneControl which creates the Submit messages for the HCD which are received by
   * a "fake" HCD and tested
   */
  @Test
  public void test7() {
    // should create a proper set of Submit messages for the fakeTromboneHCD

    TestProbe fakeTromboneHCD = new TestProbe(system);

    // Create the trombone control actor with the fake tromboneHCD
    ActorRef tromboneControl = system.actorOf(TromboneControl.props(assemblyContext, Optional.empty()));
    tromboneControl.tell(new UpdateTromboneHCD(Optional.of(fakeTromboneHCD.ref())), self());

    // The following Optional.empty() ingores the events for AOESW from calculator
    TestActorRef<FollowActor> followActor = newFollower(Optional.of(tromboneControl), Optional.empty());

    // These are the events that will be sent to the calculator to trigger position updates
    double testFE = -10.0;
    List<UpdatedEventData> updateMessages = testZenithAngles.stream().map(f ->
      new UpdatedEventData(za(f), fe(testFE), Events.getEventTime()))
      .collect(Collectors.toList());

    // This should result in two messages being sent, one to each actor in the given order
    TestProbe fakeTromboneSubscriber = new TestProbe(system);
    updateMessages.forEach(ev -> {
      fakeTromboneSubscriber.send(followActor, ev);
      // This allows the processed messages to interleave
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    // The following constructs the expected messages that contain the encoder positions
    // The following assumes we have models for what is to come out of the assembly.  Here we are just
    // reusing the actual equations to test that the events are proper
    List<TestValue> calcData = calculatedTestData(calculationConfig, controlConfig, testFE);
    // This uses to two to create the expected messages from the calculatorActor
    List<Submit> msgsExpected = calcData.stream().map(p ->
      new Submit(positionSC(getenc(p))))
      .collect(Collectors.toList());


    // This collects the set of messages from the calculator setup above
    List<?> msgs = scala.collection.JavaConversions.asJavaCollection(
      fakeTromboneHCD.receiveN(msgsExpected.size())).stream().collect(Collectors.toList());

    // The two should be equal
    assertEquals(msgsExpected, msgs);

    cleanup(Optional.empty(), tromboneControl, followActor);
  }

  // -------------- The following set of tests use an actual tromboneHCD for testing  --------------------
  // The following are used to start a tromboneHCD for testing purposes
  ActorRef startHCD() {
    Component.HcdInfo testInfo = JComponent.hcdInfo(
      TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Collections.singleton(AkkaType), FiniteDuration.apply(1, TimeUnit.SECONDS)
    );

    return Supervisor.apply(testInfo);
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
   * Test Description: This test creates a trombone HCD to receive events from the CalculatorActor.
   * The first part is about starting the HCD and waiting for it to reach the running lifecycle state where it can receive events
   * UpdatedEventData messages are constructed and sent to the CalculatorActor, which uses them to create position updates.
   * A fake TCS sends Zenith Angle SystemEvents to the CalculatorActor which receives them
   * processes them, and sends them to the HCD which replies with CurrentState updates.
   * The fake Assembly subscribes to CurrentState messages from the HCD to check for completion and other purposes.
   */
  @Test
  public void test8() {
    // should create a proper set of HCDPositionUpdate messages for zenith angle changes through to HCD instance

    // startHCD creates an instance of the HCD
    ActorRef tromboneHCD = startHCD();

    // A test probe to act as the assembly for receiving CurrentState updates
    // For Java API use self(), to make working with receiveWhile easier
    ActorRef fakeAssembly = self();

    // Create the trombone control actor with the fake tromboneHCD
    ActorRef tromboneControl = system.actorOf(TromboneControl.props(assemblyContext, Optional.empty()));
    tromboneControl.tell(new UpdateTromboneHCD(Optional.of(tromboneHCD)), self());

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SupervisorExternal.SubscribeLifecycleCallback(fakeAssembly), self());
    expectMsgEquals(new SupervisorExternal.LifecycleStateChanged(LifecycleInitialized));
    expectMsgEquals(new SupervisorExternal.LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    // This has HCD sending updates back to Assembly
    tromboneHCD.tell(Subscribe, fakeAssembly);

    // Now we are ready to test
    // The following Optional.empty() ingores the events for AOESW from calculator
    TestActorRef<FollowActor> followActor = newFollower(Optional.of(tromboneControl), Optional.empty());

    // These are the events that will be sent to the calculator to trigger position updates
    double testFE = 10.0;
    List<UpdatedEventData> updateMessages = testZenithAngles.stream().map(f -> new UpdatedEventData(za(f), fe(testFE), Events.getEventTime()))
      .collect(Collectors.toList());

    // Fake TromboneSubscriber is acting as the actor that receives events from EventService
    // This should result in two messages being sent, one to each actor in the given order
    TestProbe fakeTromboneSubscriber = new TestProbe(system);
    updateMessages.forEach(ev -> {
      fakeTromboneSubscriber.send(followActor, ev);
      // This sleep is not required, but it makes the test more interesting by allowing the actions of the assembly and HCD to interleave
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    // The following constructs the expected messages that contain the encoder positions
    // The following assumes we have models for what is to come out of the assembly.  Here we are just
    // reusing the actual equations to test that the events are proper
    // First keep focus error fixed at 10 mm

    List<TestValue> calcData = calculatedTestData(calculationConfig, controlConfig, testFE);
    int encExpected = getenc(calcData.get(calcData.size() - 1));
    //info(s"encEx: $encExpected")

    // This collects the messages from the follower setup above - it is difficult to predict what messages will arrive from the HCD because it depends on timing of inputs
    // So we only wait for messages to stop and inspect the last message that indicates we are in the right place
    List<CurrentState> msgs = expectMoveMsgsWithDest(encExpected);
    CurrentState last = msgs.get(msgs.size() - 1);
    assertEquals(JavaHelpers.jvalue(last, positionKey), Integer.valueOf(encExpected));
    assertEquals(JavaHelpers.jvalue(last, stateKey), AXIS_IDLE);
    assertEquals(JavaHelpers.jvalue(last, inLowLimitKey), Boolean.valueOf(false));
    assertEquals(JavaHelpers.jvalue(last, inHighLimitKey), Boolean.valueOf(false));

    cleanup(Optional.of(tromboneHCD), tromboneControl, followActor);
  }


  /**
   * Test Description: This test is similar to the previous test, but it simulates changes to the focus error
   * rather than changes to the zenith angle.
   * This test creates a trombone HCD to receive events from the CalculatorActor.
   * The first part is about starting the HCD and waiting for it to reach the runing lifecycle state where it can receive events
   * A fake RTC sends focus error events to the CalculatorActor which receives them, processes them, calculates new values,
   * and sends commands to the HCD which replies with CurrentState updates.
   * The fake Assembly subscribes to CurrentState messages from the HCD to check for completion
   */
  @Test
  public void test9() {
    // should create a proper set of HCDPositionUpdate messages for focus error changes through HCD

    // startHCD creates an instance of the HCD
    ActorRef tromboneHCD = startHCD();

    // A test probe to act as the assembly for receiving CurrentState updates
    // For Java API use self(), to make working with receiveWhile easier
    ActorRef fakeAssembly = self();

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SupervisorExternal.SubscribeLifecycleCallback(fakeAssembly), self());
    expectMsgEquals(new SupervisorExternal.LifecycleStateChanged(LifecycleInitialized));
    expectMsgEquals(new SupervisorExternal.LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    // This has HCD sending updates back to Assembly
    tromboneHCD.tell(Subscribe, fakeAssembly);

    // Create the trombone control actor with the fake tromboneHCD
    ActorRef tromboneControl = system.actorOf(TromboneControl.props(assemblyContext, Optional.empty()));
    tromboneControl.tell(new UpdateTromboneHCD(Optional.of(tromboneHCD)), self());

    // Now we are ready to test
    // The following ingores the events for AOESW from calculator
    TestActorRef<FollowActor> followActor = newFollower(Optional.of(tromboneControl), Optional.empty());

    double testZA = 20.0;
    // These are the events that will be sent to the calculator to trigger position updates
    List<UpdatedEventData> updateMessages = testFocusErrors.stream().map(f -> new UpdatedEventData(za(testZA), fe(f), Events.getEventTime()))
      .collect(Collectors.toList());

    // This should result in two messages being sent, one to each actor in the given order
    TestProbe fakeTromboneSubscriber = new TestProbe(system);
    updateMessages.forEach(ev -> {
      fakeTromboneSubscriber.send(followActor, ev);
      // This delay is not needed, but makes the timing more challenging for the HCD motions
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    // The following constructs the expected messages that contain the encoder positions
    // The following assumes we have models for what is to come out of the assembly.  Here we are just
    // reusing the actual equations to test that the events are proper
    // First keep zenith angle at 10.0 and vary the focus error values and construct the total altitude
    // Look at final fe value
    //val lastFE = testFocusErrors.last

    // This produces a vector of ((fe, rangedistnace), enc) values
    List<TestValue> testdata = calculatedFETestData(calculationConfig, controlConfig, calculationConfig.defaultInitialElevation, testZA);
    int encExpected = getenc(testdata.get(testdata.size() - 1));
    //info("encEx: " + encExpected)

    // This collects the messages from the calculator setup above - it is difficult to predict what messages will arrive from the HCD because it depends on timing of inputs
    List<CurrentState> msgs = expectMoveMsgsWithDest(encExpected);
    CurrentState last = msgs.get(msgs.size() - 1);
    assertEquals(JavaHelpers.jvalue(last, positionKey), Integer.valueOf(encExpected));  // XXX TODO FIXME: test gets: 208 did not equal 343
    assertEquals(JavaHelpers.jvalue(last, stateKey), AXIS_IDLE);
    assertEquals(JavaHelpers.jvalue(last, inLowLimitKey), Boolean.valueOf(false));
    assertEquals(JavaHelpers.jvalue(last, inHighLimitKey), Boolean.valueOf(false));

    cleanup(Optional.of(tromboneHCD), tromboneControl, followActor);
  }

  /**
   * Test Description: This test creates a trombone HCD to receive events from the FollowActor.
   * This tests the entire path with fake TCS sending events through Event Service, which are received by
   * TromboneSubscriber and then processed by FollowActor, and sends them to TromboneControl
   * which sends them to the TromboneHCD, which replies with StateUpdates.
   * The first part is about starting the HCD and waiting for it to reach the runing lifecycle state where it can receive events
   * The fake Assembly subscribes to CurrentState messages from the HCD to check for completion
   */
  @Test
  public void test10() {
    // creates fake TCS/RTC events with Event Service through calculator and back to HCD instance

    // startHCD creates an instance of the HCD
    ActorRef tromboneHCD = startHCD();

    // A test probe to act as the assembly for receiving CurrentState updates
    // For Java API use self(), to make working with receiveWhile easier
    ActorRef fakeAssembly = self();

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SupervisorExternal.SubscribeLifecycleCallback(fakeAssembly), self());
    expectMsgEquals(new SupervisorExternal.LifecycleStateChanged(LifecycleInitialized));
    expectMsgEquals(new SupervisorExternal.LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    // This has HCD sending updates back to Assembly
    tromboneHCD.tell(Subscribe, fakeAssembly);

    // Ignoring the messages for AO for the moment
    // Create the trombone control for receiving axis updates
    ActorRef tromboneControl = system.actorOf(TromboneControl.props(assemblyContext, Optional.of(tromboneHCD)));

    // Create the follow actor and give it the actor ref of the publisher for sending calculated events
    // The following Optional.empty() ingores the events for AOESW from calculator
    TestActorRef<FollowActor> followActor = newFollower(Optional.of(tromboneControl), Optional.empty());

    // create the subscriber that receives events from TCS for zenith angle and focus error from RTC
    ActorRef tromboneEventSubscriber = system.actorOf(TromboneEventSubscriber.props(assemblyContext, setNssInUse(false), Optional.of(followActor), eventService));

    expectNoMsg(duration("200 milli"));

    // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
    IEventService tcsRtc = eventService;

    double testFE = 10.0;
    // Publish a single focus error. This will generate a published event
    tcsRtc.publish(new SystemEvent(focusErrorPrefix).add(fe(testFE)));
    //Thread.sleep(50)

    // The following constructs the expected messages that contain the encoder positions
    // The following assumes we have models for what is to come out of the assembly.  Here we are just
    // reusing the actual equations to test that the events are working properly.
    // First keep focus error fixed at 10 um
    List<TestValue> testdata = calculatedTestData(calculationConfig, controlConfig, testFE);

    // This uses the total elevation to get expected values for encoder position
    int encExpected = getenc(testdata.get(0));
    //info(s"encExpected1: $encExpected")

    // This gets the first set of CurrentState messages for moving to the FE 10 mm position
    List<CurrentState> msgs = waitForMoveMsgs();
    CurrentState last = msgs.get(msgs.size() - 1);
    assertEquals(JavaHelpers.jvalue(last, positionKey), Integer.valueOf(encExpected));
    assertEquals(JavaHelpers.jvalue(last, stateKey), AXIS_IDLE);
    assertEquals(JavaHelpers.jvalue(last, inLowLimitKey), Boolean.valueOf(false));
    assertEquals(JavaHelpers.jvalue(last, inHighLimitKey), Boolean.valueOf(false));

    // These are fake messages for the FollowActor that will be sent to simulate the TCS
    List<SystemEvent> tcsEvents = testZenithAngles.stream().map(f -> new SystemEvent(zaConfigKey.prefix()).add(za(f)))
      .collect(Collectors.toList());

    // This should result in the length of tcsEvents being published, which is 15
    tcsEvents.forEach(f -> {
      logger.info("Publish: " + f);
      tcsRtc.publish(f);
      // The following is not required, but is added to make the event timing more interesting
      // Varying this delay from 50 to 10 shows completion of moves and at 10 update of move positions before finishing
      try {
        Thread.sleep(15);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    // This collects the messages from the calculator setup above - it is difficult to predict what messages will arrive from the HCD because it depends on timing of inputs
    encExpected = getenc(testdata.get(testdata.size() - 1));
    //info(s"encExpected2: $encExpected")
    msgs = expectMoveMsgsWithDest(encExpected);
    CurrentState last2 = msgs.get(msgs.size() - 1);
    assertEquals(JavaHelpers.jvalue(last2, positionKey), Integer.valueOf(encExpected));
    assertEquals(JavaHelpers.jvalue(last2, stateKey), AXIS_IDLE);
    assertEquals(JavaHelpers.jvalue(last2, inLowLimitKey), Boolean.valueOf(false));
    assertEquals(JavaHelpers.jvalue(last2, inHighLimitKey), Boolean.valueOf(false));

    cleanup(Optional.of(tromboneHCD), tromboneEventSubscriber, tromboneControl, followActor);
  }

}
