package csw.examples.vsliceJava.hcd;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import akka.util.Timeout;
import csw.services.loc.LocationService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;
import csw.examples.vsliceJava.hcd.SingleAxisSimulator.*;

import java.util.Collections;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import static csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisState.AXIS_MOVING;
import static org.junit.Assert.*;
import static csw.examples.vsliceJava.hcd.MotionWorker.*;


@SuppressWarnings("unused")
public class SingleAxisSimulatorTests {
  private static ActorSystem system;
  Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(60, TimeUnit.SECONDS));

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

  Vector<MotionWorkerMsgs> expectLLMoveMsgs(final int start, final int destinationIn, final int delayInMS,
                                            final boolean diagFlag) {
    Vector<MotionWorkerMsgs> allMsgs = new Vector<>();
    new JavaTestKit(system) {{
      Props props = props(start, destinationIn, delayInMS, getRef(), diagFlag);
      final TestActorRef<MotionWorker> ms = TestActorRef.create(system, props);
      ms.tell(Start.instance, getRef());

      // Get AxisStarted
      allMsgs.add(expectMsgEquals(Start.instance));
      // Receive updates until axis idle then get the last one
      final MotionWorkerMsgs[] moveMsgs =
        new ReceiveWhile<Tick>(Tick.class, duration("5 seconds")) {
          protected Tick match(Object in) {
            if (in instanceof Tick) {
              return (Tick) in;
            } else {
              throw noMatch();
            }
          }
        }.get(); // this extracts the received messages

      MotionWorkerMsgs endMsg = expectMsgClass(End.class); // last one
      Collections.addAll(allMsgs, moveMsgs);
      allMsgs.add(endMsg);

      if (diagFlag) System.out.println("LLMoveMsgs: " + allMsgs);
    }};
    return allMsgs;
  }


  Vector<AxisUpdate> expectMoveMsgs(boolean diagFlag) {
    Vector<AxisUpdate> allMsgs = new Vector<>();
    new JavaTestKit(system) {{
      // Get AxisStarted
      expectMsgEquals(Start.instance);
      // Receive updates until axis idle then get the last one
      final AxisUpdate[] msgs =
        new ReceiveWhile<AxisUpdate>(AxisUpdate.class, duration("5 second")) {
          protected AxisUpdate match(Object in) {
            if (in instanceof AxisUpdate && ((AxisUpdate) in).state == AXIS_MOVING) {
              return (AxisUpdate) in;
            } else {
              throw noMatch();
            }
          }
        }.get(); // this extracts the received messages

      AxisUpdate fmsg = expectMsgClass(AxisUpdate.class); // last one
      Collections.addAll(allMsgs, msgs);
      allMsgs.add(fmsg);

      if (diagFlag) System.out.println("MoveMsgs: " + allMsgs);
    }};
    return allMsgs;
  }

  Vector<AxisResponse> expectMoveMsgsWithDest(int target, boolean diagFlag) {
    Vector<AxisResponse> allMsgs = new Vector<>();
    new JavaTestKit(system) {{
      // Receive updates until axis idle then get the last one
      final AxisResponse[] msgs =
        new ReceiveWhile<AxisResponse>(AxisResponse.class, duration("5 second")) {
          protected AxisResponse match(Object in) {
            if (in instanceof AxisStarted || in instanceof AxisUpdate && ((AxisUpdate) in).current != target) {
              return (AxisResponse) in;
            } else {
              throw noMatch();
            }
          }
        }.get(); // this extracts the received messages

      AxisUpdate fmsg1 = expectMsgClass(AxisUpdate.class); // last one when target == current
      AxisUpdate fmsg2 = expectMsgClass(AxisUpdate.class); // then the End event with the IDLE
      Collections.addAll(allMsgs, msgs);
      allMsgs.add(fmsg1);
      allMsgs.add(fmsg2);

      if (diagFlag) System.out.println("MoveMsgsWithDest: " + allMsgs);
    }};
    return allMsgs;
  }


  // Calculates the time to wait for messages with a little extra
  FiniteDuration calcDelay(int numberSteps, int delayInSseconds) {
    return FiniteDuration.create((numberSteps + 1) * delayInSseconds * 1000, TimeUnit.SECONDS);
  }

  @Test
  public void TestingStepsCalc() throws Exception {
    // should calculate different number of steps based on the size of the move
    assertEquals(calcNumSteps(100, 105), 1);
    assertEquals(calcNumSteps(100, 115), 2);
    assertEquals(calcNumSteps(100, 500), 5);
    assertEquals(calcNumSteps(100, 900), 10);


    // should also work with step size
    assertEquals(calcStepSize(100, 105, calcNumSteps(100, 105)), 5);
    assertEquals(calcStepSize(100, 115, calcNumSteps(100, 115)), 7);
    assertEquals(calcStepSize(100, 500, calcNumSteps(100, 500)), 80);
    assertEquals(calcStepSize(100, 900, calcNumSteps(100, 900)), 80);

    // should work with step size of 1 (bug found)
    int steps = calcNumSteps(870, 869);
    assertEquals(steps, 1);
    assertEquals(calcStepSize(870, 869, steps), -1);
  }

  @Test
  public void motionWorkerSetup() throws Exception {
    new JavaTestKit(system) {
      {
        int testStart = 0;
        int testDestination = 1000;
        int testDelay = 100;

        // should be initialized properly
        Props props = props(testStart, testDestination, testDelay, getRef(), false);
        final TestActorRef<MotionWorker> ms = TestActorRef.create(system, props);
        final MotionWorker under = ms.underlyingActor();
        assertEquals(under.start, testStart);
        assertEquals(under.destination, testDestination);
        assertEquals(under.delayInNanoSeconds, testDelay * 1000000);
      }
    };
  }

  @Test
  public void motionWorkerForward() throws Exception {
        int testStart = 0;
        int testDestination = 1005;
        int testDelay = 10;

        // should allow simulation on increasing encoder steps
        Vector<MotionWorkerMsgs> msgs = expectLLMoveMsgs(testStart, testDestination, testDelay, false);
        assertEquals(msgs.lastElement(), new End(testDestination));
  }

  @Test
  public void motionWorkerReverse() throws Exception {
        int testStart = 1000;
        int testDestination = -110;
        int testDelay = 10;

        // should allow creation based on negative encoder steps
        Vector<MotionWorkerMsgs> msgs = expectLLMoveMsgs(testStart, testDestination, testDelay, false);
        assertEquals(msgs.lastElement(), new End(testDestination));
  }

  @Test
  public void simulateContinuousMotionWithMotionWorker() throws Exception {
        int testStart = 500;
        int testDestination = 600;
        int testDelay = 10;

        Vector<MotionWorkerMsgs> msgs = expectLLMoveMsgs(testStart, testDestination, testDelay, false);
        assertEquals(msgs.lastElement(), new End(testDestination));
  }

//  describe("motion worker cancel") {
//    val testStart = 0
//    val testDestination = 1000
//    val testDelay = 200
//
//    it("should allow cancelling after a few steps") {
//      val props = props(testStart, testDestination, testDelay, self, diagFlag = false)
//      val ms = TestActorRef(props)
//      ms ! Start
//      expectMsg(Start)
//      // Wait 3 messages
//      receiveN(3, calcDelay(3, testDelay))
//      ms ! Cancel
//      // One more move
//      receiveN(1)
//      expectMsgClass(classOf[End])
//    }
//  }
//
//  val defaultAxisName = "test"
//  val defaultLowLimit = 100
//  val defaultLowUser = 200
//  val defaultHighUser = 1200
//  val defaultHighLimit = 1300
//  val defaultHome = 300
//  val defaultStartPosition = 350
//  val defaultStepDelayMS = 5
//  val defaultStatusPrefix = "test.axisStatus"
//
//  val defaultAxisConfig = AxisConfig(defaultAxisName,
//    defaultLowLimit,
//    defaultLowUser,
//    defaultHighUser,
//    defaultHighLimit,
//    defaultHome,
//    defaultStartPosition,
//    defaultStepDelayMS)
//
//  def defaultAxis(replyTo: ActorRef): TestActorRef[SingleAxisSimulator] = {
//    val props = SingleAxisSimulator.props(defaultAxisConfig, Some(replyTo))
//    TestActorRef(props) // No name here since can't create actors with the same name
//  }
//
//  describe("test single axis") {
//
//    it("should be creatable and initialize") {
//      val sa = defaultAxis(testActor)
//      sa.underlyingActor.axisConfig should equal(defaultAxisConfig)
//      // Expect an initial axis status message
//      // val one = expectMsgClass(1.second, classOf[AxisUpdate])
//      //one.current should equal(defaultAxisConfig.startPosition)
//    }
//
//    it("limitMove should clamp values") {
//      val ac = defaultAxisConfig
//
//      // Acceptable
//      limitMove(ac, 200) should equal(200)
//      // Low limit
//      limitMove(ac, 0) should equal(ac.lowLimit)
//
//      // High limit
//      limitMove(ac, 2000) should equal(ac.highLimit)
//
//      // Check "limit" checks > or < user limits
//      isHighLimit(ac, ac.home) should be(false)
//      isHighLimit(ac, ac.highUser - 1) should be(false)
//      isHighLimit(ac, ac.highUser) should be(true)
//      isHighLimit(ac, ac.highLimit) should be(true)
//
//      isLowLimit(ac, ac.home) should be(false)
//      isLowLimit(ac, ac.lowUser + 1) should be(false)
//      isLowLimit(ac, ac.lowUser) should be(true)
//      isLowLimit(ac, ac.lowLimit) should be(true)
//
//      isHomed(ac, ac.home)
//    }
//
//    it("Should init properly") {
//      val sa = defaultAxis(testActor)
//
//      // Expect an initial axis status message
//      //val one = expectMsgClass(classOf[AxisUpdate])
//      //one.current should equal(defaultAxisConfig.startPosition)
//
//      sa ! Datum
//      expectMsg(AxisStarted)
//      val upd = expectMsgClass(classOf[AxisUpdate])
//      upd.state should equal(AXIS_IDLE)
//      upd.current should equal(defaultAxisConfig.startPosition + 1)
//
//
//      sa ! GetStatistics
//      val stats1: AxisStatistics = expectMsgClass(classOf[AxisStatistics])
//      stats1.initCount should be(1)
//      stats1.moveCount should be(1)
//      stats1.homeCount should be(0)
//      stats1.limitCount should be(0)
//      stats1.successCount should be(1)
//      stats1.failureCount should be(0)
//      stats1.cancelCount should be(0)
//
//      sa ! PoisonPill
//    }
//
//    it("Should home properly") {
//      val sa = defaultAxis(testActor)
//
//      sa ! Home
//      val msgs = expectMoveMsgs()
//      msgs.last.state should be(AXIS_IDLE)
//      msgs.last.inHomed should be(true)
//      msgs.last.current should be(defaultAxisConfig.home)
//
//      sa.underlyingActor.current should be(defaultAxisConfig.home)
//
//      sa ! GetStatistics
//      val stats1: AxisStatistics = expectMsgClass(classOf[AxisStatistics])
//      stats1.initCount should be(0)
//      stats1.moveCount should be(1)
//      stats1.homeCount should be(1)
//      stats1.limitCount should be(0)
//      stats1.successCount should be(1)
//      stats1.failureCount should be(0)
//      stats1.cancelCount should be(0)
//
//      sa ! PoisonPill
//    }
//
//    it("Should move properly") {
//      val sa = defaultAxis(testActor)
//
//      // Expect an initial axis status message
//      //val one = expectMsgClass(classOf[AxisUpdate])
//      //one.current should equal(defaultAxisConfig.startPosition)
//
//      sa ! Move(500, diagFlag = false)
//      val msgs = expectMoveMsgs()
//      msgs.last.state should be(AXIS_IDLE)
//      msgs.last.current should be(500)
//
//      sa.underlyingActor.current should be(500)
//
//      sa ! PoisonPill
//    }
//
//    it("Should move and update") {
//      val sa = defaultAxis(testActor)
//
//      // Sleeps are to try and not do all the updates up front before movement starts
//      sa ! Move(360)
//      Thread.sleep(30)
//      sa ! Move(365)
//      Thread.sleep(20)
//      sa ! Move(390)
//      Thread.sleep(30)
//      sa ! Move(420)
//      Thread.sleep(20)
//      sa ! Move(425)
//
//      val msgs = expectMoveMsgsWithDest(425)
//      msgs.last.isInstanceOf[AxisUpdate]
//      val last:AxisUpdate = msgs.last.asInstanceOf[AxisUpdate]
//      last.state should be(AXIS_IDLE)
//      last.current should be(425)
//
//      sa ! PoisonPill
//    }
//
//    it("Should allow a cancel") {
//      val sa = defaultAxis(testActor)
//
//      // Expect an initial axis status message
//      //val one = expectMsgClass(classOf[AxisUpdate])
//      //one.current should equal(defaultAxisConfig.startPosition)
//
//      sa ! Move(850, diagFlag = false)
//      expectMsg(AxisStarted)
//      // Wait 2 updates
//      receiveN(2)
//      sa ! CancelMove
//      // One more update due to algo
//      val lastmsg = receiveN(1)
//      val end = expectMsgClass(classOf[AxisUpdate])
//      end.state should be(AXIS_IDLE)
//      end.current should be(650)
//
//      sa ! PoisonPill
//    }
//
//    it("should limit out-of-range moves") {
//      val sa = defaultAxis(testActor)
//
//      // Expect an initial axis status message
//      //val one = expectMsgClass(classOf[AxisUpdate])
//      //one.current should equal(defaultAxisConfig.startPosition)
//
//      // Position starts out at 350
//      sa ! Move(0)
//      val msgs = expectMoveMsgs(false)
//      msgs.last.state should be(AXIS_IDLE)
//      msgs.last.current should be(100)
//      msgs.last.inLowLimit should be(true)
//
//      sa.underlyingActor.current should be(defaultAxisConfig.lowLimit)
//      sa.underlyingActor.inLowLimit should be(true)
//      sa.underlyingActor.inHighLimit should be(false)
//
//      sa ! GetStatistics
//      val stats1: AxisStatistics = expectMsgClass(classOf[AxisStatistics])
//      stats1.initCount should be(0)
//      stats1.moveCount should be(1)
//      stats1.homeCount should be(0)
//      stats1.limitCount should be(1)
//      stats1.successCount should be(1)
//      stats1.failureCount should be(0)
//      stats1.cancelCount should be(0)
//
//      sa ! Move(2000)
//      val msgs2 = expectMoveMsgs(false)
//      msgs2.last.state should be(AXIS_IDLE)
//      msgs2.last.current should be(1300)
//      msgs2.last.inLowLimit should be(false)
//      msgs2.last.inHighLimit should be(true)
//
//      sa.underlyingActor.current should be(defaultAxisConfig.highLimit)
//      sa.underlyingActor.inLowLimit should be(false)
//      sa.underlyingActor.inHighLimit should be(true)
//
//      sa ! GetStatistics
//      val stats2 = expectMsgClass(classOf[AxisStatistics])
//      stats2.initCount should be(0)
//      stats2.moveCount should be(2)
//      stats2.homeCount should be(0)
//      stats2.limitCount should be(2)
//      stats2.successCount should be(2)
//      stats2.failureCount should be(0)
//      stats2.cancelCount should be(0)
//
//      sa ! PoisonPill
//    }
//
//    it("should support a complex example") {
//      val sa = defaultAxis(testActor)
//
//      // Expect an initial axis status message
//      //val one = expectMsgClass(classOf[AxisUpdate])
//      //one.current should equal(defaultAxisConfig.startPosition)
//
//      // Starts at 350, init (351), go home, go to 423, 800, 560, highlmit at 1240, then home
//      sa ! Datum
//      var msgs = expectMoveMsgs()
//      msgs.last.current should be(defaultAxisConfig.startPosition + 1)
//
//      sa ! Home
//      msgs = expectMoveMsgs()
//      msgs.last.current should be(defaultAxisConfig.home)
//
//      sa ! Move(423)
//      msgs = expectMoveMsgs()
//      msgs.last.current should be(423)
//
//      sa ! Move(800)
//      msgs = expectMoveMsgs()
//      msgs.last.current should be(800)
//
//      sa ! Move(560)
//      msgs = expectMoveMsgs()
//      msgs.last.current should be(560)
//
//      sa ! Move(1240)
//      msgs = expectMoveMsgs()
//      msgs.last.current should be(1240)
//
//      sa ! Home
//      msgs = expectMoveMsgs()
//      msgs.last.current should be(defaultAxisConfig.home)
//
//      sa ! GetStatistics
//      val stats2 = expectMsgClass(classOf[AxisStatistics])
//      stats2.initCount should be(1)
//      stats2.moveCount should be(7)
//      stats2.homeCount should be(2)
//      stats2.limitCount should be(1)
//      stats2.successCount should be(7)
//      stats2.failureCount should be(0)
//      stats2.cancelCount should be(0)
//
//      sa ! PoisonPill
//    }
//  }

}