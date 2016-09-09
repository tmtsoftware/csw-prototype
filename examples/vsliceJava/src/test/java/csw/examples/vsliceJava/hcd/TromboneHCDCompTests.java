package csw.examples.vsliceJava.hcd;


import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.TestActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import akka.util.Timeout;
import csw.services.ccs.HcdController;
import csw.services.loc.LocationService;
import csw.services.pkg.Component.HcdInfo;
import csw.services.pkg.Supervisor3;
import csw.services.pkg.SupervisorExternal;
import csw.services.pkg.SupervisorExternal.*;
import csw.util.config.Configurations.SetupConfig;
import csw.util.config.StateVariable.CurrentState;
import javacsw.services.ccs.JHcdController;
import javacsw.services.loc.JConnectionType;
import javacsw.services.pkg.JComponent;
import javacsw.services.pkg.JSupervisor3;
import javacsw.services.pkg.JSupervisor3.*;
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
public class TromboneHCDCompTests extends JavaTestKit {
  private static LoggingAdapter log;
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

  public TromboneHCDCompTests() {
    super(system);
    log = Logging.getLogger(system, this);
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

  HcdInfo testInfo = JComponent.hcdInfo(
    TromboneHCD.componentName,
    TromboneHCD.trombonePrefix,
    TromboneHCD.componentClassName,
    JComponent.DoNotRegister,
    Collections.singleton(JConnectionType.AkkaType),
    FiniteDuration.create(1, "second"));

  String troboneAssemblyPrefix = "nfiraos.ncc.trombone";


  ActorRef startHCD() {
    return Supervisor3.apply(testInfo);
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

  @Test
  public void componentLevelExternalPublicInterfaceTests() throws Exception {

    it("should allow fetching stats");
    {
      ActorRef hcd = startHCD();

      TestProbe fakeAssembly = new TestProbe(system);

      hcd.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
      log.info("Running");

      fakeAssembly.send(hcd, JHcdController.Subscribe);
      fakeAssembly.send(hcd, GetAxisStats);

      CurrentState stats = fakeAssembly.expectMsgClass(CurrentState.class);
      System.out.println("AxisStats: " + stats);
      assertEquals(jvalue(jitem(stats, datumCountKey)).intValue(), 0);
      assertEquals(jvalue(jitem(stats, moveCountKey)).intValue(), 0);
      assertEquals(jvalue(jitem(stats, homeCountKey)).intValue(), 0);
      assertEquals(jvalue(jitem(stats, limitCountKey)).intValue(), 0);
      assertEquals(jvalue(jitem(stats, successCountKey)).intValue(), 0);
      assertEquals(jvalue(jitem(stats, failureCountKey)).intValue(), 0);
      assertEquals(jvalue(jitem(stats, cancelCountKey)).intValue(), 0);

      hcd.tell(JHcdController.Unsubscribe, self());

      hcd.tell(PoisonPill.getInstance(), self());
      log.info("Done");
    }


    it("should allow fetching config");
    {
      ActorRef hcd = startHCD();

      TestProbe fakeAssembly = new TestProbe(system);

      hcd.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
      fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
      log.info("Running");

      fakeAssembly.send(hcd, JHcdController.Subscribe);
      fakeAssembly.send(hcd, GetAxisConfig);

      // The values are hard-coded because we can't look at the config inside the actor, will fail if config changes
      CurrentState config = fakeAssembly.expectMsgClass(CurrentState.class);
      System.out.println("AxisConfig: " + config);
      assertEquals(jvalue(jitem(config, axisNameKey)), TromboneHCD.tromboneAxisName);
      assertEquals(jvalue(jitem(config, lowLimitKey)).intValue(), 100);
      assertEquals(jvalue(jitem(config, lowUserKey)).intValue(), 200);
      assertEquals(jvalue(jitem(config, highUserKey)).intValue(), 1200);
      assertEquals(jvalue(jitem(config, highLimitKey)).intValue(), 1300);
      assertEquals(jvalue(jitem(config, homeValueKey)).intValue(), 300);
      assertEquals(jvalue(jitem(config, startValueKey)).intValue(), 350);

      fakeAssembly.send(hcd, JHcdController.Unsubscribe);
      hcd.tell(PoisonPill.getInstance(), self());
      log.info("Done");
    }


//    it("should accept an init") {
//      val hcd = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//      log.info("Running")
//      // Currently can't subscribe unless in Running state because controllerReceive has process
//      fakeAssembly.send(hcd, Subscribe)
//
//      fakeAssembly.send(hcd, Submit(datumSC))
//
//      val msgs = waitForMoveMsgs(fakeAssembly)
//      //msgs.last(positionKey).head should equal(tla.underlyingActor.axisConfig.startPosition + 1) // Init position is one off the start position
//      log.info("Msgs: " + msgs)
//
//      fakeAssembly.send(hcd, GetAxisStats)
//      val stats = fakeAssembly.expectMsgClass(classOf[CurrentState])
//      println("Stats: " + stats)
//      stats.configKey should equal(TromboneHCD.axisStatsCK)
//      stats.item(datumCountKey).head should equal(1)
//      stats.item(moveCountKey).head should equal(1)
//
//      fakeAssembly.send(hcd, Unsubscribe)
//      /*
//            val probe = TestProbe()
//            probe watch hcd
//            hcd ! PoisonPill
//            probe.expectTerminated(hcd)
//            */
//      log.info("Done")
//    }
//
//
//    it("should allow homing") {
//      val hcd = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//      log.info("Running")
//
//      // Currently can't subscribe unless in Running state because controllerReceive has process
//      fakeAssembly.send(hcd, Subscribe)
//
//      // Being done this way to ensure ConfigKey equality works
//      val sc = SetupConfig(axisHomePrefix)
//      fakeAssembly.send(hcd, Submit(sc))
//
//      val msgs = waitForMoveMsgs(fakeAssembly)
//      log.info("Msgs: " + msgs)
//      msgs.last(positionKey).head should equal(300)
//      msgs.last(inHomeKey).head should equal(true)
//      msgs.last(inLowLimitKey).head should equal(false)
//      msgs.last(inHighLimitKey).head should equal(false)
//
//      fakeAssembly.send(hcd, GetAxisStats)
//      val stats = fakeAssembly.expectMsgClass(classOf[CurrentState])
//      log.info(s"Stats: $stats")
//      stats.configKey should equal(TromboneHCD.axisStatsCK)
//      stats.item(homeCountKey).head should equal(1)
//      stats.item(moveCountKey).head should equal(1)
//
//      fakeAssembly.send(hcd, Unsubscribe)
//    }
//  }
//
//
//  it("should allow a short move") {
//    val hcd = startHCD
//
//    val fakeAssembly = TestProbe()
//
//    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    log.info("Running")
//
//    fakeAssembly.send(hcd, Subscribe)
//    // Being done this way to ensure ConfigKey equality works
//    val testPos = 500
//
//    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
//
//    val msgs = waitForMoveMsgs(fakeAssembly)
//    // Check the last message
//    msgs.last(positionKey).head should be(testPos)
//    msgs.last(stateKey).head should be(AXIS_IDLE)
//
//    fakeAssembly.send(hcd, Unsubscribe)
//  }
//
//  it("should show entering a low limit") {
//    val hcd = startHCD
//
//    val fakeAssembly = TestProbe()
//
//    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//    fakeAssembly.send(hcd, Subscribe)
//
//    val testPos = 0
//    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
//    val lowLimit = 100 // Note this will fail if axisConfig is changed
//
//    val msgs = waitForMoveMsgs(fakeAssembly)
//    // Check the last message
//    msgs.last(stateKey).head should be(AXIS_IDLE)
//    msgs.last(positionKey).head should be(lowLimit)
//    msgs.last(inLowLimitKey).head should equal(true)
//    msgs.last(inHighLimitKey).head should equal(false)
//
//    //log.info("Msgs: " + msgs)
//    fakeAssembly.send(hcd, Unsubscribe)
//
//  }
//
//  it("should show entering a high limit") {
//    val hcd = startHCD
//
//    val fakeAssembly = TestProbe()
//
//    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//    fakeAssembly.send(hcd, Subscribe)
//
//    val testPos = 3000
//    val highLimit = 1300 // Note this will fail if axisConfig is changed
//
//    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
//
//    val msgs = waitForMoveMsgs(fakeAssembly)
//    // Check the last message
//    msgs.last(stateKey).head should be(AXIS_IDLE)
//    msgs.last(positionKey).head should be(highLimit)
//    msgs.last(inLowLimitKey).head should equal(false)
//    msgs.last(inHighLimitKey).head should equal(true)
//
//    //log.info("Msgs: " + msgs)
//    fakeAssembly.send(hcd, Unsubscribe)
//  }
//
//  it("should allow complex series of moves") {
//    // Starts at 350, init (351), go home, go to 423, 800, 560, highlmit at 1240, then home
//    val hcd = startHCD
//
//    val fakeAssembly = TestProbe()
//
//    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//    fakeAssembly.send(hcd, Subscribe)
//
//    // Move 1
//    fakeAssembly.send(hcd, Submit(SetupConfig(axisDatumPrefix))) // Could use ones in TromboneHCD
//    var msgs = waitForMoveMsgs(fakeAssembly)
//    msgs.last(inHomeKey).head should be(false)
//
//    // Move 2
//    fakeAssembly.send(hcd, Submit(homeSC))
//    msgs = waitForMoveMsgs(fakeAssembly)
//    msgs.last(inHomeKey).head should be(true)
//
//    // Move 3
//    var testPos = 423
//    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
//    msgs = waitForMoveMsgs(fakeAssembly)
//    // Check the last message
//    msgs.last(positionKey).head should be(testPos)
//    msgs.last(stateKey).head should be(AXIS_IDLE)
//    msgs.last(inHomeKey).head should be(false)
//    msgs.last(inLowLimitKey).head should be(false)
//    msgs.last(inHighLimitKey).head should be(false)
//
//    // Move 4
//    testPos = 800
//    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
//    msgs = waitForMoveMsgs(fakeAssembly)
//    // Check the last message
//    msgs.last(positionKey).head should be(testPos)
//    msgs.last(stateKey).head should be(AXIS_IDLE)
//
//    // Move 5
//    testPos = 1240
//    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
//    msgs = waitForMoveMsgs(fakeAssembly)
//    // Check the last message
//    msgs.last(positionKey).head should be(testPos)
//    msgs.last(stateKey).head should be(AXIS_IDLE)
//    msgs.last(inLowLimitKey).head should be(false)
//    msgs.last(inHighLimitKey).head should be(true)
//
//    // Move 6
//    fakeAssembly.send(hcd, Submit(homeSC))
//    msgs = waitForMoveMsgs(fakeAssembly)
//    msgs.last(inHomeKey).head should be(true)
//    msgs.last(inLowLimitKey).head should be(false)
//    msgs.last(inHighLimitKey).head should be(false)
//
//    // Get summary stats
//    fakeAssembly.send(hcd, GetAxisStats)
//    val stats = fakeAssembly.expectMsgClass(classOf[CurrentState])
//    //println("Stats: " + stats)
//    stats.configKey should equal(TromboneHCD.axisStatsCK)
//    stats.item(datumCountKey).head should equal(1)
//    stats.item(moveCountKey).head should equal(6)
//    stats.item(homeCountKey).head should equal(2)
//    stats.item(limitCountKey).head should equal(1)
//    stats.item(successCountKey).head should equal(6)
//    stats.item(failureCountKey).head should be(0)
//    stats.item(cancelCountKey).head should be(0)
//
//    fakeAssembly.send(hcd, Unsubscribe)
//  }
//
//  it("start up a move and cancel it") {
//    val hcd = startHCD
//
//    val fakeAssembly = TestProbe()
//
//    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//    fakeAssembly.send(hcd, Subscribe)
//
//    val testPos = 1000
//
//    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
//
//    // wait for 2 updates
//    fakeAssembly.receiveN(2)
//    fakeAssembly.send(hcd, Submit(cancelSC))
//    val msgs = waitForMoveMsgs(fakeAssembly)
//    // Check the last message
//    msgs.last(stateKey).head should be(AXIS_IDLE)
//
//    // Get summary stats
//    fakeAssembly.send(hcd, GetAxisStats)
//    val stats = fakeAssembly.expectMsgClass(classOf[CurrentState])
//    //println("Stats: " + stats)
//    stats.configKey should equal(TromboneHCD.axisStatsCK)
//    stats.item(moveCountKey).head should equal(1)
//    stats.item(successCountKey).head should equal(1)
//    stats.item(cancelCountKey).head should be(1)
//
//    fakeAssembly.send(hcd, Unsubscribe)
//  }
//
//  it("should allow repetitive moves") {
//    // Starts at 350, init (351), go home, small moves repeating */
//    val hcd = startHCD
//
//    val fakeAssembly = TestProbe()
//
//    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//    fakeAssembly.send(hcd, Subscribe)
//
//    // Init 1
//    fakeAssembly.send(hcd, Submit(SetupConfig(axisDatumCK))) // Could use ones in TromboneHCD
//    var msgs = waitForMoveMsgs(fakeAssembly)
//    msgs.last(stateKey).head should be(AXIS_IDLE)
//
//    // Move 2
//    fakeAssembly.send(hcd, Submit(homeSC))
//    msgs = waitForMoveMsgs(fakeAssembly)
//    msgs.last(inHomeKey).head should be(true)
//
//    val start = 300
//    val finish = 500
//    val stepSize = 10
//    for (loops <- 1 to 2) {
//      logger.info(s"Loop: $loops")
//      for (testPos <- start to finish by stepSize) {
//        fakeAssembly.send(hcd, Submit(positionSC(testPos)))
//        msgs = waitForMoveMsgs(fakeAssembly)
//      }
//    }
//  }
//
//  it("should drive into limits") {
//    // Starts at 350, goes to zero */
//    val hcd = startHCD
//
//    val fakeAssembly = TestProbe()
//
//    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//    fakeAssembly.send(hcd, Subscribe)
//
//    // Get the axis config  for testing limits
//    fakeAssembly.send(hcd, GetAxisConfig)
//
//    // The values are hard-coded because we can't look at the config inside the actor, will fail if config changes
//    val config: CurrentState = fakeAssembly.expectMsgClass(classOf[CurrentState])
//    val lowLimit = config(lowLimitKey).head
//    val highLimit = config(highLimitKey).head
//
//    // Move to 0
//    var testPos = 0
//    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
//    var msgs = waitForMoveMsgs(fakeAssembly)
//    // Check the last message
//    msgs.last(positionKey).head should be(lowLimit)
//    msgs.last(stateKey).head should be(AXIS_IDLE)
//    msgs.last(inLowLimitKey).head should be(true)
//    msgs.last(inHighLimitKey).head should be(false)
//
//    // Move to 2000
//    testPos = 2000
//    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
//    msgs = waitForMoveMsgs(fakeAssembly)
//    // Check the last message
//    msgs.last(positionKey).head should be(highLimit)
//    msgs.last(stateKey).head should be(AXIS_IDLE)
//    msgs.last(inLowLimitKey).head should be(false)
//    msgs.last(inHighLimitKey).head should be(true)
//  }
//
  }

}
