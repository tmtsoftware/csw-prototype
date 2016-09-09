package csw.examples.vsliceJava.hcd;

import akka.actor.ActorRef;
import akka.testkit.TestActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import akka.util.Timeout;
import csw.services.loc.LocationService;
import csw.services.pkg.Component.HcdInfo;
import csw.util.config.Configurations.SetupConfig;
import csw.util.config.StateVariable.CurrentState;
import javacsw.services.ccs.JHcdController;
import javacsw.services.loc.JConnectionType;
import javacsw.services.pkg.JComponent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import static csw.examples.vsliceJava.hcd.TromboneHCD.TromboneEngineering.GetAxisConfig;
import static csw.examples.vsliceJava.hcd.TromboneHCD.*;
import static csw.examples.vsliceJava.hcd.TromboneHCD.TromboneEngineering.GetAxisStats;
import static javacsw.util.config.JItems.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static javacsw.services.pkg.JSupervisor3.*;
import static org.junit.Assert.*;

@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused"})
public class TromboneHCDBasicTests extends JavaTestKit {
  private static ActorSystem system;
  Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(60, TimeUnit.SECONDS));

  // This def helps to make the test code look more like normal production code, where self() is defined in an actor class
  ActorRef self() {
    return getTestActor();
  }

  // For compatibility with Scala tests
  void it(String s) {
    System.out.println(s);
  }

  public TromboneHCDBasicTests() {
    super(system);
  }

  @BeforeClass
  public static void setup() {
    LocationService.initInterface();
    system = ActorSystem.create();
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  String troboneAssemblyPrefix = "nfiraos.ncc.trombone";

  HcdInfo testInfo = JComponent.hcdInfo(
    TromboneHCD.componentName,
    TromboneHCD.trombonePrefix,
    TromboneHCD.componentClassName,
    JComponent.DoNotRegister,
    Collections.singleton(JConnectionType.AkkaType),
    FiniteDuration.create(1, "second"));


  Props getTromboneProps(HcdInfo hcdInfo, Optional<ActorRef> supervisorIn) {
    if (supervisorIn.isPresent())
      return TromboneHCD.props(hcdInfo, supervisorIn.get());
    else
      return TromboneHCD.props(hcdInfo, new TestProbe(system).ref());
  }

  // In place of Scala pair...
  static class TestProbeActorRefPair {
    TestProbe testProbe;
    ActorRef actorRef;

    public TestProbeActorRefPair(TestProbe testProbe, ActorRef actorRef) {
      this.testProbe = testProbe;
      this.actorRef = actorRef;
    }
  }

  TestProbeActorRefPair newTrombone() {
    HcdInfo hcdInfo = testInfo;
    TestProbe supervisor = new TestProbe(system);
    Props props = getTromboneProps(hcdInfo, Optional.of(supervisor.ref()));
    return new TestProbeActorRefPair(supervisor, system.actorOf(props));
  }

  // In place of Scala pair...
  static class TestProbeTestActorRefPair {
    TestProbe testProbe;
    TestActorRef<TromboneHCD> testActorRef;

    public TestProbeTestActorRefPair(TestProbe testProbe, TestActorRef<TromboneHCD> testActorRef) {
      this.testProbe = testProbe;
      this.testActorRef = testActorRef;
    }
  }

  TestProbeTestActorRefPair newTestTrombone() {
    HcdInfo hcdInfo = testInfo;
    TestProbe supervisor = new TestProbe(system);
    Props props = getTromboneProps(hcdInfo, Optional.of(supervisor.ref()));
    return new TestProbeTestActorRefPair(supervisor, TestActorRef.create(system, props));
  }

  void lifecycleStart(TestProbe supervisor, ActorRef tla) {
    supervisor.expectMsg(Initialized);
    supervisor.expectMsg(Started);

    supervisor.send(tla, Running);
  }

  Vector<CurrentState> waitForMoveMsgs() {
    final CurrentState[] msgs =
      new ReceiveWhile<CurrentState>(CurrentState.class, duration("5 seconds")) {
        protected CurrentState match(Object in) {
          if (in instanceof CurrentState) {
            CurrentState cs = (CurrentState)in;
            if (cs.prefix().contains(TromboneHCD.axisStatePrefix) && jvalue(jitem(cs, stateKey)).name().equals(AXIS_MOVING.name())) {
              return cs;
            }
            // This is present to pick up the first status message
            if (cs.prefix().equals(TromboneHCD.axisStatsPrefix)) {
              return cs;
            }
          }
          throw noMatch();
        }
      }.get(); // this extracts the received messages

    CurrentState fmsg = expectMsgClass(CurrentState.class); // last one

    Vector<CurrentState> allmsgs = new Vector<>(Arrays.asList(msgs));
    allmsgs.add(fmsg);
    return allmsgs;
  }

  List<CurrentState> waitForAllMsgs() {
    final CurrentState[] msgs =
      new ReceiveWhile<CurrentState>(CurrentState.class, duration("5 seconds")) {
        protected CurrentState match(Object in) {
          if (in instanceof CurrentState) {
            CurrentState cs = (CurrentState)in;
            if (cs.prefix().contains(TromboneHCD.axisStatePrefix)) {
              return cs;
            }
            // This is present to pick up the first status message
            if (cs.prefix().equals(TromboneHCD.axisStatsPrefix)) {
              return cs;
            }
          }
          throw noMatch();
        }
      }.get(); // this extracts the received messages

    CurrentState fmsg = expectMsgClass(CurrentState.class); // last one

    List<CurrentState> allmsgs = Arrays.asList(msgs);
    allmsgs.add(fmsg);
    return allmsgs;
  }

  @Test
  public void lowLevelInstrumentedTromboneHcdTests() throws Exception {

    it("should initialize the trombone axis simulator");
    {
      TestActorRef<TromboneHCD> tla = newTestTrombone().testActorRef;
      TromboneHCD ua = tla.underlyingActor();

      assertNotNull(ua.tromboneAxis);

      // Should have initialized the current values in HCD from Axis
      assertEquals(ua.current.current, ua.axisConfig.startPosition);
      assertEquals(ua.current.state, SingleAxisSimulator.AxisState.AXIS_IDLE); // This is simulator value
      assertEquals(ua.current.inHighLimit, false);
      assertEquals(ua.current.inLowLimit, false);
      assertEquals(ua.current.inHomed, false);

      // Should initialize the statistics
      assertEquals(ua.stats.limitCount, 0);
      assertEquals(ua.stats.cancelCount, 0);
      assertEquals(ua.stats.failureCount, 0);
      assertEquals(ua.stats.homeCount, 0);
      assertEquals(ua.stats.initCount, 0);
      assertEquals(ua.stats.moveCount, 0);
      assertEquals(ua.stats.successCount, 0);
    }

    it("should lifecycle properly with a fake supervisor");
    {
      TestProbeTestActorRefPair t = newTestTrombone();
      TestProbe supervisor = t.testProbe;
      TestActorRef<TromboneHCD> tla = t.testActorRef;

      supervisor.expectMsg(Initialized);
      supervisor.expectMsg(Started);

      supervisor.send(tla, Running);

      supervisor.send(tla, DoShutdown);
      supervisor.expectMsg(ShutdownComplete);
    }

    it("should allow fetching config");
    {
      TestProbeTestActorRefPair t = newTestTrombone();
      TestProbe supervisor = t.testProbe;
      TestActorRef<TromboneHCD> tla = t.testActorRef;

      lifecycleStart(supervisor, tla);

      tla.tell(JHcdController.Subscribe, self());
      tla.tell(GetAxisConfig, self());

      CurrentState config = expectMsgClass(CurrentState.class);
      //System.out.println("AxisStats: " + config)
      assertEquals(jvalue(jitem(config, axisNameKey)), tla.underlyingActor().axisConfig.axisName);
      assertEquals(jvalue(jitem(config, lowLimitKey)).intValue(), tla.underlyingActor().axisConfig.lowLimit);
      assertEquals(jvalue(jitem(config, highUserKey)).intValue(), tla.underlyingActor().axisConfig.highUser);
      assertEquals(jvalue(jitem(config, highLimitKey)).intValue(), tla.underlyingActor().axisConfig.highLimit);
      assertEquals(jvalue(jitem(config, homeValueKey)).intValue(), tla.underlyingActor().axisConfig.home);
      assertEquals(jvalue(jitem(config, startValueKey)).intValue(), tla.underlyingActor().axisConfig.startPosition);
      assertEquals(jvalue(jitem(config, stepDelayMSKey)).intValue(), tla.underlyingActor().axisConfig.stepDelayMS);

      tla.tell(JHcdController.Unsubscribe, self());

      tla.underlyingActor().context().stop(tla);
    }

    it("should allow fetching stats");
    {
      TestProbeTestActorRefPair t = newTestTrombone();
      TestProbe supervisor = t.testProbe;
      TestActorRef<TromboneHCD> tla = t.testActorRef;

      lifecycleStart(supervisor, tla);

      tla.tell(JHcdController.Subscribe, self());
      tla.tell(GetAxisStats, self());

      CurrentState stats = expectMsgClass(CurrentState.class);
      //System.out.println("AxisStats: " + stats);
      assertEquals(jvalue(jitem(stats, datumCountKey)).intValue(), 0);
      assertEquals(jvalue(jitem(stats, moveCountKey)).intValue(), 0);
      assertEquals(jvalue(jitem(stats, homeCountKey)).intValue(), 0);
      assertEquals(jvalue(jitem(stats, limitCountKey)).intValue(), 0);
      assertEquals(jvalue(jitem(stats, successCountKey)).intValue(), 0);
      assertEquals(jvalue(jitem(stats, failureCountKey)).intValue(), 0);
      assertEquals(jvalue(jitem(stats, cancelCountKey)).intValue(), 0);

      tla.tell(JHcdController.Unsubscribe, self());

      tla.underlyingActor().context().stop(tla);
    }

    it("should allow external init when running");
    {
      TestProbeTestActorRefPair t = newTestTrombone();
      TestProbe supervisor = t.testProbe;
      TestActorRef<TromboneHCD> tla = t.testActorRef;

      lifecycleStart(supervisor, tla);

      tla.tell(JHcdController.Subscribe, self());
      tla.tell(new Submit(datumSC), self());

      Vector<CurrentState> msgs = waitForMoveMsgs();
      assertEquals(
        jvalue(jitem(msgs.lastElement(), positionKey)).intValue(),
        tla.underlyingActor().axisConfig.startPosition + 1); // Init position is one off the start position
      //info("Msgs: " + msgs)

      tla.tell(GetAxisStats, self());
      CurrentState stats = expectMsgClass(CurrentState.class);
      //println("Stats: " + stats)
      assertEquals(stats.configKey(), TromboneHCD.axisStatsCK);
      assertEquals(stats.item(datumCountKey).head(), 1);
      assertEquals(stats.item(moveCountKey).head(), 1);

      tla.tell(JHcdController.Unsubscribe, self());
      system.stop(tla);
    }

    it("should allow homing");
    {
      // Note there is no test actor ref
      TestProbeTestActorRefPair t = newTestTrombone();
      TestProbe supervisor = t.testProbe;
      TestActorRef<TromboneHCD> tla = t.testActorRef;

      lifecycleStart(supervisor, tla);

      tla.tell(JHcdController.Subscribe, self());
      // Being done this way to ensure ConfigKey equality works
      SetupConfig sc = SetupConfig(axisHomePrefix);
      tla.tell(new Submit(sc), self());

      Vector<CurrentState> msgs = waitForMoveMsgs();
      //info("Msgs: " + msgs)

      assertEquals(jvalue(jitem(msgs.lastElement(), positionKey)).intValue(), 300);
      assertEquals(jvalue(jitem(msgs.lastElement(), inHomeKey)), true);
      assertEquals(jvalue(jitem(msgs.lastElement(), inLowLimitKey)), false);
      assertEquals(jvalue(jitem(msgs.lastElement(), inHighLimitKey)), false);

      tla.tell(GetAxisStats, self());
      CurrentState stats = expectMsgClass(CurrentState.class);
      //info(s"Stats: $stats")
      assertEquals(stats.configKey(), TromboneHCD.axisStatsCK);
      assertEquals(stats.item(homeCountKey).head(), 1);
      assertEquals(stats.item(moveCountKey).head(), 1);

      tla.tell(JHcdController.Unsubscribe, self());
      system.stop(tla);
    }

    it("should allow a short move");
    {
      TestProbeTestActorRefPair t = newTestTrombone();
      TestProbe supervisor = t.testProbe;
      TestActorRef<TromboneHCD> tla = t.testActorRef;

      lifecycleStart(supervisor, tla);

      int testPos = 500;

      tla.tell(JHcdController.Subscribe, self());
      tla.tell(new Submit(positionSC(testPos)), self());

      Vector<CurrentState> msgs = waitForMoveMsgs();
      // Check the last message
      assertEquals(jvalue(jitem(msgs.lastElement(), positionKey)).intValue(), testPos);
      assertEquals(jvalue(jitem(msgs.lastElement(), stateKey)), AXIS_IDLE);


      //info("Msgs: " + msgs)
      tla.tell(JHcdController.Unsubscribe, self());
      system.stop(tla);
    }


    it("should allow continuous short values");
    {
      int[] encoderTestValues = new int[]{460, 465, 470, 475, 480, 485, 490, 400};

      TestProbeTestActorRefPair t = newTestTrombone();
      TestProbe supervisor = t.testProbe;
      TestActorRef<TromboneHCD> tla = t.testActorRef;

      lifecycleStart(supervisor, tla);

      tla.tell(JHcdController.Subscribe, self());
      // Move 2
      tla.tell(new Submit(homeSC), self());
      Vector<CurrentState> msgs = waitForMoveMsgs();
      assertEquals(jvalue(jitem(msgs.lastElement(), inHomeKey)), true);

      for (int testPos : encoderTestValues) {
        tla.tell(new Submit(positionSC(testPos)), self());
        //val msgs = waitForMoveMsgs
      }
      waitForMoveMsgs();

      tla.tell(JHcdController.Unsubscribe, self());
      system.stop(tla);
    }
  }

  @Test
  public void placeIntoTheLowLimit() throws Exception {

      it("should show entering a low limit");
      {
        TestProbeTestActorRefPair t = newTestTrombone();
        TestProbe supervisor = t.testProbe;
        TestActorRef<TromboneHCD> tla = t.testActorRef;

        lifecycleStart(supervisor, tla);

        int testPos = 0;
        int testActual = tla.underlyingActor().axisConfig.lowLimit;

        tla.tell(JHcdController.Subscribe, self());
        tla.tell(new Submit(positionSC(testPos)), self());

        Vector<CurrentState> msgs = waitForMoveMsgs();
        // Check the last message
        assertEquals(jvalue(jitem(msgs.lastElement(), stateKey)), AXIS_IDLE);
        assertEquals(jvalue(jitem(msgs.lastElement(), positionKey)).intValue(), testActual);
        assertEquals(jvalue(jitem(msgs.lastElement(), inLowLimitKey)), true);
        assertEquals(jvalue(jitem(msgs.lastElement(), inHighLimitKey)), false);

        //info("Msgs: " + msgs)
        tla.tell(JHcdController.Unsubscribe, self());
        system.stop(tla);
      }
    }

  @Test
  public void placeIntoTheHighLimit() throws Exception {
      it("should show entering a high limit");
      {
        TestProbeTestActorRefPair t = newTestTrombone();
        TestProbe supervisor = t.testProbe;
        TestActorRef<TromboneHCD> tla = t.testActorRef;

        lifecycleStart(supervisor, tla);

        int testPos = 3000;
        int testActual = tla.underlyingActor().axisConfig.highLimit;

        tla.tell(JHcdController.Subscribe, self());
        tla.tell(new Submit(positionSC(testPos)), self());

        Vector<CurrentState> msgs = waitForMoveMsgs();
        // Check the last message
        assertEquals(jvalue(jitem(msgs.lastElement(), stateKey)), AXIS_IDLE);
        assertEquals(jvalue(jitem(msgs.lastElement(), positionKey)).intValue(), testActual);
        assertEquals(jvalue(jitem(msgs.lastElement(), inLowLimitKey)), false);
        assertEquals(jvalue(jitem(msgs.lastElement(), inHighLimitKey)), true);

        //info("Msgs: " + msgs)
        tla.tell(JHcdController.Unsubscribe, self());
        system.stop(tla);
      }
    }

  @Test
  public void ShouldSupportAMoreComplexSeriesOfMoves() throws Exception {
      it("should allow complex series of moves");
      {
        // Starts at 350, init (351), go home, go to 423, 800, 560, highlmit at 1240, then home

        TestProbeTestActorRefPair t = newTestTrombone();
        TestProbe supervisor = t.testProbe;
        TestActorRef<TromboneHCD> tla = t.testActorRef;

        lifecycleStart(supervisor, tla);

        // Get state events
        tla.tell(JHcdController.Subscribe, self());

        // Move 1
        tla.tell(new Submit(SetupConfig(axisDatumPrefix)), self()); // Could use ones in TromboneHCD
        Vector<CurrentState> msgs = waitForMoveMsgs();
        assertEquals(jvalue(jitem(msgs.lastElement(), inHomeKey)), false);

        // Move 2
        tla.tell(new Submit(homeSC), self());
        msgs = waitForMoveMsgs();
        assertEquals(jvalue(jitem(msgs.lastElement(), inHomeKey)), true);

        // Move 3
        int testPos = 423;
        tla.tell(new Submit(positionSC(testPos)), self());
        msgs = waitForMoveMsgs();
        // Check the last message
        assertEquals(jvalue(jitem(msgs.lastElement(), positionKey)).intValue(), testPos);
        assertEquals(jvalue(jitem(msgs.lastElement(), stateKey)), AXIS_IDLE);
        assertEquals(jvalue(jitem(msgs.lastElement(), inHomeKey)), false);
        assertEquals(jvalue(jitem(msgs.lastElement(), inLowLimitKey)), false);
        assertEquals(jvalue(jitem(msgs.lastElement(), inHighLimitKey)), false);

        // Move 4
        testPos = 800;
        tla.tell(new Submit(positionSC(testPos)), self());
        msgs = waitForMoveMsgs();
        // Check the last message
        assertEquals(jvalue(jitem(msgs.lastElement(), positionKey)).intValue(), testPos);
        assertEquals(jvalue(jitem(msgs.lastElement(), stateKey)), AXIS_IDLE);

        // Move 5
        testPos = 1240;
        tla.tell(new Submit(positionSC(testPos)), self());
        msgs = waitForMoveMsgs();
        // Check the last message
        assertEquals(jvalue(jitem(msgs.lastElement(), positionKey)).intValue(), testPos);
        assertEquals(jvalue(jitem(msgs.lastElement(), stateKey)), AXIS_IDLE);
        assertEquals(jvalue(jitem(msgs.lastElement(), inLowLimitKey)), false);
        assertEquals(jvalue(jitem(msgs.lastElement(), inHighLimitKey)), true);

        // Move 6
        tla.tell(new Submit(homeSC), self());
        msgs = waitForMoveMsgs();
        assertEquals(jvalue(jitem(msgs.lastElement(), inHomeKey)), true);
        assertEquals(jvalue(jitem(msgs.lastElement(), inLowLimitKey)), false);
        assertEquals(jvalue(jitem(msgs.lastElement(), inHighLimitKey)), false);

        // Get summary stats
        tla.tell(GetAxisStats, self());
        CurrentState stats = expectMsgClass(CurrentState.class);
        //println("Stats: " + stats)
        assertEquals(stats.configKey(), TromboneHCD.axisStatsCK);
        assertEquals(stats.item(datumCountKey).head(), 1);
        assertEquals(stats.item(moveCountKey).head(), 6);
        assertEquals(stats.item(homeCountKey).head(), 2);
        assertEquals(stats.item(limitCountKey).head(), 1);
        assertEquals(stats.item(successCountKey).head(), 6);
        assertEquals(stats.item(failureCountKey).head(), 0);
        assertEquals(stats.item(cancelCountKey).head(), 0);

        tla.tell(JHcdController.Unsubscribe, self());
        system.stop(tla);
      }
    }


  @Test
  public void ShouldHandleACancelOfAMotion() throws Exception {
      it("start up a move and cancel it");
      {
        TestProbeTestActorRefPair t = newTestTrombone();
        TestProbe supervisor = t.testProbe;
        TestActorRef<TromboneHCD> tla = t.testActorRef;

        lifecycleStart(supervisor, tla);

        int testPos = 1000;

        tla.tell(JHcdController.Subscribe, self());
        tla.tell(new Submit(positionSC(testPos)), self());

        // wait for 2 updates
        receiveN(2);
        tla.tell(new Submit(cancelSC), self());
        Vector<CurrentState> msgs = waitForMoveMsgs();
        // Check the last message
        assertEquals(jvalue(jitem(msgs.lastElement(), stateKey)), AXIS_IDLE);
        // info("Msgs: " + msgs)

        // Get summary stats
        tla.tell(GetAxisStats, self());
        CurrentState stats = expectMsgClass(CurrentState.class);
        //println("Stats: " + stats)
        assertEquals(stats.configKey(), TromboneHCD.axisStatsCK);
        assertEquals(stats.item(moveCountKey).head(), 1);
        assertEquals(stats.item(successCountKey).head(), 1);
        assertEquals(stats.item(cancelCountKey).head(), 1);

        tla.tell(JHcdController.Unsubscribe, self());
        system.stop(tla);
      }
    }
}
