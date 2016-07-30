package csw.examples.vslice.hcd

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import csw.services.loc.ConnectionType.AkkaType
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
import csw.services.pkg.Supervisor
import csw.util.config.Configurations.SetupConfig
import csw.util.config.StateVariable.CurrentState
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, ShouldMatchers}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * TMT Source Code: 7/18/16.
  */
class TromboneHCDBasicTests extends TestKit(ActorSystem("TromboneTests")) with ImplicitSender
  with FunSpecLike with ShouldMatchers with BeforeAndAfterAll {

  override def afterAll = TestKit.shutdownActorSystem(system)

  val troboneAssemblyPrefix = "nfiraos.ncc.trombone"

  val testInfo = HcdInfo(TromboneHCD.componentName,
    TromboneHCD.trombonePrefix,
    TromboneHCD.componentClassName,
    DoNotRegister, Set(AkkaType), 1.second)


  describe("axis should be initialized") {

    it("should initialize the trombone axis simulator") {
      val tla = TestActorRef[TromboneHCD](Props(new TromboneHCD(testInfo)))
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
  }

  def waitForMoveMsgs: Seq[CurrentState] = {
    val msgs = receiveWhile(5.seconds) {
      case m@CurrentState(ck, items) if ck.prefix.contains(TromboneHCD.axisStatePrefix) && m(TromboneHCD.stateKey).head == TromboneHCD.MOVING => m
      // This is present to pick up the first status message
      case st@CurrentState(ck, items) if ck.prefix.equals(TromboneHCD.axisStatsPrefix) => st
    }
    val fmsg = expectMsgClass(classOf[CurrentState]) // last one
    val allmsgs = msgs :+ fmsg
    allmsgs
  }

  def waitForAllMsgs: Seq[CurrentState] = {
    val msgs = receiveWhile(5.seconds) {
      case m@CurrentState(ck, items) if ck.prefix.contains(TromboneHCD.axisStatePrefix) => m
      // This is present to pick up the first status message
      case st@CurrentState(ck, items) if ck.prefix.equals(TromboneHCD.axisStatsPrefix) => st
    }
    val fmsg = expectMsgClass(classOf[CurrentState]) // last one
    val allmsgs = msgs :+ fmsg
    allmsgs
  }

  describe("Should be ready for running with stats") {
    import TromboneHCD._
    import csw.services.ccs.HcdController._

    it("should allow fetching stats") {

      val tla = TestActorRef[TromboneHCD](Props(new TromboneHCD(testInfo)))

      tla ! Subscribe
      tla ! AxisStats

      val stats = expectMsgClass(classOf[CurrentState])
      //println("AxisStats: " + stats)
      stats(initCountKey).head should be(0)
      stats(moveCountKey).head should be(0)
      stats(homeCountKey).head should be(0)
      stats(limitCountKey).head should be(0)
      stats(successCountKey).head should be(0)
      stats(failureCountKey).head should be(0)
      stats(cancelCountKey).head should be(0)

      tla ! Unsubscribe

      tla.underlyingActor.context.stop(tla)
    }
  }

  def createTromboneHCD(hcdInfo: HcdInfo): ActorRef = {
    val props = TromboneHCD.props(hcdInfo)
    system.actorOf(props)
  }

  def createTestTromboneHCD(hcdInfo: HcdInfo): TestActorRef[TromboneHCD] = {
    val props = TromboneHCD.props(hcdInfo)
    TestActorRef(props)
  }

  describe("Should work running with init") {
    import TromboneHCD._
    import csw.services.ccs.HcdController._

    it("should allow init") {

      val tla = createTestTromboneHCD(testInfo)

      tla ! Subscribe
      tla ! Submit(initSC)

      val msgs = waitForMoveMsgs
      msgs.last(positionKey).head should equal(tla.underlyingActor.axisConfig.startPosition + 1) // Init position is one off the start position
      //info("Msgs: " + msgs)

      tla ! AxisStats
      val stats = expectMsgClass(classOf[CurrentState])
      //println("Stats: " + stats)
      stats.configKey should equal(TromboneHCD.axisStatsCK)
      stats.item(initCountKey).head should equal(1)
      stats.item(moveCountKey).head should equal(1)

      tla ! Unsubscribe
      system.stop(tla)
    }
  }

  describe("Should be ready for running with home") {
    import TromboneHCD._
    import csw.services.ccs.HcdController._

    it("should allow homing") {

      // Note there is no test actor ref
      val tla = createTromboneHCD(testInfo)

      tla ! Subscribe
      // Being done this way to ensure ConfigKey equality works
      val sc = SetupConfig(axisHomePrefix)
      tla ! Submit(sc)

      val msgs = waitForMoveMsgs
      //info("Msgs: " + msgs)
      msgs.last(positionKey).head should equal(300)
      msgs.last(homedKey).head should equal(true)
      msgs.last(lowLimitKey).head should equal(false)
      msgs.last(highLimitKey).head should equal(false)

      tla ! AxisStats
      val stats = expectMsgClass(classOf[CurrentState])
      //info(s"Stats: $stats")
      stats.configKey should equal(TromboneHCD.axisStatsCK)
      stats.item(homeCountKey).head should equal(1)
      stats.item(moveCountKey).head should equal(1)

      tla ! Unsubscribe

      system.stop(tla)
    }
  }

  describe("Should be ready for running with move") {
    import TromboneHCD._
    import csw.services.ccs.HcdController._

    it("should allow short move") {

      val tla = createTromboneHCD(testInfo)
      val testPos = 500

      tla ! Subscribe
      tla ! Submit(positionSC.add(positionKey -> testPos))

      val msgs = waitForMoveMsgs
      // Check the last message
      msgs.last(positionKey).head should be(testPos)
      msgs.last(stateKey).head should be("AXIS_IDLE")

      //info("Msgs: " + msgs)
      tla ! Unsubscribe

      system.stop(tla)
    }
  }

  describe("place into the low limit") {
    import TromboneHCD._
    import csw.services.ccs.HcdController._

    it("should show entering a low limit") {

      val tla = createTestTromboneHCD(testInfo)
      val testPos = 0
      val testActual = tla.underlyingActor.axisConfig.lowLimit

      tla ! Subscribe
      tla ! Submit(positionSC.add(positionKey -> testPos))

      val msgs = waitForMoveMsgs
      // Check the last message
      msgs.last(stateKey).head should be("AXIS_IDLE")
      msgs.last(positionKey).head should be(testActual)
      msgs.last(lowLimitKey).head should equal(true)
      msgs.last(highLimitKey).head should equal(false)

      //info("Msgs: " + msgs)
      tla ! Unsubscribe

      system.stop(tla)
    }
  }

  describe("place into the high limit") {
    import TromboneHCD._
    import csw.services.ccs.HcdController._

    it("should show entering a high limit") {

      val tla = createTestTromboneHCD(testInfo)
      val testPos = 3000
      val testActual = tla.underlyingActor.axisConfig.highLimit

      tla ! Subscribe
      tla ! Submit(positionSC.add(positionKey -> testPos))

      val msgs = waitForMoveMsgs
      // Check the last message
      msgs.last(stateKey).head should be("AXIS_IDLE")
      msgs.last(positionKey).head should be(testActual)
      msgs.last(lowLimitKey).head should equal(false)
      msgs.last(highLimitKey).head should equal(true)

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

      val tla = createTromboneHCD(testInfo)
      // Get state events
      tla ! Subscribe

      // Move 1
      tla ! Submit(SetupConfig(axisInitPrefix)) // Could use ones in TromboneHCD
      var msgs = waitForMoveMsgs
      msgs.last(homedKey).head should be(false)

      // Move 2
      tla ! Submit(homeSC)
      msgs = waitForMoveMsgs
      msgs.last(homedKey).head should be(true)

      // Move 3
      var testPos = 423
      tla ! Submit(positionSC.add(positionKey -> testPos))
      msgs = waitForMoveMsgs
      // Check the last message
      msgs.last(positionKey).head should be(testPos)
      msgs.last(stateKey).head should be("AXIS_IDLE")
      msgs.last(homedKey).head should be(false)
      msgs.last(lowLimitKey).head should be(false)
      msgs.last(highLimitKey).head should be(false)

      // Move 4
      testPos = 800
      tla ! Submit(positionSC.add(positionKey -> testPos))
      msgs = waitForMoveMsgs
      // Check the last message
      msgs.last(positionKey).head should be(testPos)
      msgs.last(stateKey).head should be("AXIS_IDLE")

      // Move 5
      testPos = 1240
      tla ! Submit(positionSC.add(positionKey -> testPos))
      msgs = waitForMoveMsgs
      // Check the last message
      msgs.last(positionKey).head should be(testPos)
      msgs.last(stateKey).head should be("AXIS_IDLE")
      msgs.last(lowLimitKey).head should be(false)
      msgs.last(highLimitKey).head should be(true)

      // Move 6
      tla ! Submit(homeSC)
      msgs = waitForMoveMsgs
      msgs.last(homedKey).head should be(true)
      msgs.last(lowLimitKey).head should be(false)
      msgs.last(highLimitKey).head should be(false)

      // Get summary stats
      tla ! AxisStats
      val stats = expectMsgClass(classOf[CurrentState])
      //println("Stats: " + stats)
      stats.configKey should equal(TromboneHCD.axisStatsCK)
      stats.item(initCountKey).head should equal(1)
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

      val tla = createTromboneHCD(testInfo)
      val testPos = 1000

      tla ! Subscribe
      tla ! Submit(positionSC.add(positionKey -> testPos))

      // wait for 2 updates
      receiveN(2)
      tla ! Submit(cancelSC)
      val msgs = waitForMoveMsgs
      // Check the last message
      msgs.last(stateKey).head should be("AXIS_IDLE")
      info("Msgs: " + msgs)

      // Get summary stats
      tla ! AxisStats
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


  def startHCD: ActorRef = {
    val testInfo = HcdInfo(TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Set(AkkaType), 1.second)
    Supervisor(testInfo)
  }


  def stopComponent(supervisorSystem: ActorSystem, supervisor: ActorRef, timeout: FiniteDuration) = {
    //system.scheduler.scheduleOnce(timeout) {
    println("STOPPING")
    Supervisor.haltComponent(supervisor)
    Await.ready(supervisorSystem.whenTerminated, 5.seconds)
    system.terminate()
    System.exit(0)
    //}
  }

}
