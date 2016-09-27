package csw.examples.vslice.hcd

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import csw.services.loc.ConnectionType.AkkaType
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
import csw.services.pkg.Supervisor3._
import csw.util.config.Configurations.SetupConfig
import csw.util.config.StateVariable.CurrentState
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, FunSpecLike, ShouldMatchers}

import scala.concurrent.duration._

/**
 * TMT Source Code: 7/18/16.
 */
@DoNotDiscover
class TromboneHCDBasicTests extends TestKit(ActorSystem("TromboneTests")) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll {

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  val troboneAssemblyPrefix = "nfiraos.ncc.trombone"

  val testInfo = HcdInfo(
    TromboneHCD.componentName,
    TromboneHCD.trombonePrefix,
    TromboneHCD.componentClassName,
    DoNotRegister, Set(AkkaType), 1.second
  )

  def getTromboneProps(hcdInfo: HcdInfo, supervisorIn: Option[ActorRef]): Props = {
    supervisorIn match {
      case None           => TromboneHCD.props(hcdInfo, TestProbe().ref)
      case Some(actorRef) => TromboneHCD.props(hcdInfo, actorRef)
    }
  }

  def newTrombone(hcdInfo: HcdInfo = testInfo): (TestProbe, ActorRef) = {
    val supervisor = TestProbe()
    val props = getTromboneProps(hcdInfo, Some(supervisor.ref))
    (supervisor, system.actorOf(props))
  }

  def newTestTrombone(hcdInfo: HcdInfo = testInfo): (TestProbe, TestActorRef[TromboneHCD]) = {
    val supervisor = TestProbe()
    val props = getTromboneProps(hcdInfo, Some(supervisor.ref))
    (supervisor, TestActorRef(props))
  }

  def lifecycleStart(supervisor: TestProbe, tla: ActorRef): Unit = {
    supervisor.expectMsg(Initialized)
    supervisor.expectMsg(Started)

    supervisor.send(tla, Running)
  }

  def waitForMoveMsgs: Seq[CurrentState] = {
    val msgs = receiveWhile(5.seconds) {
      case m @ CurrentState(ck, _) if ck.prefix.contains(TromboneHCD.axisStatePrefix) && m(TromboneHCD.stateKey).head == TromboneHCD.AXIS_MOVING => m
      // This is present to pick up the first status message
      case st @ CurrentState(ck, _) if ck.prefix.equals(TromboneHCD.axisStatsPrefix) => st
    }
    val fmsg = expectMsgClass(classOf[CurrentState]) // last one
    val allmsgs = msgs :+ fmsg
    allmsgs
  }

  def waitForAllMsgs: Seq[CurrentState] = {
    val msgs = receiveWhile(5.seconds) {
      case m @ CurrentState(ck, _) if ck.prefix.contains(TromboneHCD.axisStatePrefix) => m
      // This is present to pick up the first status message
      case st @ CurrentState(ck, _) if ck.prefix.equals(TromboneHCD.axisStatsPrefix)  => st
    }
    val fmsg = expectMsgClass(classOf[CurrentState]) // last one
    val allmsgs = msgs :+ fmsg
    allmsgs
  }

  describe("low-level instrumented trombone HCD tests") {
    import TromboneHCD._
    import csw.services.ccs.HcdController._

    it("should initialize the trombone axis simulator") {

      val (_, tla) = newTestTrombone()
      val ua = tla.underlyingActor

      ua.tromboneAxis should not be null

      // Should have initialized the current values in HCD from Axis
      ua.current.current should be(ua.axisConfig.startPosition)
      ua.current.state should be(SingleAxisSimulator.AXIS_IDLE) // This is simulator value
      ua.current.inHighLimit should be(false)
      ua.current.inLowLimit should be(false)
      ua.current.inHomed should be(false)

      // Should initialize the statistics
      ua.stats.limitCount should be(0)
      ua.stats.cancelCount should be(0)
      ua.stats.failureCount should be(0)
      ua.stats.homeCount should be(0)
      ua.stats.initCount should be(0)
      ua.stats.moveCount should be(0)
      ua.stats.successCount should be(0)
    }

    it("should lifecycle properly with a fake supervisor") {
      val (supervisor, tla) = newTestTrombone()

      supervisor.expectMsg(Initialized)
      supervisor.expectMsg(Started)

      supervisor.send(tla, Running)

      supervisor.send(tla, DoShutdown)
      supervisor.expectMsg(ShutdownComplete)

    }

    it("should allow fetching config") {

      val (supervisor, tla) = newTestTrombone()
      lifecycleStart(supervisor, tla)

      tla ! Subscribe
      tla ! GetAxisConfig

      val config = expectMsgClass(classOf[CurrentState])
      //println("AxisStats: " + config)
      config(axisNameKey).head equals tla.underlyingActor.axisConfig.axisName
      config(lowLimitKey).head should be(tla.underlyingActor.axisConfig.lowLimit)
      config(lowUserKey).head should be(tla.underlyingActor.axisConfig.lowUser)
      config(highUserKey).head should be(tla.underlyingActor.axisConfig.highUser)
      config(highLimitKey).head should be(tla.underlyingActor.axisConfig.highLimit)
      config(homeValueKey).head should be(tla.underlyingActor.axisConfig.home)
      config(startValueKey).head should be(tla.underlyingActor.axisConfig.startPosition)
      config(stepDelayMSKey).head should be(tla.underlyingActor.axisConfig.stepDelayMS)

      tla ! Unsubscribe

      tla.underlyingActor.context.stop(tla)
    }

    it("should allow fetching stats") {

      val (supervisor, tla) = newTestTrombone()
      lifecycleStart(supervisor, tla)

      tla ! Subscribe
      tla ! GetAxisStats

      val stats = expectMsgClass(classOf[CurrentState])
      //println("AxisStats: " + stats)
      stats(datumCountKey).head should be(0)
      stats(moveCountKey).head should be(0)
      stats(homeCountKey).head should be(0)
      stats(limitCountKey).head should be(0)
      stats(successCountKey).head should be(0)
      stats(failureCountKey).head should be(0)
      stats(cancelCountKey).head should be(0)

      tla ! Unsubscribe

      tla.underlyingActor.context.stop(tla)
    }

    it("should allow external init when running") {

      val (supervisor, tla) = newTestTrombone()
      lifecycleStart(supervisor, tla)

      tla ! Subscribe
      tla ! Submit(datumSC)

      val msgs = waitForMoveMsgs
      msgs.last(positionKey).head should equal(tla.underlyingActor.axisConfig.startPosition + 1) // Init position is one off the start position
      //info("Msgs: " + msgs)

      tla ! GetAxisStats
      val stats = expectMsgClass(classOf[CurrentState])
      //println("Stats: " + stats)
      stats.configKey should equal(TromboneHCD.axisStatsCK)
      stats.item(datumCountKey).head should equal(1)
      stats.item(moveCountKey).head should equal(1)

      tla ! Unsubscribe
      system.stop(tla)
    }

    it("should allow homing") {

      // Note there is no test actor ref
      val (supervisor, tla) = newTrombone()
      lifecycleStart(supervisor, tla)

      tla ! Subscribe
      // Being done this way to ensure ConfigKey equality works
      val sc = SetupConfig(axisHomePrefix)
      tla ! Submit(sc)

      val msgs = waitForMoveMsgs
      //info("Msgs: " + msgs)
      msgs.last(positionKey).head should equal(300)
      msgs.last(inHomeKey).head should equal(true)
      msgs.last(inLowLimitKey).head should equal(false)
      msgs.last(inHighLimitKey).head should equal(false)

      tla ! GetAxisStats
      val stats = expectMsgClass(classOf[CurrentState])
      //info(s"Stats: $stats")
      stats.configKey should equal(TromboneHCD.axisStatsCK)
      stats.item(homeCountKey).head should equal(1)
      stats.item(moveCountKey).head should equal(1)

      tla ! Unsubscribe

      system.stop(tla)
    }

    it("should allow a short move") {

      val (supervisor, tla) = newTrombone()
      lifecycleStart(supervisor, tla)

      val testPos = 500

      tla ! Subscribe
      tla ! Submit(positionSC(testPos))

      val msgs = waitForMoveMsgs
      // Check the last message
      msgs.last(positionKey).head should be(testPos)
      msgs.last(stateKey).head should be(AXIS_IDLE)

      //info("Msgs: " + msgs)
      tla ! Unsubscribe

      system.stop(tla)
    }

    it("should allow continuous short values") {

      val encoderTestValues: Vector[Int] = Vector(
        460, 465, 470, 475, 480, 485, 490, 400
      )

      val (supervisor, tla) = newTrombone()
      lifecycleStart(supervisor, tla)

      tla ! Subscribe
      // Move 2
      tla ! Submit(homeSC)
      val msgs = waitForMoveMsgs
      msgs.last(inHomeKey).head should be(true)

      encoderTestValues.foreach { testPos =>
        tla ! Submit(positionSC(testPos))
        //val msgs = waitForMoveMsgs
      }
      waitForMoveMsgs

    }

    describe("place into the low limit") {
      import TromboneHCD._
      import csw.services.ccs.HcdController._

      it("should show entering a low limit") {

        val (supervisor, tla) = newTestTrombone()
        lifecycleStart(supervisor, tla)

        val testPos = 0
        val testActual = tla.underlyingActor.axisConfig.lowLimit

        tla ! Subscribe
        tla ! Submit(positionSC(testPos))

        val msgs = waitForMoveMsgs
        // Check the last message
        msgs.last(stateKey).head should be(AXIS_IDLE)
        msgs.last(positionKey).head should be(testActual)
        msgs.last(inLowLimitKey).head should equal(true)
        msgs.last(inHighLimitKey).head should equal(false)

        //info("Msgs: " + msgs)
        tla ! Unsubscribe

        system.stop(tla)
      }
    }

    describe("place into the high limit") {
      import TromboneHCD._
      import csw.services.ccs.HcdController._

      it("should show entering a high limit") {

        val (supervisor, tla) = newTestTrombone()
        lifecycleStart(supervisor, tla)

        val testPos = 3000
        val testActual = tla.underlyingActor.axisConfig.highLimit

        tla ! Subscribe
        tla ! Submit(positionSC(testPos))

        val msgs = waitForMoveMsgs
        // Check the last message
        msgs.last(stateKey).head should be(AXIS_IDLE)
        msgs.last(positionKey).head should be(testActual)
        msgs.last(inLowLimitKey).head should equal(false)
        msgs.last(inHighLimitKey).head should equal(true)

        //info("Msgs: " + msgs)
        tla ! Unsubscribe

        system.stop(tla)
      }
    }

    describe("Should support a more complex series of moves") {
      import TromboneHCD._
      import csw.services.ccs.HcdController._

      it("should allow complex series of moves") {
        // Starts at 350, init (351), go home, go to 423, 800, 560, highlmit at 1240, then home

        val (supervisor, tla) = newTrombone()
        lifecycleStart(supervisor, tla)

        // Get state events
        tla ! Subscribe

        // Move 1
        tla ! Submit(SetupConfig(axisDatumPrefix)) // Could use ones in TromboneHCD
        var msgs = waitForMoveMsgs
        msgs.last(inHomeKey).head should be(false)

        // Move 2
        tla ! Submit(homeSC)
        msgs = waitForMoveMsgs
        msgs.last(inHomeKey).head should be(true)

        // Move 3
        var testPos = 423
        tla ! Submit(positionSC(testPos))
        msgs = waitForMoveMsgs
        // Check the last message
        msgs.last(positionKey).head should be(testPos)
        msgs.last(stateKey).head should be(AXIS_IDLE)
        msgs.last(inHomeKey).head should be(false)
        msgs.last(inLowLimitKey).head should be(false)
        msgs.last(inHighLimitKey).head should be(false)

        // Move 4
        testPos = 800
        tla ! Submit(positionSC(testPos))
        msgs = waitForMoveMsgs
        // Check the last message
        msgs.last(positionKey).head should be(testPos)
        msgs.last(stateKey).head should be(AXIS_IDLE)

        // Move 5
        testPos = 1240
        tla ! Submit(positionSC(testPos))
        msgs = waitForMoveMsgs
        // Check the last message
        msgs.last(positionKey).head should be(testPos)
        msgs.last(stateKey).head should be(AXIS_IDLE)
        msgs.last(inLowLimitKey).head should be(false)
        msgs.last(inHighLimitKey).head should be(true)

        // Move 6
        tla ! Submit(homeSC)
        msgs = waitForMoveMsgs
        msgs.last(inHomeKey).head should be(true)
        msgs.last(inLowLimitKey).head should be(false)
        msgs.last(inHighLimitKey).head should be(false)

        // Get summary stats
        tla ! GetAxisStats
        val stats = expectMsgClass(classOf[CurrentState])
        //println("Stats: " + stats)
        stats.configKey should equal(TromboneHCD.axisStatsCK)
        stats.item(datumCountKey).head should equal(1)
        stats.item(moveCountKey).head should equal(6)
        stats.item(homeCountKey).head should equal(2)
        stats.item(limitCountKey).head should equal(1)
        stats.item(successCountKey).head should equal(6)
        stats.item(failureCountKey).head should be(0)
        stats.item(cancelCountKey).head should be(0)

        tla ! Unsubscribe

        system.stop(tla)
      }
    }

    describe("Should handle a cancel of a motion") {
      import TromboneHCD._
      import csw.services.ccs.HcdController._

      it("start up a move and cancel it") {

        val (supervisor, tla) = newTrombone()
        lifecycleStart(supervisor, tla)

        val testPos = 1000

        tla ! Subscribe
        tla ! Submit(positionSC(testPos))

        // wait for 2 updates
        receiveN(2)
        tla ! Submit(cancelSC)
        val msgs = waitForMoveMsgs
        // Check the last message
        msgs.last(stateKey).head should be(AXIS_IDLE)
        info("Msgs: " + msgs)

        // Get summary stats
        tla ! GetAxisStats
        val stats = expectMsgClass(classOf[CurrentState])
        //println("Stats: " + stats)
        stats.configKey should equal(TromboneHCD.axisStatsCK)
        stats.item(moveCountKey).head should equal(1)
        stats.item(successCountKey).head should equal(1)
        stats.item(cancelCountKey).head should be(1)

        tla ! Unsubscribe

        system.stop(tla)
      }
    }

    //      def startHCD: ActorRef = {
    //        val testInfo = HcdInfo(TromboneHCD.componentName,
    //          TromboneHCD.trombonePrefix,
    //          TromboneHCD.componentClassName,
    //          DoNotRegister, Set(AkkaType), 1.second)
    //        Supervisor3(testInfo)
    //      }

    //    def stopComponent(supervisorSystem: ActorSystem, supervisor: ActorRef, timeout: FiniteDuration) = {
    //      //system.scheduler.scheduleOnce(timeout) {
    //      println("STOPPING")
    //      Supervisor3.haltComponent(supervisor)
    //      Await.ready(supervisorSystem.whenTerminated, 5.seconds)
    //      system.terminate()
    //      System.exit(0)
    //      //}
    //    }

  }
}
