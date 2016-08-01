package csw.examples.vslice.hcd

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.ConnectionType.AkkaType
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
import csw.services.pkg.Supervisor3.{LifecycleInitialized, LifecycleRunning}
import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
import csw.services.pkg.{Supervisor, Supervisor3}
import csw.util.config.Configurations.SetupConfig
import csw.util.config.StateVariable.CurrentState
import org.scalatest.{BeforeAndAfterAll, _}

import scala.concurrent.Await
import scala.concurrent.duration.{FiniteDuration, _}

/**
  * TMT Source Code: 7/27/16.
  */
class TromboneHCDCompTests extends FunSpec with ShouldMatchers with LazyLogging with BeforeAndAfterAll {

  import TromboneHCD._
  import csw.services.ccs.HcdController._


  //override def afterAll = TestKit.shutdownActorSystem(system)

  implicit val system = ActorSystem("TestSystem")

  val testInfo = HcdInfo(TromboneHCD.componentName,
    TromboneHCD.trombonePrefix,
    TromboneHCD.componentClassName,
    DoNotRegister, Set(AkkaType), 1.second)

  val troboneAssemblyPrefix = "nfiraos.ncc.trombone"

  def startHCD: ActorRef = {
    val testInfo = HcdInfo(TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Set(AkkaType), 1.second)

    //val props = Supervisor3.props(testInfo, None)
    //system.actorOf(props)
    Supervisor3(testInfo)
  }

  def waitForMoveMsgs(tp: TestProbe): Seq[CurrentState] = {
    val msgs = tp.receiveWhile(5.seconds) {
      case m@CurrentState(ck, items) if ck.prefix.contains(TromboneHCD.axisStatePrefix) && m(TromboneHCD.stateKey).head == TromboneHCD.MOVING => m
      // This is present to pick up the first status message
      case st@CurrentState(ck, items) if ck.prefix.equals(TromboneHCD.axisStatsPrefix) => st
    }
    val fmsg = tp.expectMsgClass(classOf[CurrentState]) // last one
    val allmsgs = msgs :+ fmsg
    allmsgs
  }

  describe("external interface tests") {

    it("should allow fetching stats") {
      val hcd = startHCD

      val fakeAssembly = TestProbe()

      hcd ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      info("Running")

      fakeAssembly.send(hcd, Subscribe)

      fakeAssembly.send(hcd, AxisStats)

      val stats: CurrentState = fakeAssembly.expectMsgClass(classOf[CurrentState])
      println("AxisStats: " + stats)
      stats(initCountKey).head should be(0)
      stats(moveCountKey).head should be(0)
      stats(homeCountKey).head should be(0)
      stats(limitCountKey).head should be(0)
      stats(successCountKey).head should be(0)
      stats(failureCountKey).head should be(0)
      stats(cancelCountKey).head should be(0)

      hcd ! Unsubscribe
      /*
            val probe = TestProbe()
            probe watch hcd
            hcd ! PoisonPill
            probe.expectTerminated(hcd)
        */
      info("Done")

    }

    it("should accept an init") {
      val hcd = startHCD

      val fakeAssembly = TestProbe()

      fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      info("Running")
      // Currently can't subscribe unless in Running state because controllerReceive has process
      fakeAssembly.send(hcd, Subscribe)

      fakeAssembly.send(hcd, Submit(initSC))

      val msgs = waitForMoveMsgs(fakeAssembly)
      //msgs.last(positionKey).head should equal(tla.underlyingActor.axisConfig.startPosition + 1) // Init position is one off the start position
      info("Msgs: " + msgs)

      fakeAssembly.send(hcd, AxisStats)
      val stats = fakeAssembly.expectMsgClass(classOf[CurrentState])
      println("Stats: " + stats)
      stats.configKey should equal(TromboneHCD.axisStatsCK)
      stats.item(initCountKey).head should equal(1)
      stats.item(moveCountKey).head should equal(1)

      fakeAssembly.send(hcd, Unsubscribe)
      /*
            val probe = TestProbe()
            probe watch hcd
            hcd ! PoisonPill
            probe.expectTerminated(hcd)
            */
      info("Done")
    }

    it("should allow homing") {
      val hcd = startHCD

      val fakeAssembly = TestProbe()

      fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      info("Running")

      // Currently can't subscribe unless in Running state because controllerReceive has process
      fakeAssembly.send(hcd, Subscribe)

      // Being done this way to ensure ConfigKey equality works
      val sc = SetupConfig(axisHomePrefix)
      fakeAssembly.send(hcd, Submit(sc))

      val msgs = waitForMoveMsgs(fakeAssembly)
      info("Msgs: " + msgs)
      msgs.last(positionKey).head should equal(300)
      msgs.last(homedKey).head should equal(true)
      msgs.last(lowLimitKey).head should equal(false)
      msgs.last(highLimitKey).head should equal(false)

      fakeAssembly.send(hcd, AxisStats)
      val stats = fakeAssembly.expectMsgClass(classOf[CurrentState])
      info(s"Stats: $stats")
      stats.configKey should equal(TromboneHCD.axisStatsCK)
      stats.item(homeCountKey).head should equal(1)
      stats.item(moveCountKey).head should equal(1)

      fakeAssembly.send(hcd, Unsubscribe)
    }

  }

  it("should allow a short move") {
    val hcd = startHCD

    val fakeAssembly = TestProbe()

    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
    info("Running")

    fakeAssembly.send(hcd, Subscribe)
    // Being done this way to ensure ConfigKey equality works
    val testPos = 500

    fakeAssembly.send(hcd, Submit(positionSC.add(positionKey -> testPos)))

    val msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(positionKey).head should be(testPos)
    msgs.last(stateKey).head should be("AXIS_IDLE")
    info("Msgs: " + msgs)

    fakeAssembly.send(hcd, Unsubscribe)

  }

  it("should show entering a low limit") {
    val hcd = startHCD

    val fakeAssembly = TestProbe()

    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

    fakeAssembly.send(hcd, Subscribe)

    val testPos = 0
    fakeAssembly.send(hcd, Submit(positionSC.add(positionKey -> testPos)))
    val lowLimit = 100 // Note this will fail if axisConfig is changed

    val msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(stateKey).head should be("AXIS_IDLE")
    msgs.last(positionKey).head should be(lowLimit)
    msgs.last(lowLimitKey).head should equal(true)
    msgs.last(highLimitKey).head should equal(false)

    //info("Msgs: " + msgs)
    fakeAssembly.send(hcd, Unsubscribe)

  }

  it("should show entering a high limit") {
    val hcd = startHCD

    val fakeAssembly = TestProbe()

    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

    fakeAssembly.send(hcd, Subscribe)

    val testPos = 3000
    val highLimit = 1300 // Note this will fail if axisConfig is changed

    fakeAssembly.send(hcd, Submit(positionSC.add(positionKey -> testPos)))

    val msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(stateKey).head should be("AXIS_IDLE")
    msgs.last(positionKey).head should be(highLimit)
    msgs.last(lowLimitKey).head should equal(false)
    msgs.last(highLimitKey).head should equal(true)

    //info("Msgs: " + msgs)
    fakeAssembly.send(hcd, Unsubscribe)
  }

  it("should allow complex series of moves") {
    // Starts at 350, init (351), go home, go to 423, 800, 560, highlmit at 1240, then home
    val hcd = startHCD

    val fakeAssembly = TestProbe()

    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

    fakeAssembly.send(hcd, Subscribe)

    // Move 1
    fakeAssembly.send(hcd, Submit(SetupConfig(axisInitPrefix))) // Could use ones in TromboneHCD
    var msgs = waitForMoveMsgs(fakeAssembly)
    msgs.last(homedKey).head should be(false)

    // Move 2
    fakeAssembly.send(hcd, Submit(homeSC))
    msgs = waitForMoveMsgs(fakeAssembly)
    msgs.last(homedKey).head should be(true)

    // Move 3
    var testPos = 423
    fakeAssembly.send(hcd, Submit(positionSC.add(positionKey -> testPos)))
    msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(positionKey).head should be(testPos)
    msgs.last(stateKey).head should be("AXIS_IDLE")
    msgs.last(homedKey).head should be(false)
    msgs.last(lowLimitKey).head should be(false)
    msgs.last(highLimitKey).head should be(false)

    // Move 4
    testPos = 800
    fakeAssembly.send(hcd, Submit(positionSC.add(positionKey -> testPos)))
    msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(positionKey).head should be(testPos)
    msgs.last(stateKey).head should be("AXIS_IDLE")

    // Move 5
    testPos = 1240
    fakeAssembly.send(hcd, Submit(positionSC.add(positionKey -> testPos)))
    msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(positionKey).head should be(testPos)
    msgs.last(stateKey).head should be("AXIS_IDLE")
    msgs.last(lowLimitKey).head should be(false)
    msgs.last(highLimitKey).head should be(true)

    // Move 6
    fakeAssembly.send(hcd, Submit(homeSC))
    msgs = waitForMoveMsgs(fakeAssembly)
    msgs.last(homedKey).head should be(true)
    msgs.last(lowLimitKey).head should be(false)
    msgs.last(highLimitKey).head should be(false)

    // Get summary stats
    fakeAssembly.send(hcd, AxisStats)
    val stats = fakeAssembly.expectMsgClass(classOf[CurrentState])
    //println("Stats: " + stats)
    stats.configKey should equal(TromboneHCD.axisStatsCK)
    stats.item(initCountKey).head should equal(1)
    stats.item(moveCountKey).head should equal(6)
    stats.item(homeCountKey).head should equal(2)
    stats.item(limitCountKey).head should equal(1)
    stats.item(successCountKey).head should equal(6)
    stats.item(failureCountKey).head should be(0)
    stats.item(cancelCountKey).head should be(0)

    fakeAssembly.send(hcd, Unsubscribe)
  }

  it("start up a move and cancel it") {
    val hcd = startHCD

    val fakeAssembly = TestProbe()

    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

    fakeAssembly.send(hcd, Subscribe)

    val testPos = 1000

    fakeAssembly.send(hcd, Submit(positionSC.add(positionKey -> testPos)))

    // wait for 2 updates
    fakeAssembly.receiveN(2)
    fakeAssembly.send(hcd, Submit(cancelSC))
    val msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(stateKey).head should be("AXIS_IDLE")
    info("Msgs: " + msgs)

    // Get summary stats
    fakeAssembly.send(hcd, AxisStats)
    val stats = fakeAssembly.expectMsgClass(classOf[CurrentState])
    //println("Stats: " + stats)
    stats.configKey should equal(TromboneHCD.axisStatsCK)
    stats.item(moveCountKey).head should equal(1)
    stats.item(successCountKey).head should equal(1)
    stats.item(cancelCountKey).head should be(1)

    fakeAssembly.send(hcd, Unsubscribe)
  }

  it("should allow repetitive movces") {
    // Starts at 350, init (351), go home, small moves repeating */
    val hcd = startHCD

    val fakeAssembly = TestProbe()

    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

    fakeAssembly.send(hcd, Subscribe)

    // Init 1
    fakeAssembly.send(hcd, Submit(SetupConfig(axisInitPrefix))) // Could use ones in TromboneHCD
    var msgs = waitForMoveMsgs(fakeAssembly)
    msgs.last(stateKey).head should be(IDLE)

    // Move 2
    fakeAssembly.send(hcd, Submit(homeSC))
    msgs = waitForMoveMsgs(fakeAssembly)
    msgs.last(homedKey).head should be(true)

    val start = 300
    val finish = 500
    val stepSize = 10
    val loops = 2
    for (loops <- 1 to 2) {
      logger.info(s"Loop: $loops")
      for (testPos <- start to finish by stepSize) {
        fakeAssembly.send(hcd, Submit(positionSC.add(positionKey -> testPos)))
        msgs = waitForMoveMsgs(fakeAssembly)
      }
    }
  }


  def stopComponent(supervisorSystem: ActorSystem, supervisor: ActorRef, timeout: FiniteDuration) = {
    //system.scheduler.scheduleOnce(timeout) {
    println("STOPPING")
    Supervisor.haltComponent(supervisor)
    Await.ready(supervisorSystem.whenTerminated, 5.seconds)
    //system.terminate()
    System.exit(0)
    //}
  }

}
