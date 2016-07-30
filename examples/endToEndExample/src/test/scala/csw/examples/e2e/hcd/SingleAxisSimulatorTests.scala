package csw.examples.e2e.hcd

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import csw.examples.e2e.hcd.MotionWorker.{Cancel, End, Start}
import csw.examples.e2e.hcd.SingleAxisSimulator.AxisConfig
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, ShouldMatchers}

import scala.concurrent.duration._

/**
  * TMT Source Code: 7/19/16.
  */
class SingleAxisSimulatorTests extends TestKit(ActorSystem("TromboneHCDTests")) with ImplicitSender
  with FunSpecLike with ShouldMatchers with BeforeAndAfterAll {

  override def afterAll = TestKit.shutdownActorSystem(system)

  // Calculates the time to wait for messages with a little extra
  def calcDelay(numberSteps: Int, delayInSseconds: Int): FiniteDuration = (numberSteps + 1) * delayInSseconds * 1000.seconds

  describe("motion worker setup") {
    val testStart = 0
    val testDestination = 1000
    val testDelay = 100
    val testNumberSteps = 4

    it("should be initialized properly") {
      val props = MotionWorker.props(testStart, testDestination, testNumberSteps, testDelay, self, false)
      val ms = TestActorRef[MotionWorker](props)
      val under = ms.underlyingActor
      under.numSteps should equal(testNumberSteps)
      under.start should equal(testStart)
      under.destination should equal(testDestination)
      under.delayInNanoSeconds should equal(testDelay * 1000000)
    }
  }

  describe("motion worker forward") {
    val testStart = 0
    val testDestination = 1000
    val testDelay = 500
    val testNumberSteps = 4

    it("should allow simulation on increasing encoder steps") {
      val props = MotionWorker.props(testStart, testDestination, 4, testDelay, self, false)
      val ms = TestActorRef(props)
      ms ! Start

      expectMsg(Start)
      receiveN(testNumberSteps, calcDelay(testNumberSteps, testDelay))
      expectMsg(End(1000))
    }
  }


  describe("motion worker reverse") {
    val testStart = 1000
    val testDestination = 0
    val testDelay = 500
    val testNumberSteps = 5
    it("should allow creation based on negative encoder steps") {

      val props = MotionWorker.props(testStart, testDestination, testNumberSteps, testDelay, self, false)
      val ms = TestActorRef(props)
      ms ! Start
      expectMsg(Start)
      val msgs = receiveN(testNumberSteps, calcDelay(testNumberSteps, testDelay))
      msgs.size should be(testNumberSteps)
      expectMsg(End(0))
    }
  }

  describe("simulate continuous motion with motion worker") {
    val testStart = 500
    val testDestination = 600
    val testDelay = 50
    val testNumberSteps = 50
    it("should allow creation based on negative encoder steps") {

      val props = MotionWorker.props(testStart, testDestination, testNumberSteps, testDelay, self, false)
      val ms = TestActorRef(props)
      ms ! Start
      expectMsg(Start)
      val msgs = receiveN(testNumberSteps, calcDelay(testNumberSteps, testDelay))
      msgs.size should be(testNumberSteps)
      val end = expectMsgClass(classOf[End])
    }
  }

  describe("motion worker cancel") {
    val testStart = 0
    val testDestination = 1000
    val testDelay = 200
    val testNumberSteps = 10

    it("should allow cancelling after a few steps") {
      val props = MotionWorker.props(testStart, testDestination, testNumberSteps, testDelay, self, false)
      val ms = TestActorRef(props)
      ms ! Start
      expectMsg(Start)
      // Wait 3 messages
      receiveN(3, calcDelay(3, testDelay))
      ms ! Cancel
      // One more move
      val lastmsg = receiveN(1)
      val end = expectMsgClass(classOf[End])
    }
  }

  val defaultAxisName = "test"
  val defaultLowLimit = 100
  val defaultLowUser = 200
  val defaultHighUser = 1200
  val defaultHighLimit = 1300
  val defaultHome = 300
  val defaultStartPosition = 350
  val defaultStatusPrefix = "test.axisStatus"

  val defaultAxisConfig = AxisConfig(defaultAxisName,
    defaultLowLimit,
    defaultLowUser,
    defaultHighUser,
    defaultHighLimit,
    defaultHome,
    defaultStartPosition)

  def defaultAxis(replyTo: ActorRef): TestActorRef[SingleAxisSimulator] = {
    val props = SingleAxisSimulator.props(defaultAxisConfig, Some(replyTo))
    TestActorRef(props) // No name here since can't create actors with the same name
  }


  describe("test single axis") {
    import SingleAxisSimulator._

    it("should be creatable and initialize") {
      val sa = defaultAxis(testActor)
      sa.underlyingActor.axisConfig should equal(defaultAxisConfig)
      // Expect an initial axis status message
      // val one = expectMsgClass(1.second, classOf[AxisUpdate])
      //one.current should equal(defaultAxisConfig.startPosition)
    }

    it("limitMove should clamp values") {
      import SingleAxisSimulator.limitMove
      val ac = defaultAxisConfig

      // Acceptable
      limitMove(ac, 200) should equal(200)
      // Low limit
      limitMove(ac, 0) should equal(ac.lowLimit)

      // High limit
      limitMove(ac, 2000) should equal(ac.highLimit)

      // Check "limit" checks > or < user limits
      isHighLimit(ac, ac.home) should be(false)
      isHighLimit(ac, ac.highUser - 1) should be(false)
      isHighLimit(ac, ac.highUser) should be(true)
      isHighLimit(ac, ac.highLimit) should be(true)

      isLowLimit(ac, ac.home) should be(false)
      isLowLimit(ac, ac.lowUser + 1) should be(false)
      isLowLimit(ac, ac.lowUser) should be(true)
      isLowLimit(ac, ac.lowLimit) should be(true)

      isHomed(ac, ac.home)
    }

    it("Should init properly") {
      val sa = defaultAxis(testActor)

      // Expect an initial axis status message
      //val one = expectMsgClass(classOf[AxisUpdate])
      //one.current should equal(defaultAxisConfig.startPosition)

      sa ! Init
      expectMsg(AxisStarted)
      val upd = expectMsgClass(classOf[AxisUpdate])
      upd.state should equal(AXIS_IDLE)
      upd.current should equal(defaultAxisConfig.startPosition + 1)


      sa ! GetStatistics
      val stats1: AxisStatistics = expectMsgClass(classOf[AxisStatistics])
      stats1.initCount should be(1)
      stats1.moveCount should be(1)
      stats1.homeCount should be(0)
      stats1.limitCount should be(0)
      stats1.successCount should be(1)
      stats1.failureCount should be(0)
      stats1.cancelCount should be(0)

      sa ! PoisonPill
    }

    it("Should home properly") {
      val sa = defaultAxis(testActor)

      // Expect an initial axis status message
      //val one = expectMsgClass(classOf[AxisUpdate])
      //one.current should equal(defaultAxisConfig.startPosition)

      sa ! Home
      val msgs = expectMoveMsgs()
      msgs.last.state should be(AXIS_IDLE)
      msgs.last.inHomed should be(true)
      msgs.last.current should be(defaultAxisConfig.home)

      sa.underlyingActor.current should be(defaultAxisConfig.home)

      sa ! GetStatistics
      val stats1: AxisStatistics = expectMsgClass(classOf[AxisStatistics])
      stats1.initCount should be(0)
      stats1.moveCount should be(1)
      stats1.homeCount should be(1)
      stats1.limitCount should be(0)
      stats1.successCount should be(1)
      stats1.failureCount should be(0)
      stats1.cancelCount should be(0)

      sa ! PoisonPill
    }

    def expectMoveMsgs(diagFlag: Boolean = false): Seq[AxisUpdate] = {
      // Get AxisStarted
      expectMsg(AxisStarted)
      // Receive updates until axis idle then get the last one
      val msgs = receiveWhile(5.seconds) {
        case m@AxisUpdate(_, axisState, current, _, _, _) if axisState == AXIS_MOVING => m
      }
      val fmsg = expectMsgClass(classOf[AxisUpdate]) // last one
      val allmsgs = msgs :+ fmsg
      if (diagFlag) info(s"MoveMsgs: $allmsgs")
      allmsgs
    }

    it("Should move properly") {
      val sa = defaultAxis(testActor)

      // Expect an initial axis status message
      //val one = expectMsgClass(classOf[AxisUpdate])
      //one.current should equal(defaultAxisConfig.startPosition)

      sa ! Move(500, diagFlag = false)
      val msgs = expectMoveMsgs()
      msgs.last.state should be(AXIS_IDLE)
      msgs.last.current should be(500)

      sa.underlyingActor.current should be(500)

      sa ! PoisonPill
    }

    it("Should allow a cancel") {
      val sa = defaultAxis(testActor)

      // Expect an initial axis status message
      //val one = expectMsgClass(classOf[AxisUpdate])
      //one.current should equal(defaultAxisConfig.startPosition)

      sa ! Move(850, diagFlag = false)
      expectMsg(AxisStarted)
      // Wait 2 updates
      receiveN(2)
      sa ! CancelMove
      // One more update due to algo
      val lastmsg = receiveN(1)
      val end = expectMsgClass(classOf[AxisUpdate])
      end.state should be(AXIS_IDLE)
      end.current should be(650)

      sa ! PoisonPill
    }

    it("should limit out-of-range moves") {
      val sa = defaultAxis(testActor)

      // Expect an initial axis status message
      //val one = expectMsgClass(classOf[AxisUpdate])
      //one.current should equal(defaultAxisConfig.startPosition)

      // Position starts out at 350
      sa ! Move(0)
      val msgs = expectMoveMsgs(false)
      msgs.last.state should be(AXIS_IDLE)
      msgs.last.current should be(100)
      msgs.last.inLowLimit should be(true)

      sa.underlyingActor.current should be(defaultAxisConfig.lowLimit)
      sa.underlyingActor.inLowLimit should be(true)
      sa.underlyingActor.inHighLimit should be(false)

      sa ! GetStatistics
      val stats1: AxisStatistics = expectMsgClass(classOf[AxisStatistics])
      stats1.initCount should be(0)
      stats1.moveCount should be(1)
      stats1.homeCount should be(0)
      stats1.limitCount should be(1)
      stats1.successCount should be(1)
      stats1.failureCount should be(0)
      stats1.cancelCount should be(0)

      sa ! Move(2000)
      val msgs2 = expectMoveMsgs(false)
      msgs2.last.state should be(AXIS_IDLE)
      msgs2.last.current should be(1300)
      msgs2.last.inLowLimit should be(false)
      msgs2.last.inHighLimit should be(true)

      sa.underlyingActor.current should be(defaultAxisConfig.highLimit)
      sa.underlyingActor.inLowLimit should be(false)
      sa.underlyingActor.inHighLimit should be(true)

      sa ! GetStatistics
      val stats2 = expectMsgClass(classOf[AxisStatistics])
      stats2.initCount should be(0)
      stats2.moveCount should be(2)
      stats2.homeCount should be(0)
      stats2.limitCount should be(2)
      stats2.successCount should be(2)
      stats2.failureCount should be(0)
      stats2.cancelCount should be(0)

      sa ! PoisonPill
    }

    it("should support a complex example") {
      val sa = defaultAxis(testActor)

      // Expect an initial axis status message
      //val one = expectMsgClass(classOf[AxisUpdate])
      //one.current should equal(defaultAxisConfig.startPosition)

      // Starts at 350, init (351), go home, go to 423, 800, 560, highlmit at 1240, then home
      sa ! Init
      var msgs = expectMoveMsgs()
      msgs.last.current should be(defaultAxisConfig.startPosition + 1)

      sa ! Home
      msgs = expectMoveMsgs()
      msgs.last.current should be(defaultAxisConfig.home)

      sa ! Move(423)
      msgs = expectMoveMsgs()
      msgs.last.current should be(423)

      sa ! Move(800)
      msgs = expectMoveMsgs()
      msgs.last.current should be(800)

      sa ! Move(560)
      msgs = expectMoveMsgs()
      msgs.last.current should be(560)

      sa ! Move(1240)
      msgs = expectMoveMsgs()
      msgs.last.current should be(1240)

      sa ! Home
      msgs = expectMoveMsgs()
      msgs.last.current should be(defaultAxisConfig.home)

      sa ! GetStatistics
      val stats2 = expectMsgClass(classOf[AxisStatistics])
      stats2.initCount should be(1)
      stats2.moveCount should be(7)
      stats2.homeCount should be(2)
      stats2.limitCount should be(1)
      stats2.successCount should be(7)
      stats2.failureCount should be(0)
      stats2.cancelCount should be(0)

      sa ! PoisonPill
    }
  }
}