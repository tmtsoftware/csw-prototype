package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.assembly.FollowCommand.{StopFollowing, UpdateZAandFE}
import csw.examples.vslice.hcd.TromboneHCD
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.ccs.HcdController._
import csw.services.events._
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.LocationService
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
import csw.services.pkg.Supervisor3
import csw.services.pkg.Supervisor3.{LifecycleInitialized, LifecycleRunning}
import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
import csw.util.config.BooleanItem
import csw.util.config.Events.{StatusEvent, SystemEvent}
import csw.util.config.StateVariable.CurrentState
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, _}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object FollowCommandTests {
  LocationService.initInterface()
  val system = ActorSystem("FollowCommandTests")

  // Test subscriber actor for telemetry
  object TestSubscriber {
    def props(): Props = Props(new TestSubscriber())

    case object GetResults

    case class Results(msgs: Vector[Event])
  }

  class TestSubscriber extends Actor with ActorLogging {
    import TestSubscriber._

    var msgs = Vector.empty[Event]

    def receive: Receive = {
      case event: SystemEvent =>
        msgs = msgs :+ event
        log.info(s"-------->RECEIVED System ${event.info.source} event: $event")
      case event: StatusEvent =>
        msgs = msgs :+ event
        log.info(s"-------->RECEIVED Status ${event.info.source} event: $event")

      case GetResults => sender() ! Results(msgs)
    }
  }

}

/**
 * TMT Source Code: 9/21/16.
 */
class FollowCommandTests extends TestKit(FollowCommandTests.system) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {

  import FollowCommandTests._
  import system.dispatcher

  // Name of the event service Redis instance to use
  val esName = "FollowCommandTests"

  implicit val timeout = Timeout(10.seconds)

  // Used to start and stop the event service Redis instance used for the test
  var eventAdmin: EventServiceAdmin = _

  // Get the event service by looking up the name with the location service.
  var eventService: EventService = _

  override def beforeAll() = {
    // Note: This is only for testing: Normally Redis would already be running and registered with the location service.
    // Start redis and register it with the location service on a random free port.
    // The following is the equivalent of running this from the command line:
    //   tracklocation --name "Event Service Test" --command "redis-server --port %port"
    EventServiceAdmin.startEventService(esName)

    // Get the event service by looking it up the name with the location service.
    eventService = Await.result(EventService(esName), timeout.duration)

    // This is only used to stop the Redis instance that was started for this test
    eventAdmin = EventServiceAdmin(eventService)
  }

  override protected def afterAll(): Unit = {
    // Shutdown Redis (Only do this in tests that also started the server)
    Try(if (eventAdmin != null) Await.ready(eventAdmin.shutdown(), timeout.duration))
    TestKit.shutdownActorSystem(system)
  }

  val assemblyContext = AssemblyTestData.TestAssemblyContext
  val calculationConfig = assemblyContext.calculationConfig
  val controlConfig = assemblyContext.controlConfig
  import assemblyContext._

  def newTestFollowCommand(nssInUse: BooleanItem, tromboneHCD: Option[ActorRef], eventPublisher: Option[ActorRef]): TestActorRef[FollowCommand] = {
    val props = FollowCommand.props(assemblyContext, nssInUse, tromboneHCD, eventPublisher, Some(eventService))
    TestActorRef(props)
  }

  def newFollowCommand(isNssInUse: BooleanItem, tromboneHCD: Option[ActorRef], eventPublisher: Option[ActorRef]): ActorRef = {
    val props = FollowCommand.props(assemblyContext, isNssInUse, tromboneHCD, eventPublisher, Some(eventService))
    system.actorOf(props, "newfollow")
  }

  // The following are used to start a tromboneHCD for testing purposes
  def startHCD: ActorRef = {
    val testInfo = HcdInfo(
      TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Set(AkkaType), 1.second
    )

    Supervisor3(testInfo)
  }

  /**
   * This expect message will absorb CurrentState messages as long as the current is not equal the desired destination
   * Then it collects the one where it is the destination and the end message
   * @param tp TestProbe that is receiving the CurrentState messages
   * @param dest a TestProbe acting as the assembly
   * @return A sequence of CurrentState messages
   */
  def expectMoveMsgsWithDest(tp: TestProbe, dest: Int): Seq[CurrentState] = {
    val msgs = tp.receiveWhile(5.seconds) {
      case m @ CurrentState(ck, _) if ck.prefix.contains(TromboneHCD.axisStatePrefix) && m(TromboneHCD.positionKey).head != dest => m
      // This is present to pick up the first status message
      case st @ CurrentState(ck, _) if ck.prefix.equals(TromboneHCD.axisStatsPrefix) => st
    }
    val fmsg1 = tp.expectMsgClass(classOf[CurrentState]) // last one with current == target
    val fmsg2 = tp.expectMsgClass(classOf[CurrentState]) // the the end event with IDLE
    val allmsgs = msgs :+ fmsg1 :+ fmsg2
    allmsgs
  }

  /**
   * This will accept CurrentState messages until a state value is AXIS_IDLE
   * This is useful when you know there is one move and it will end without being updated
   * @param tp TestProbe that is the destination of the CurrentState messages
   * @return a Sequence of CurrentState messages
   */
  def waitForMoveMsgs(tp: TestProbe): Seq[CurrentState] = {
    val msgs = tp.receiveWhile(5.seconds) {
      case m @ CurrentState(ck, _) if ck.prefix.contains(TromboneHCD.axisStatePrefix) && m(TromboneHCD.stateKey).head == TromboneHCD.AXIS_MOVING => m
      // This is present to pick up the first status message
      case st @ CurrentState(ck, _) if ck.prefix.equals(TromboneHCD.axisStatsPrefix) => st
    }
    val fmsg = tp.expectMsgClass(classOf[CurrentState]) // last one -- with AXIS_IDLE
    val allmsgs = msgs :+ fmsg
    allmsgs
  }

  describe("basic event command setup") {

    it("should be created with no issues") {
      val fakeTromboneHCD = TestProbe()

      val fc: TestActorRef[FollowCommand] = newTestFollowCommand(setNssInUse(false), Some(fakeTromboneHCD.ref), None)

      fc.underlyingActor.nssInUseIn shouldBe setNssInUse(false)
      fc.underlyingActor.tromboneHCDIn should equal(Some(fakeTromboneHCD.ref))

      fc ! StopFollowing
      fakeTromboneHCD.expectNoMsg(250.milli)
    }
  }

  describe("Tests of the overall collection of actors in follow commmand") {
    /**
     * Test Description: This test creates a trombone HCD to receive events from the FollowActor when nssNotInUse.
     * This tests the entire path with fake TCS sending events through Event Service, which are received by
     * TromboneSubscriber and then processed by FollowActor, which sends them to TromboneControl
     * which sends them to the TromboneHCD, which replies with StateUpdates.
     * The FollowActor is also publishing eng and sodiumLayer StatusEvents, which are published to the event service
     * and subscribed to by test clients, that collect their events for checking at the end
     * The first part is about starting the HCD and waiting for it to reach the runing lifecycle state where it can receive events
     */
    it("1 creates fake TCS/RTC events with Event Service through FollowActor and back to HCD instance - nssNotInUse") {
      import AssemblyTestData._
      import Algorithms._
      import TestSubscriber._

      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      //info("Running")

      // This has HCD sending updates back to this Assembly
      fakeAssembly.send(tromboneHCD, Subscribe)

      val eventPublisher = system.actorOf(TrombonePublisher.props(assemblyContext, Some(eventService)), "eventpublisher1")
      val fc = newFollowCommand(setNssInUse(false), Some(tromboneHCD), Some(eventPublisher))

      // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
      val tcsRtc = eventService

      val testFE = 20.0
      // Publish a single focus error. This will generate a published event
      tcsRtc.publish(SystemEvent(focusErrorPrefix).add(fe(testFE)))

      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
      val resultSubscriber1 = system.actorOf(TestSubscriber.props())
      eventService.subscribe(resultSubscriber1, postLastEvents = true, aoSystemEventPrefix)

      val resultSubscriber2 = system.actorOf(TestSubscriber.props())
      eventService.subscribe(resultSubscriber2, postLastEvents = true, engStatusEventPrefix)

      // These are fake messages for the FollowActor that will be sent to simulate the TCS updating ZA
      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

      // This should result in the length of tcsEvents being published, which is 15
      //tcsEvents.map(f => tcsRtc.publish(f))
      // This should result in the length of tcsEvents being published, which is 15
      tcsEvents.foreach { f =>
        logger.info("Publish: " + f)
        tcsRtc.publish(f)
        // The following is not required, but is added to make the event timing more interesting
        // Varying this delay from 50 to 10 shows completion of moves and at 10 update of move positions before finishing
        Thread.sleep(500) // 500 makes it seem more interesting to watch, but is not needed for proper operation
      }

      // ---- Everything from here on is about gathering the data and checking

      // The following constructs the expected messages that contain the encoder positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are working properly.
      // First keep focus error fixed at 10 um
      val testdata = calculatedTestData(calculationConfig, controlConfig, testFE)

      // This uses the total elevation to get expected values for encoder position
      val encExpected = getenc(testdata.last)
      logger.info(s"encExpected1: $encExpected")

      // This gets the first set of CurrentState messages for moving to the FE 10 mm position
      val msgs = expectMoveMsgsWithDest(fakeAssembly, encExpected)
      msgs.last(positionKey).head should be(encExpected)
      msgs.last(stateKey).head should equal(AXIS_IDLE)
      msgs.last(inLowLimitKey).head should equal(false)
      msgs.last(inHighLimitKey).head should equal(false)

      // Check that nothing is happening - not needed
      fakeAssembly.expectNoMsg(200.milli)

      // Stop this follow command
      system.stop(fc)
      system.stop(eventPublisher)

      resultSubscriber1 ! GetResults
      // Check the events received through the Event Service
      val result1 = expectMsgClass(classOf[Results])
      //logger.info("Result 1: " + result1)

      // Calculate expected events
      val testResult = newRangeAndElData(testFE)

      val firstOne = SystemEvent(aoSystemEventPrefix).madd(naElevationKey -> testResult.head._2 withUnits naElevationUnits, naRangeDistanceKey -> testResult.head._1 withUnits naRangeDistanceUnits)
      val zaExpected = testResult.map(f => SystemEvent(aoSystemEventPrefix).madd(naElevationKey -> f._2 withUnits naElevationUnits, naRangeDistanceKey -> f._1 withUnits naRangeDistanceUnits))
      val aoeswExpected = firstOne +: zaExpected
      //info("aowes: " + aoeswExpected)
      result1.msgs should equal(aoeswExpected)

      resultSubscriber2 ! GetResults
      // Check the events received through the Event Service
      val result2 = expectMsgClass(classOf[Results])
      //logger.info("Result 2: " + result2)

      val calcTestData = calculatedTestData(calculationConfig, controlConfig, testFE)

      val firstStage = Algorithms.rangeDistanceToStagePosition(gettrd(calcTestData(0)))
      val firstZA = getza(calcTestData(0))

      val firstEng = StatusEvent(engStatusEventPrefix).madd(focusErrorKey -> testFE withUnits focusErrorUnits, stagePositionKey -> firstStage withUnits stagePositionUnits, zenithAngleKey -> firstZA withUnits zenithAngleUnits)

      val zaEngExpected = calcTestData.map(f => StatusEvent(engStatusEventPrefix).madd(focusErrorKey -> testFE withUnits focusErrorUnits, stagePositionKey -> rangeDistanceToStagePosition(gettrd(f)) withUnits stagePositionUnits, zenithAngleKey -> getza(f) withUnits zenithAngleUnits))
      val engExpected = firstEng +: zaEngExpected
      result2.msgs should equal(engExpected)
    }

    /**
     * Test Description: This test creates a trombone HCD to receive events from the FollowActor when nssNotInUse.
     * This tests the entire path with fake TCS sending events through Event Service, which are received by
     * TromboneSubscriber and then processed by FollowActor, which sends them to TromboneControl
     * which sends them to the TromboneHCD, which replies with StateUpdates.
     * The FollowActor is also publishing eng and sodiumLayer StatusEvents, which are published to the event service
     * and subscribed to by test clients, that collect their events for checking at the end
     * The first part is about starting the HCD and waiting for it to reach the runing lifecycle state where it can receive events
     */
    it("2 creates fake TCS/RTC events with Event Service through FollowActor and back to HCD instance - nssInUse") {
      import AssemblyTestData._
      import TestSubscriber._

      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      //info("Running")

      // This has HCD sending updates back to this Assembly
      fakeAssembly.send(tromboneHCD, Subscribe)

      // First set it up so we can ensure initial za
      val eventPublisher = system.actorOf(TrombonePublisher.props(assemblyContext, Some(eventService)), "eventpublisher2")
      val fc = newFollowCommand(setNssInUse(true), Some(tromboneHCD), Some(eventPublisher))
      // Initialize the fe and za
      val testZA = 30.0
      val testFE = 10.0
      fc ! UpdateZAandFE(za(testZA), fe(testFE))
      waitForMoveMsgs(fakeAssembly)

      // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
      val tcsRtc = eventService

      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
      val resultSubscriber1 = TestActorRef(TestSubscriber.props())
      eventService.subscribe(resultSubscriber1, postLastEvents = true, aoSystemEventPrefix)

      val resultSubscriber2 = TestActorRef(TestSubscriber.props())
      eventService.subscribe(resultSubscriber2, postLastEvents = true, engStatusEventPrefix)

      // These are fake messages for the FollowActor that will be sent to simulate the TCS updating ZA
      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

      // This should result in the length of tcsEvents being published, which is 15
      // Since nss is in use, no events will be published because za is not subscribed to
      tcsEvents.take(3).foreach { f =>
        logger.info("Publish: " + f)
        tcsRtc.publish(f)
        // The following is not required, but is added to make the event timing more interesting
        // Varying this delay from 50 to 10 shows completion of moves and at 10 update of move positions before finishing
        Thread.sleep(200) // 500 makes it seem more interesting to watch, but is not needed for proper operation
      }

      // Check that nothing is happening - i.e. no events nor is anything coming back from the follow actor or HCD
      fakeAssembly.expectNoMsg(500.milli)
      resultSubscriber1 ! GetResults
      expectMsg(Results(Vector.empty[Event]))

      // Now create some fe events
      // Publish a set of focus errors. This will generate published events but ZA better be 0
      val feEvents = testFocusErrors.map(f => SystemEvent(focusErrorPrefix).add(fe(f)))
      feEvents.foreach { f =>
        logger.info("Publish FE: " + f)
        tcsRtc.publish(f)
        // The following is not required, but is added to make the event timing more interesting
        // Varying this delay from 50 to 10 shows completion of moves and at 10 update of move positions before finishing
        Thread.sleep(500) // 500 makes it seem more interesting to watch, but is not needed for proper operation
      }

      val calcData = calculatedTestData(calculationConfig, controlConfig, testFocusErrors.last)
      val encExpected = calcData.map(getenc).head
      info(s"encEx: $encExpected")

      // This gets the first set of CurrentState messages for moving to the FE 10 mm position
      val msgs = expectMoveMsgsWithDest(fakeAssembly, encExpected)
      msgs.last(positionKey).head should be(encExpected)
      msgs.last(stateKey).head should equal(AXIS_IDLE)
      msgs.last(inLowLimitKey).head should equal(false)
      msgs.last(inHighLimitKey).head should equal(false)

      // Stop this follow command
      system.stop(fc)
      system.stop(eventPublisher)

      // verify that the eng messages are the right number and that za is always 0
      // Still no AOESW events
      resultSubscriber1 ! GetResults
      expectMsg(Results(Vector.empty[Event]))

      // Now get the engr telemetry
      resultSubscriber2 ! GetResults
      val results: Results = expectMsgClass(classOf[Results])
      val engs = results.msgs.map(_.asInstanceOf[StatusEvent])
      // Verify that the za is always 0.0 when inNssMode
      engs.map(f => f.item(zenithAngleKey).head).filter(_ != 0.0) shouldBe empty
    }

    /**
     * Test Description: This test creates a trombone HCD to receive events from the FollowActor when nssNotInUse.
     * This tests the entire path with fake TCS sending events through Event Service, which are received by
     * TromboneSubscriber and then processed by FollowActor, which sends them to TromboneControl
     * which sends them to the TromboneHCD, which replies with StateUpdates.
     * The FollowActor is also publishing eng and sodiumLayer StatusEvents, which are published to the event service
     * and subscribed to by test clients, that collect their events for checking at the end
     * The first part is about starting the HCD and waiting for it to reach the runing lifecycle state where it can receive events.
     * This test verifies that the updatehcd message works by sending None which causes the position updates to stop
     */
    it("3 creates fake TCS/RTC events with Event Service through FollowActor and back to HCD instance - check that update HCD works") {
      import AssemblyTestData._

      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      //info("Running")

      // This has HCD sending updates back to this Assembly
      fakeAssembly.send(tromboneHCD, Subscribe)

      val eventPublisher = system.actorOf(TrombonePublisher.props(assemblyContext, Some(eventService)), "eventpublisher3")
      val fc = newFollowCommand(setNssInUse(false), Some(tromboneHCD), Some(eventPublisher))

      // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
      val tcsRtc = eventService

      // These are fake messages for the FollowActor that will be sent to simulate the TCS updating ZA
      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

      // This should result in the length of tcsEvents being published, which is 15
      //tcsEvents.map(f => tcsRtc.publish(f))
      // This should result in the length of tcsEvents being published, which is 15
      tcsEvents.foreach { f =>
        logger.info("Publish: " + f)
        tcsRtc.publish(f)
        // The following is not required, but is added to make the event timing more interesting
        // Varying this delay from 50 to 10 shows completion of moves and at 10 update of move positions before finishing
        Thread.sleep(100)
      }
      val testFE = 0.0
      val testdata = calculatedTestData(calculationConfig, controlConfig, testFE)

      // This uses the total elevation to get expected values for encoder position
      expectMoveMsgsWithDest(fakeAssembly, getenc(testdata.last))

      // Now update to tromboneHCD = none
      fc ! TromboneAssembly.UpdateTromboneHCD(None)

      tcsEvents.foreach { f =>
        logger.info("Publish: " + f)
        tcsRtc.publish(f)
        // The following is not required, but is added to make the event timing more interesting
        // Varying this delay from 50 to 10 shows completion of moves and at 10 update of move positions before finishing
        Thread.sleep(100)
      }
      // Should get no messages
      fakeAssembly.expectNoMsg(200.milli)

      // Stop this follow command
      system.stop(fc)
      system.stop(eventPublisher)
    }

  }
}
