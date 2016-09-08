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
import csw.util.config.StateVariable;
import csw.util.config.StateVariable.CurrentState;
import javacsw.services.loc.JConnectionType;
import javacsw.services.pkg.JComponent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;
import static javacsw.util.config.JItems.*;
import static javacsw.util.config.JConfigDSL.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static javacsw.services.pkg.JSupervisor3.*;
import static javacsw.util.config.JUnitsOfMeasure.seconds;
import static org.junit.Assert.*;
import static csw.examples.vsliceJava.hcd.SingleAxisSimulator.*;
import static csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisState.AXIS_IDLE;
import static csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisState.AXIS_MOVING;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
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

  //  def waitForMoveMsgs: Seq[CurrentState] = {
//    val msgs = receiveWhile(5.seconds) {
//      case m@CurrentState(ck, items) if ck.prefix.contains(TromboneHCD.axisStatePrefix) && m(TromboneHCD.stateKey).head == TromboneHCD.AXIS_MOVING => m
//      // This is present to pick up the first status message
//      case st@CurrentState(ck, items) if ck.prefix.equals(TromboneHCD.axisStatsPrefix) => st
//    }
//    val fmsg = expectMsgClass(classOf[CurrentState]) // last one
//    val allmsgs = msgs :+ fmsg
//    allmsgs
//  }
  List<CurrentState> waitForMoveMsgs() {
    final CurrentState[] msgs =
      new ReceiveWhile<CurrentState>(CurrentState.class, duration("5 seconds")) {
        protected CurrentState match(Object in) {
          if (in instanceof CurrentState) {
            CurrentState cs = (CurrentState)in;
            if (cs.prefix().contains(TromboneHCD.axisStatePrefix) && jvalue(jitem(cs, TromboneHCD.stateKey)).equals(TromboneHCD.AXIS_MOVING)) {
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
      assertEquals(ua.current.state, AXIS_IDLE); // This is simulator value
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

//    it("should allow fetching config") {
//
//      val (supervisor, tla) = newTestTrombone()
//      lifecycleStart(supervisor, tla)
//
//      tla ! Subscribe
//      tla ! GetAxisConfig
//
//      val config = expectMsgClass(classOf[CurrentState])
//      //println("AxisStats: " + config)
//      config(axisNameKey).head equals tla.underlyingActor.axisConfig.axisName
//      config(lowLimitKey).head, tla.underlyingActor.axisConfig.lowLimit)
//      config(lowUserKey).head, tla.underlyingActor.axisConfig.lowUser)
//      config(highUserKey).head, tla.underlyingActor.axisConfig.highUser)
//      config(highLimitKey).head, tla.underlyingActor.axisConfig.highLimit)
//      config(homeValueKey).head, tla.underlyingActor.axisConfig.home)
//      config(startValueKey).head, tla.underlyingActor.axisConfig.startPosition)
//      config(stepDelayMSKey).head, tla.underlyingActor.axisConfig.stepDelayMS)
//
//      tla ! Unsubscribe
//
//      tla.underlyingActor.context.stop(tla)
//    }
//
//    it("should allow fetching stats") {
//
//      val (supervisor, tla) = newTestTrombone()
//      lifecycleStart(supervisor, tla)
//
//      tla ! Subscribe
//      tla ! GetAxisStats
//
//      val stats = expectMsgClass(classOf[CurrentState])
//      //println("AxisStats: " + stats)
//      stats(datumCountKey).head, 0)
//      stats(moveCountKey).head, 0)
//      stats(homeCountKey).head, 0)
//      stats(limitCountKey).head, 0)
//      stats(successCountKey).head, 0)
//      stats(failureCountKey).head, 0)
//      stats(cancelCountKey).head, 0)
//
//      tla ! Unsubscribe
//
//      tla.underlyingActor.context.stop(tla)
//    }
//
//    it("should allow external init when running") {
//
//      val (supervisor, tla) = newTestTrombone()
//      lifecycleStart(supervisor, tla)
//
//      tla ! Subscribe
//      tla ! Submit(datumSC)
//
//      val msgs = waitForMoveMsgs
//      msgs.last(positionKey).head should equal(tla.underlyingActor.axisConfig.startPosition + 1) // Init position is one off the start position
//      //info("Msgs: " + msgs)
//
//      tla ! GetAxisStats
//      val stats = expectMsgClass(classOf[CurrentState])
//      //println("Stats: " + stats)
//      stats.configKey should equal(TromboneHCD.axisStatsCK)
//      stats.item(datumCountKey).head should equal(1)
//      stats.item(moveCountKey).head should equal(1)
//
//      tla ! Unsubscribe
//      system.stop(tla)
//    }
//
//    it("should allow homing") {
//
//      // Note there is no test actor ref
//      val (supervisor, tla) = newTrombone()
//      lifecycleStart(supervisor, tla)
//
//      tla ! Subscribe
//      // Being done this way to ensure ConfigKey equality works
//      val sc = SetupConfig(axisHomePrefix)
//      tla ! Submit(sc)
//
//      val msgs = waitForMoveMsgs
//      //info("Msgs: " + msgs)
//      msgs.last(positionKey).head should equal(300)
//      msgs.last(inHomeKey).head should equal(true)
//      msgs.last(inLowLimitKey).head should equal(false)
//      msgs.last(inHighLimitKey).head should equal(false)
//
//      tla ! GetAxisStats
//      val stats = expectMsgClass(classOf[CurrentState])
//      //info(s"Stats: $stats")
//      stats.configKey should equal(TromboneHCD.axisStatsCK)
//      stats.item(homeCountKey).head should equal(1)
//      stats.item(moveCountKey).head should equal(1)
//
//      tla ! Unsubscribe
//
//      system.stop(tla)
//    }
//
//    it("should allow a short move") {
//
//      val (supervisor, tla) = newTrombone()
//      lifecycleStart(supervisor, tla)
//
//      val testPos = 500
//
//      tla ! Subscribe
//      tla ! Submit(positionSC(testPos))
//
//      val msgs = waitForMoveMsgs
//      // Check the last message
//      msgs.last(positionKey).head, testPos)
//      msgs.last(stateKey).head, AXIS_IDLE)
//
//      //info("Msgs: " + msgs)
//      tla ! Unsubscribe
//
//      system.stop(tla)
//    }
//
//
//    it("should allow continuous short values") {
//
//      val encoderTestValues: Vector[Int] = Vector(
//        460, 465, 470, 475, 480, 485, 490, 400
//      )
//
//      val (supervisor, tla) = newTrombone()
//      lifecycleStart(supervisor, tla)
//
//      tla ! Subscribe
//      // Move 2
//      tla ! Submit(homeSC)
//      var msgs = waitForMoveMsgs
//      msgs.last(inHomeKey).head, true)
//
//      encoderTestValues.foreach { testPos =>
//        tla ! Submit(positionSC(testPos))
//        //val msgs = waitForMoveMsgs
//      }
//      waitForMoveMsgs

    }

//    describe("place into the low limit") {
//      import csw.services.ccs.HcdController._
//
//      it("should show entering a low limit") {
//
//        val (supervisor, tla) = newTestTrombone()
//        lifecycleStart(supervisor, tla)
//
//        val testPos = 0
//        val testActual = tla.underlyingActor.axisConfig.lowLimit
//
//        tla ! Subscribe
//        tla ! Submit(positionSC(testPos))
//
//        val msgs = waitForMoveMsgs
//        // Check the last message
//        msgs.last(stateKey).head, AXIS_IDLE)
//        msgs.last(positionKey).head, testActual)
//        msgs.last(inLowLimitKey).head should equal(true)
//        msgs.last(inHighLimitKey).head should equal(false)
//
//        //info("Msgs: " + msgs)
//        tla ! Unsubscribe
//
//        system.stop(tla)
//      }
//    }
//
//    describe("place into the high limit") {
//      import csw.services.ccs.HcdController._
//
//      it("should show entering a high limit") {
//
//        val (supervisor, tla) = newTestTrombone()
//        lifecycleStart(supervisor, tla)
//
//        val testPos = 3000
//        val testActual = tla.underlyingActor.axisConfig.highLimit
//
//        tla ! Subscribe
//        tla ! Submit(positionSC(testPos))
//
//        val msgs = waitForMoveMsgs
//        // Check the last message
//        msgs.last(stateKey).head, AXIS_IDLE)
//        msgs.last(positionKey).head, testActual)
//        msgs.last(inLowLimitKey).head should equal(false)
//        msgs.last(inHighLimitKey).head should equal(true)
//
//        //info("Msgs: " + msgs)
//        tla ! Unsubscribe
//
//        system.stop(tla)
//      }
//    }
//
//    describe("Should support a more complex series of moves") {
//      import csw.services.ccs.HcdController._
//
//      it("should allow complex series of moves") {
//        // Starts at 350, init (351), go home, go to 423, 800, 560, highlmit at 1240, then home
//
//        val (supervisor, tla) = newTrombone()
//        lifecycleStart(supervisor, tla)
//
//        // Get state events
//        tla ! Subscribe
//
//        // Move 1
//        tla ! Submit(SetupConfig(axisDatumPrefix)) // Could use ones in TromboneHCD
//        var msgs = waitForMoveMsgs
//        msgs.last(inHomeKey).head, false)
//
//        // Move 2
//        tla ! Submit(homeSC)
//        msgs = waitForMoveMsgs
//        msgs.last(inHomeKey).head, true)
//
//        // Move 3
//        var testPos = 423
//        tla ! Submit(positionSC(testPos))
//        msgs = waitForMoveMsgs
//        // Check the last message
//        msgs.last(positionKey).head, testPos)
//        msgs.last(stateKey).head, AXIS_IDLE)
//        msgs.last(inHomeKey).head, false)
//        msgs.last(inLowLimitKey).head, false)
//        msgs.last(inHighLimitKey).head, false)
//
//        // Move 4
//        testPos = 800
//        tla ! Submit(positionSC(testPos))
//        msgs = waitForMoveMsgs
//        // Check the last message
//        msgs.last(positionKey).head, testPos)
//        msgs.last(stateKey).head, AXIS_IDLE)
//
//        // Move 5
//        testPos = 1240
//        tla ! Submit(positionSC(testPos))
//        msgs = waitForMoveMsgs
//        // Check the last message
//        msgs.last(positionKey).head, testPos)
//        msgs.last(stateKey).head, AXIS_IDLE)
//        msgs.last(inLowLimitKey).head, false)
//        msgs.last(inHighLimitKey).head, true)
//
//        // Move 6
//        tla ! Submit(homeSC)
//        msgs = waitForMoveMsgs
//        msgs.last(inHomeKey).head, true)
//        msgs.last(inLowLimitKey).head, false)
//        msgs.last(inHighLimitKey).head, false)
//
//        // Get summary stats
//        tla ! GetAxisStats
//        val stats = expectMsgClass(classOf[CurrentState])
//        //println("Stats: " + stats)
//        stats.configKey should equal(TromboneHCD.axisStatsCK)
//        stats.item(datumCountKey).head should equal(1)
//        stats.item(moveCountKey).head should equal(6)
//        stats.item(homeCountKey).head should equal(2)
//        stats.item(limitCountKey).head should equal(1)
//        stats.item(successCountKey).head should equal(6)
//        stats.item(failureCountKey).head, 0)
//        stats.item(cancelCountKey).head, 0)
//
//        tla ! Unsubscribe
//
//        system.stop(tla)
//      }
//    }
//
//    describe("Should handle a cancel of a motion") {
//      import csw.services.ccs.HcdController._
//
//      it("start up a move and cancel it") {
//
//        val (supervisor, tla) = newTrombone()
//        lifecycleStart(supervisor, tla)
//
//        val testPos = 1000
//
//        tla ! Subscribe
//        tla ! Submit(positionSC(testPos))
//
//        // wait for 2 updates
//        receiveN(2)
//        tla ! Submit(cancelSC)
//        val msgs = waitForMoveMsgs
//        // Check the last message
//        msgs.last(stateKey).head, AXIS_IDLE)
//        info("Msgs: " + msgs)
//
//        // Get summary stats
//        tla ! GetAxisStats
//        val stats = expectMsgClass(classOf[CurrentState])
//        //println("Stats: " + stats)
//        stats.configKey should equal(TromboneHCD.axisStatsCK)
//        stats.item(moveCountKey).head should equal(1)
//        stats.item(successCountKey).head should equal(1)
//        stats.item(cancelCountKey).head, 1)
//
//        tla ! Unsubscribe
//
//        system.stop(tla)
//      }
//    }
//
//    /*
//      def startHCD: ActorRef = {
//        val testInfo = HcdInfo(TromboneHCD.componentName,
//          TromboneHCD.trombonePrefix,
//          TromboneHCD.componentClassName,
//          DoNotRegister, Set(AkkaType), 1.second)
//        Supervisor3(testInfo)
//      }
//    */
//
//    def stopComponent(supervisorSystem: ActorSystem, supervisor: ActorRef, timeout: FiniteDuration) = {
//      //system.scheduler.scheduleOnce(timeout) {
//      println("STOPPING")
//      Supervisor3.haltComponent(supervisor)
//      Await.ready(supervisorSystem.whenTerminated, 5.seconds)
//      system.terminate()
//      System.exit(0)
//      //}
//    }
//
//  }

}
