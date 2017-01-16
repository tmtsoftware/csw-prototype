package csw.examples.vslice.hcd

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.TestEnv
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.LocationService
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
import csw.services.pkg.Supervisor.{HaltComponent, LifecycleInitialized, LifecycleRunning}
import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
import csw.services.pkg.Supervisor
import csw.util.config.Configurations.SetupConfig
import csw.util.config.StateVariable.CurrentState
import org.scalatest.{BeforeAndAfterAll, _}

import scala.concurrent.Await
import scala.concurrent.duration.{FiniteDuration, _}

/**
 * TMT Source Code: 7/27/16.
 */
object TromboneHCDCompTests {
  LocationService.initInterface()
  implicit val system = ActorSystem("TromboneHCDCompTests")
}

class TromboneHCDCompTests extends FunSpec with ShouldMatchers with LazyLogging with BeforeAndAfterAll {

  import TromboneHCDCompTests._
  import TromboneHCD._
  import csw.services.ccs.HcdController._

  override def beforeAll: Unit = {
    TestEnv.createTromboneHcdConfig()
  }

  override def afterAll: Unit = system.terminate()

  val testInfo = HcdInfo(
    TromboneHCD.componentName,
    TromboneHCD.trombonePrefix,
    TromboneHCD.componentClassName,
    DoNotRegister, Set(AkkaType), 1.second
  )

  // val troboneAssemblyPrefix: String = TromboneAssembly.componentPrefix

  def startHCD: ActorRef = {
    val testInfo = HcdInfo(
      TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Set(AkkaType), 1.second
    )

    Supervisor(testInfo)
  }

  // Stop any actors created for a test to avoid conflict with other tests
  private def cleanup(component: ActorRef): Unit = {
    val monitor = TestProbe()
    monitor.watch(component)
    component ! HaltComponent
    monitor.expectTerminated(component)
  }

  def waitForMoveMsgs(tp: TestProbe): Seq[CurrentState] = {
    val msgs = tp.receiveWhile(5.seconds) {
      case m @ CurrentState(ck, _) if ck.prefix.contains(TromboneHCD.axisStatePrefix) && m(TromboneHCD.stateKey).head == TromboneHCD.AXIS_MOVING => m
      // This is present to pick up the first status message
      case st @ CurrentState(ck, _) if ck.prefix.equals(TromboneHCD.axisStatsPrefix) => st
    }
    val fmsg = tp.expectMsgClass(classOf[CurrentState]) // last one
    val allmsgs = msgs :+ fmsg
    allmsgs
  }

  describe("component level external public interface tests") {

    it("should allow fetching stats") {
      val hcd = startHCD

      val fakeAssembly = TestProbe()

      hcd ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      info("Running")

      fakeAssembly.send(hcd, Subscribe)

      fakeAssembly.send(hcd, GetAxisStats)

      val stats: CurrentState = fakeAssembly.expectMsgClass(classOf[CurrentState])
      println("AxisStats: " + stats)
      stats(datumCountKey).head should be(0)
      stats(moveCountKey).head should be(0)
      stats(homeCountKey).head should be(0)
      stats(limitCountKey).head should be(0)
      stats(successCountKey).head should be(0)
      stats(failureCountKey).head should be(0)
      stats(cancelCountKey).head should be(0)

      hcd ! Unsubscribe

      cleanup(hcd)
      info("Done")
    }

    it("should allow fetching config") {
      val hcd = startHCD

      val fakeAssembly = TestProbe()

      hcd ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      info("Running")

      fakeAssembly.send(hcd, Subscribe)
      fakeAssembly.send(hcd, GetAxisConfig)

      // The values are hard-coded because we can't look at the config inside the actor, will fail if config changes
      val config: CurrentState = fakeAssembly.expectMsgClass(classOf[CurrentState])
      println("AxisConfig: " + config)
      config(axisNameKey).head equals TromboneHCD.tromboneAxisName
      config(lowLimitKey).head should be(100)
      config(lowUserKey).head should be(200)
      config(highUserKey).head should be(1200)
      config(highLimitKey).head should be(1300)
      config(homeValueKey).head should be(300)
      config(startValueKey).head should be(350)

      fakeAssembly.send(hcd, Unsubscribe)
      cleanup(hcd)
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

      fakeAssembly.send(hcd, Submit(datumSC))

      val msgs = waitForMoveMsgs(fakeAssembly)
      //msgs.last(positionKey).head should equal(tla.underlyingActor.axisConfig.startPosition + 1) // Init position is one off the start position
      info("Msgs: " + msgs)

      fakeAssembly.send(hcd, GetAxisStats)
      val stats = fakeAssembly.expectMsgClass(classOf[CurrentState])
      println("Stats: " + stats)
      stats.configKey should equal(TromboneHCD.axisStatsCK)
      stats.item(datumCountKey).head should equal(1)
      stats.item(moveCountKey).head should equal(1)

      fakeAssembly.send(hcd, Unsubscribe)
      cleanup(hcd)
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
      msgs.last(inHomeKey).head should equal(true)
      msgs.last(inLowLimitKey).head should equal(false)
      msgs.last(inHighLimitKey).head should equal(false)

      fakeAssembly.send(hcd, GetAxisStats)
      val stats = fakeAssembly.expectMsgClass(classOf[CurrentState])
      info(s"Stats: $stats")
      stats.configKey should equal(TromboneHCD.axisStatsCK)
      stats.item(homeCountKey).head should equal(1)
      stats.item(moveCountKey).head should equal(1)

      fakeAssembly.send(hcd, Unsubscribe)
      cleanup(hcd)
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

    fakeAssembly.send(hcd, Submit(positionSC(testPos)))

    val msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(positionKey).head should be(testPos)
    msgs.last(stateKey).head should be(AXIS_IDLE)

    fakeAssembly.send(hcd, Unsubscribe)
    cleanup(hcd)
  }

  it("should show entering a low limit") {
    val hcd = startHCD

    val fakeAssembly = TestProbe()

    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

    fakeAssembly.send(hcd, Subscribe)

    val testPos = 0
    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
    val lowLimit = 100 // Note this will fail if axisConfig is changed

    val msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(stateKey).head should be(AXIS_IDLE)
    msgs.last(positionKey).head should be(lowLimit)
    msgs.last(inLowLimitKey).head should equal(true)
    msgs.last(inHighLimitKey).head should equal(false)

    //info("Msgs: " + msgs)
    fakeAssembly.send(hcd, Unsubscribe)
    cleanup(hcd)
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

    fakeAssembly.send(hcd, Submit(positionSC(testPos)))

    val msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(stateKey).head should be(AXIS_IDLE)
    msgs.last(positionKey).head should be(highLimit)
    msgs.last(inLowLimitKey).head should equal(false)
    msgs.last(inHighLimitKey).head should equal(true)

    //info("Msgs: " + msgs)
    fakeAssembly.send(hcd, Unsubscribe)
    cleanup(hcd)
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
    fakeAssembly.send(hcd, Submit(SetupConfig(axisDatumPrefix))) // Could use ones in TromboneHCD
    var msgs = waitForMoveMsgs(fakeAssembly)
    msgs.last(inHomeKey).head should be(false)

    // Move 2
    fakeAssembly.send(hcd, Submit(homeSC))
    msgs = waitForMoveMsgs(fakeAssembly)
    msgs.last(inHomeKey).head should be(true)

    // Move 3
    var testPos = 423
    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
    msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(positionKey).head should be(testPos)
    msgs.last(stateKey).head should be(AXIS_IDLE)
    msgs.last(inHomeKey).head should be(false)
    msgs.last(inLowLimitKey).head should be(false)
    msgs.last(inHighLimitKey).head should be(false)

    // Move 4
    testPos = 800
    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
    msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(positionKey).head should be(testPos)
    msgs.last(stateKey).head should be(AXIS_IDLE)

    // Move 5
    testPos = 1240
    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
    msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(positionKey).head should be(testPos)
    msgs.last(stateKey).head should be(AXIS_IDLE)
    msgs.last(inLowLimitKey).head should be(false)
    msgs.last(inHighLimitKey).head should be(true)

    // Move 6
    fakeAssembly.send(hcd, Submit(homeSC))
    msgs = waitForMoveMsgs(fakeAssembly)
    msgs.last(inHomeKey).head should be(true)
    msgs.last(inLowLimitKey).head should be(false)
    msgs.last(inHighLimitKey).head should be(false)

    // Get summary stats
    fakeAssembly.send(hcd, GetAxisStats)
    val stats = fakeAssembly.expectMsgClass(classOf[CurrentState])
    //println("Stats: " + stats)
    stats.configKey should equal(TromboneHCD.axisStatsCK)
    stats.item(datumCountKey).head should equal(1)
    stats.item(moveCountKey).head should equal(6)
    stats.item(homeCountKey).head should equal(2)
    stats.item(limitCountKey).head should equal(1)
    stats.item(successCountKey).head should equal(6)
    stats.item(failureCountKey).head should be(0)
    stats.item(cancelCountKey).head should be(0)

    fakeAssembly.send(hcd, Unsubscribe)
    cleanup(hcd)
  }

  it("start up a move and cancel it") {
    val hcd = startHCD

    val fakeAssembly = TestProbe()

    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

    fakeAssembly.send(hcd, Subscribe)

    val testPos = 1000

    fakeAssembly.send(hcd, Submit(positionSC(testPos)))

    // wait for 2 updates
    fakeAssembly.receiveN(2)
    fakeAssembly.send(hcd, Submit(cancelSC))
    val msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(stateKey).head should be(AXIS_IDLE)

    // Get summary stats
    fakeAssembly.send(hcd, GetAxisStats)
    val stats = fakeAssembly.expectMsgClass(classOf[CurrentState])
    //println("Stats: " + stats)
    stats.configKey should equal(TromboneHCD.axisStatsCK)
    stats.item(moveCountKey).head should equal(1)
    stats.item(successCountKey).head should equal(1)
    stats.item(cancelCountKey).head should be(1)

    fakeAssembly.send(hcd, Unsubscribe)
    cleanup(hcd)
  }

  it("should allow repetitive moves") {
    // Starts at 350, init (351), go home, small moves repeating */
    val hcd = startHCD

    val fakeAssembly = TestProbe()

    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

    fakeAssembly.send(hcd, Subscribe)

    // Init 1
    fakeAssembly.send(hcd, Submit(SetupConfig(axisDatumCK))) // Could use ones in TromboneHCD
    var msgs = waitForMoveMsgs(fakeAssembly)
    msgs.last(stateKey).head should be(AXIS_IDLE)

    // Move 2
    fakeAssembly.send(hcd, Submit(homeSC))
    msgs = waitForMoveMsgs(fakeAssembly)
    msgs.last(inHomeKey).head should be(true)

    val start = 300
    val finish = 500
    val stepSize = 10
    for (loops <- 1 to 2) {
      logger.info(s"Loop: $loops")
      for (testPos <- start to finish by stepSize) {
        fakeAssembly.send(hcd, Submit(positionSC(testPos)))
        msgs = waitForMoveMsgs(fakeAssembly)
      }
    }
    cleanup(hcd)
  }

  it("should drive into limits") {
    // Starts at 350, goes to zero */
    val hcd = startHCD

    val fakeAssembly = TestProbe()

    fakeAssembly.send(hcd, SubscribeLifecycleCallback(fakeAssembly.ref))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

    fakeAssembly.send(hcd, Subscribe)

    // Get the axis config  for testing limits
    fakeAssembly.send(hcd, GetAxisConfig)

    // The values are hard-coded because we can't look at the config inside the actor, will fail if config changes
    val config: CurrentState = fakeAssembly.expectMsgClass(classOf[CurrentState])
    val lowLimit = config(lowLimitKey).head
    val highLimit = config(highLimitKey).head

    // Move to 0
    var testPos = 0
    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
    var msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(positionKey).head should be(lowLimit)
    msgs.last(stateKey).head should be(AXIS_IDLE)
    msgs.last(inLowLimitKey).head should be(true)
    msgs.last(inHighLimitKey).head should be(false)

    // Move to 2000
    testPos = 2000
    fakeAssembly.send(hcd, Submit(positionSC(testPos)))
    msgs = waitForMoveMsgs(fakeAssembly)
    // Check the last message
    msgs.last(positionKey).head should be(highLimit)
    msgs.last(stateKey).head should be(AXIS_IDLE)
    msgs.last(inLowLimitKey).head should be(false)
    msgs.last(inHighLimitKey).head should be(true)
    cleanup(hcd)
  }

  //  def stopComponent(supervisorSystem: ActorSystem, supervisor: ActorRef, timeout: FiniteDuration): Unit = {
  //    //system.scheduler.scheduleOnce(timeout) {
  //    println("STOPPING")
  //    Supervisor.haltComponent(supervisor)
  //    Await.ready(supervisorSystem.whenTerminated, 5.seconds)
  //    //system.terminate()
  //    System.exit(0)
  //    //}
  //  }

}
