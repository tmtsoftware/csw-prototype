package csw.examples.vsliceJava.assembly;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import akka.util.Timeout;
import csw.examples.vsliceJava.hcd.TromboneHCD;
import csw.services.loc.Connection;
import csw.services.loc.LocationService;
import csw.services.pkg.Component;
import csw.services.pkg.Supervisor3;
import csw.util.config.Events;
import javacsw.services.events.ITelemetryService;
import javacsw.services.pkg.JComponent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static csw.examples.vsliceJava.hcd.TromboneHCD.TromboneEngineering.GetAxisStats;
import static csw.util.config.Events.SystemEvent;
import static csw.util.config.Events.StatusEvent;
import static javacsw.services.loc.JConnectionType.AkkaType;
import static javacsw.services.pkg.JComponent.DoNotRegister;
import static csw.services.loc.Connection.AkkaConnection;
import static javacsw.services.pkg.JSupervisor3.LifecycleInitialized;
import static javacsw.services.pkg.JSupervisor3.LifecycleRunning;
import static csw.services.pkg.SupervisorExternal.LifecycleStateChanged;
import static csw.services.pkg.SupervisorExternal.SubscribeLifecycleCallback;
import static csw.examples.vsliceJava.hcd.TromboneHCD.TromboneEngineering.*;
import static csw.examples.vsliceJava.assembly.TrombonePublisher.AxisStateUpdate;
import static csw.examples.vsliceJava.assembly.TrombonePublisher.AxisStatsUpdate;
import static csw.examples.vsliceJava.assembly.DiagPublisher.DiagnosticState;
import static junit.framework.TestCase.assertEquals;
import static csw.examples.vsliceJava.assembly.DiagPublisher.OperationsState;
import static csw.services.loc.LocationService.Location;
import static csw.services.loc.LocationService.Unresolved;
import static csw.services.loc.LocationService.ResolvedAkkaLocation;

@SuppressWarnings("WeakerAccess")
/**
 * Test event service client, subscribes to some event
 */
class TestSubscriber extends AbstractActor {
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
  static class GetSysResults {
  }

  static class GetStatusResults {
  }

  static class SysResults {
    public final Vector<SystemEvent> msgs;

    public SysResults(Vector<SystemEvent> msgs) {
      this.msgs = msgs;
    }
  }

  static class StatusResults {
    public final Vector<StatusEvent> msgs;

    public StatusResults(Vector<StatusEvent> msgs) {
      this.msgs = msgs;
    }
  }

  Vector<SystemEvent> sysmsgs = new Vector<>();
  Vector<StatusEvent> statmsgs = new Vector<>();

  public TestSubscriber() {
    receive(ReceiveBuilder.
      match(SystemEvent.class, event -> {
        sysmsgs.add(event);
        log.debug("Received system event: " + event);
      }).
      match(Events.StatusEvent.class, event -> {
        statmsgs.add(event);
        log.debug("Received status event: " + event);
      }).
      match(GetSysResults.class, t -> sender().tell(new SysResults(sysmsgs), self())).
      match(GetStatusResults.class, t -> sender().tell(new StatusResults(statmsgs), self())).
      matchAny(t -> log.warning("Unknown message received: " + t)).
      build());
  }
}

/**
 * Diag Pubisher Tests
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused", "FieldCanBeLocal", "WeakerAccess"})
public class DiagPublisherTests extends JavaTestKit {

  private static ActorSystem system;
  private static LoggingAdapter logger;

  private static Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(10, TimeUnit.SECONDS));

  private static AssemblyContext assemblyContext = AssemblyTestData.TestAssemblyContext;

  private static ITelemetryService telemtryService;

  // This def helps to make the test code look more like normal production code, where self() is defined in an actor class
  ActorRef self() {
    return getTestActor();
  }

  public DiagPublisherTests() {
    super(system);
  }

  @BeforeClass
  public static void setup() throws Exception {
    LocationService.initInterface();
    system = ActorSystem.create();
    logger = Logging.getLogger(system, system);
    telemtryService = ITelemetryService.getTelemetryService(ITelemetryService.defaultName, system, timeout)
      .get(5, TimeUnit.SECONDS);
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  ActorRef startHCD() {
    Component.HcdInfo testInfo = JComponent.hcdInfo(
      TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Collections.singleton(AkkaType), FiniteDuration.apply(1, TimeUnit.SECONDS)
    );

    return Supervisor3.apply(testInfo);
  }

  // This is possible since trombone HCD has only one HCD
  Connection.AkkaConnection tromboneHCDConnection = (AkkaConnection)assemblyContext.info.getConnections().get(0);

  TestActorRef<DiagPublisher> newDiagPublisher(ActorRef currentStateReceiver, Optional<ActorRef> tromboneHCD, Optional<ActorRef> eventPublisher) {
    Props props = DiagPublisher.props(assemblyContext, currentStateReceiver, tromboneHCD, eventPublisher);
    return TestActorRef.create(system, props);
  }

  // --- basic diag tests ---

    /**
     * Test Description: Stimulate DiagPublisher with CurrentState events to demonstrate diag publishing in operations state.
     */
    @Test
    public void test1() {
      // should see one type of messages sent to publisher in operations mode
      ActorRef tromboneHCD = startHCD();

      TestProbe fakeAssembly = new TestProbe(system);

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));

      TestProbe fakePublisher = new TestProbe(system);

      // Use HCD as currentStateReceiver
      ActorRef dp = newDiagPublisher(tromboneHCD, Optional.of(tromboneHCD), Optional.of(fakePublisher.ref()));

      // Sending GetAxisStats and GetAxisUpdate to tromboneHCD simulates generation of messages during motion -- at least for AxisUpdate
      // Operations mode ignores AxisStats messages
      tromboneHCD.tell(GetAxisStats, self());
      // Check that nothing is happening here
      fakePublisher.expectNoMsg(FiniteDuration.apply(20, TimeUnit.MILLISECONDS));

      // Skip count is 5 so should get one message right away and then none for 4 more - just check for one
      tromboneHCD.tell(GetAxisUpdate, self());
      fakePublisher.expectMsgClass(AxisStateUpdate.class);

      system.stop(dp);
      tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
    }

    /**
     * Test Description: Stimulate DiagPublisher with CurrentState events to demonstrate diag publishing in operations mode.
     * This test shows that in operations state the skip count is 5
     */
    @Test
    public void test2() {
      // should see one state message sent to publisher in operations mode for every skipCount messages
      ActorRef tromboneHCD = startHCD();

      TestProbe fakeAssembly = new TestProbe(system);

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));

      TestProbe fakePublisher = new TestProbe(system);

      // Use HCD as currentStateReceiver
      ActorRef dp = newDiagPublisher(tromboneHCD, Optional.of(tromboneHCD), Optional.of(fakePublisher.ref()));

      // Skip count is 5 so should get one message right away and then none for 4 more
      tromboneHCD.tell(GetAxisUpdate, self());
      AxisStateUpdate msg = fakePublisher.expectMsgClass(AxisStateUpdate.class);
      tromboneHCD.tell(GetAxisUpdate, self());
      fakePublisher.expectNoMsg(FiniteDuration.apply(20, TimeUnit.MILLISECONDS));
      tromboneHCD.tell(GetAxisUpdate, self());
      fakePublisher.expectNoMsg(FiniteDuration.apply(20, TimeUnit.MILLISECONDS));
      tromboneHCD.tell(GetAxisUpdate, self());
      fakePublisher.expectNoMsg(FiniteDuration.apply(20, TimeUnit.MILLISECONDS));
      tromboneHCD.tell(GetAxisUpdate, self());
      fakePublisher.expectNoMsg(FiniteDuration.apply(20, TimeUnit.MILLISECONDS));
      tromboneHCD.tell(GetAxisUpdate, self());
      fakePublisher.expectMsgClass(AxisStateUpdate.class);

      system.stop(dp);
      tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
    }

    /**
     * Test Description: Stimulate DiagPublisher with CurrentState events to demonstrate diag publishing in diagnostic mode.
     * This test shows that in diagnostic state the skip count is 2
     */
    @Test
    public void test3() {
      // should see one state message sent to publisher in operations mode for every skipCount messages
      ActorRef tromboneHCD = startHCD();

      TestProbe fakeAssembly = new TestProbe(system);

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));

      TestProbe fakePublisher = new TestProbe(system);

      // Use HCD as currentStateReceiver
      ActorRef dp = newDiagPublisher(tromboneHCD, Optional.of(tromboneHCD), Optional.of(fakePublisher.ref()));

      dp.tell(new DiagnosticState(), self());

      // Skip count is 2 so should get a message for every other event
      tromboneHCD.tell(GetAxisUpdate, self());
      AxisStateUpdate msg = fakePublisher.expectMsgClass(AxisStateUpdate.class);
      tromboneHCD.tell(GetAxisUpdate, self());
      fakePublisher.expectNoMsg(FiniteDuration.apply(20, TimeUnit.MILLISECONDS));
      tromboneHCD.tell(GetAxisUpdate, self());
      fakePublisher.expectMsgClass(AxisStateUpdate.class);
      tromboneHCD.tell(GetAxisUpdate, self());
      fakePublisher.expectNoMsg(FiniteDuration.apply(20, TimeUnit.MILLISECONDS));
      tromboneHCD.tell(GetAxisUpdate, self());
      fakePublisher.expectMsgClass(AxisStateUpdate.class);
      tromboneHCD.tell(GetAxisUpdate, self());
      fakePublisher.expectNoMsg(FiniteDuration.apply(20, TimeUnit.MILLISECONDS));

      system.stop(dp);
      tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
    }

    /**
     * Test Description: This test shows that in diagnostic state there is also a stats event once/second.
     * This test waits for one message demonstrating that stats events are published
     */
    @Test
    public void test4() {
      // should see one stats message sent to publisher in diagnostics mode every second (current spec)
      ActorRef tromboneHCD = startHCD();

      TestProbe fakeAssembly = new TestProbe(system);

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));

      TestProbe fakePublisher = new TestProbe(system);

      // Use HCD as currentStateReceiver
      ActorRef dp = newDiagPublisher(tromboneHCD, Optional.of(tromboneHCD), Optional.of(fakePublisher.ref()));

      dp.tell(new DiagnosticState(), self());

      // Because timeout is 3 seconds, we get the one stats event after 1 second
      fakePublisher.expectMsgClass(AxisStatsUpdate.class);

      system.stop(dp);
      tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
    }

    /**
     * Test Description: Demonstrate that stats events are published once/second by waiting for 3 seconds
     * The end of the test demonstrates that the stats events are turned off properl in operations state
     */
    @Test
    public void test5() {
      // should generate several timed events in diagnostic mode
      ActorRef tromboneHCD = startHCD();

      TestProbe fakeAssembly = new TestProbe(system);

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));

      TestProbe fakeEventPublisher = new TestProbe(system);

      // Use HCD as currentStateReceiver
      ActorRef dp = newDiagPublisher(tromboneHCD, Optional.of(tromboneHCD), Optional.of(fakeEventPublisher.ref()));

      dp.tell(new DiagnosticState(), self());

      final AxisStatsUpdate[] msgs =
        new ReceiveWhile<AxisStatsUpdate>(AxisStatsUpdate.class, duration("3200 milliseconds")) {
          protected AxisStatsUpdate match(Object in) {
            if (in instanceof AxisStatsUpdate) {
              return (AxisStatsUpdate) in;
            } else {
              throw noMatch();
            }
          }
        }.get(); // this extracts the received messages


      assertEquals(msgs.length, 3);

      // Now turn them off
      dp.tell(new OperationsState(), self());
      // A delay to see that no messages arrive after one second to ensure timer is off
      fakeEventPublisher.expectNoMsg(duration("1200 milliseconds"));

      system.stop(dp);
      tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
    }

    void setLocation(Location loc) {
      // These times are important to allow time for test actors to get and process the state updates when running tests
      expectNoMsg(FiniteDuration.apply(10, TimeUnit.MILLISECONDS));
      system.eventStream().publish(loc);
      // This is here to allow the destination to run and set its state
      expectNoMsg(FiniteDuration.apply(10, TimeUnit.MILLISECONDS));
    }

    /**
     * Test Description: Test that updating the HCD actorRef during operations works properly by
     * first setting the HCD to None and then resetting it.
     */
    @Test
    public void test6() throws URISyntaxException {
      // "tromboneHCD update should work properly impacting timed events which contact the HCD"
      ActorRef tromboneHCD = startHCD();

      TestProbe fakeAssembly = new TestProbe(system);

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));

      TestProbe fakePublisher = new TestProbe(system);

      // Use HCD as currentStateReceiver
      ActorRef dp = newDiagPublisher(tromboneHCD, Optional.of(tromboneHCD), Optional.of(fakePublisher.ref()));

      dp.tell(new DiagnosticState(), self());

      // Wait for one update message
      fakePublisher.expectMsgClass(AxisStatsUpdate.class);

      // Setting HCD to None should turn off stats updates
      setLocation(new Unresolved(tromboneHCDConnection));
      expectNoMsg(FiniteDuration.apply(200, TimeUnit.MILLISECONDS)); // This is to let event bus and other actor work on slow machines
      fakePublisher.expectNoMsg(FiniteDuration.apply(1500, TimeUnit.MILLISECONDS));

      // Turn back on and wait for next event
      URI uri = new URI("http://test"); // Some fake URI for AkkaLocation
      setLocation(new ResolvedAkkaLocation(tromboneHCDConnection, uri, "", Optional.of(tromboneHCD)));
      // Wait for one update message
      fakePublisher.expectMsgClass(AxisStatsUpdate.class);

      system.stop(dp);
      tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
    }
//
//  /**
//   * These tests tie the Telemetry Service to the DiagPublisher and verify that real events are published as needed
//   */
//  describe("functionality tests using Telemetry Service") {
//
//    /**
//     * Test Description: This test creates an HCD and uses TestSubscribers to listen for diag publisher events.
//     * The diag publisher is in operations state so it requires 6 updates to produce one event
//     */
//    it("should receive status events in operations mode") {
//      import TestSubscriber._
//
//      // Create the trombone publisher for publishing SystemEvents to AOESW
//      val publisherActorRef = system.actorOf(TrombonePublisher.props(assemblyContext, None, Some(telemetryService)))
//
//      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
//      val resultSubscriber = TestActorRef(TestSubscriber.props())
//      telemetryService.subscribe(resultSubscriber, postLastEvents = false, axisStateEventPrefix)
//
//      val tromboneHCD = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      // The following is to synchronize the test with the HCD entering Running state
//      // This is boiler plate for setting up an HCD for testing
//      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//      // Use HCD as currentStateReceiver
//      newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(publisherActorRef))
//
//      // This should cause an event to be generated and received
//      // This should cause two published events since skip count is 5
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//
//      // Need to give a little time for messages to flow about and back to the subscriber
//      // On my machine in this testing envrironment this needs to be at least 1000 ms
//      expectNoMsg(1.second)
//
//      // Ask the local subscriber for all the ao events published for testing
//      resultSubscriber ! GetStatusResults
//      // Check the events received through the Event Service
//      val result = expectMsgClass(classOf[StatusResults])
//      result.msgs.size shouldBe 2
//      //info("result: " + result)
//
//      tromboneHCD ! PoisonPill
//    }
//
//    /**
//     * Test Description: This test creates an HCD and uses TestSubscribers to listen for diag publisher events.
//     * The diag publisher is in diagnostic state so it publishes an event every 2 updates
//     */
//    it("should receive status events in diagnostic mode") {
//      import TestSubscriber._
//
//      // Create the trombone publisher for publishing SystemEvents to AOESW
//      val publisherActorRef = system.actorOf(TrombonePublisher.props(assemblyContext, None, Some(telemetryService)))
//
//      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
//      val resultSubscriber = TestActorRef(TestSubscriber.props())
//      logger.info("Before subscribe")
//      telemetryService.subscribe(resultSubscriber, postLastEvents = false, axisStateEventPrefix)
//      logger.info("After subscribe")
//      logger.info("After wait")
//
//      val tromboneHCD = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      // The following is to synchronize the test with the HCD entering Running state
//      // This is boiler plate for setting up an HCD for testing
//      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//      // Use HCD as currentStateReceiver
//      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(publisherActorRef))
//      // Turn on Diagnostic state
//      dp ! DiagnosticState
//
//      // This should cause an event to be generated and received
//      // This should cause 4 published events since skip count is 2 in diag mode
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//
//      tromboneHCD ! GetAxisUpdate
//
//      // Need to give a little time for messages to flow about and back to the subscriber, this is related to start up of various systems
//      // On my machine in needs to be at least 750 ms for the subscribe to finish
//      expectNoMsg(1.second)
//
//      // Turn off timed events
//      dp ! OperationsState
//
//      // Ask the local subscriber for all the ao events published for testing
//      logger.info("Requesting Status Now")
//      resultSubscriber ! GetStatusResults
//      // Check the events received through the Event Service
//      val result = expectMsgClass(classOf[StatusResults])
//      //result.msgs.size shouldBe 4
//      info("result: " + result)
//
//      tromboneHCD ! PoisonPill
//      expectNoMsg(5.seconds)
//    }
//
//    /**
//     * Test Description: This test creates an HCD and uses TestSubscribers to listen for diag publisher events.
//     * This test is checking that the 1 per second stats events are published properly in diagnostic state
//     * It is also testing for concurrent generation of axis state events
//     */
//    it("should also receive stats events in diagnostic mode") {
//      import TestSubscriber._
//
//      // Create the trombone publisher for publishing SystemEvents to AOESW
//      val publisherActorRef = system.actorOf(TrombonePublisher.props(assemblyContext, None, Some(telemetryService)))
//
//      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
//      val resultSubscriber = TestActorRef(TestSubscriber.props())
//      telemetryService.subscribe(resultSubscriber, postLastEvents = false, axisStateEventPrefix)
//
//      // Creates a subscriber for stats events
//      val resultSubscriber2 = TestActorRef(TestSubscriber.props())
//      telemetryService.subscribe(resultSubscriber2, postLastEvents = false, axisStatsEventPrefix)
//
//      val tromboneHCD = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      // The following is to synchronize the test with the HCD entering Running state
//      // This is boiler plate for setting up an HCD for testing
//      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//      // Use HCD as currentStateReceiver
//      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(publisherActorRef))
//      dp ! DiagnosticState
//
//      // This should cause an event to be generated and received
//      // This should cause 4 published events since skip count is 2 in diag state
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//
//      // Need to give a little time for messages to flow about and back to the subscriber
//      // On my machine in needs to be at least 1000 ms with current event service
//      expectNoMsg(1.second)
//
//      // Ask the local subscriber for all the stats events received
//      resultSubscriber ! GetStatusResults
//      // Check the events received through the Event Service
//      val result = expectMsgClass(classOf[StatusResults])
//      result.msgs.size shouldBe 2 // because of 4 messages
//      //info("result: " + result)
//
//      // Now check for stats events
//      // Wait 2 seconds for at least two timed events, should result in 2 or 3 depending on timing
//      expectNoMsg(2.seconds)
//
//      // Turn off timed events
//      dp ! OperationsState
//
//      resultSubscriber2 ! GetStatusResults
//      // Check the events received through the Event Service
//      val result2 = expectMsgClass(classOf[StatusResults])
//      result2.msgs.size should be >= 2
//      //info("result: " + result2)
//
//      tromboneHCD ! PoisonPill
//    }
//
}
