package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.TestEnv
import csw.examples.vslice.assembly.AssemblyContext.{TromboneCalculationConfig, TromboneControlConfig}
import csw.examples.vslice.assembly.FollowActor.UpdatedEventData
import csw.examples.vslice.assembly.TromboneControl.GoToStagePosition
import csw.examples.vslice.assembly.TrombonePublisher.{AOESWUpdate, EngrUpdate}
import csw.examples.vslice.hcd.TromboneHCD
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.ccs.HcdController._
import csw.services.events._
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.LocationService
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
import csw.services.pkg.Supervisor
import csw.services.pkg.Supervisor.{LifecycleInitialized, LifecycleRunning}
import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
import csw.util.config.BooleanItem
import csw.util.config.Events.{EventTime, StatusEvent, SystemEvent}
import csw.util.config.StateVariable.CurrentState
import csw.util.config.UnitsOfMeasure.{degrees, kilometers, micrometers, millimeters}
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, ShouldMatchers}

import scala.concurrent.Await
import scala.concurrent.duration._

object FollowActorTests {
  LocationService.initInterface()
  val system = ActorSystem("FollowActorTests")

  val initialElevation = 90.0

  // Test subscriber actor for telemetry
  object TestSubscriber {
    def props(): Props = Props(new TestSubscriber())

    case object GetResults

    case class Results(msgs: Vector[Event])

  }

  class TestSubscriber extends Actor with ActorLogging {

    import TestSubscriber._

    var msgs: Vector[Event] = Vector.empty[Event]

    def receive: Receive = {
      case event: SystemEvent =>
        msgs = msgs :+ event
        log.info(s"RECEIVED System ${event.info.source} event: $event")
      case event: StatusEvent =>
        msgs = msgs :+ event
        log.info(s"RECEIVED Status ${event.info.source} event: $event")

      case GetResults => sender() ! Results(msgs)
    }
  }

}

/**
 * TMT Source Code: 8/12/16.
 */
class FollowActorTests extends TestKit(FollowActorTests.system) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {

  import FollowActorTests._

  implicit val timeout = Timeout(10.seconds)

  // Used to start and stop the event service Redis instance used for the test
  //  var eventAdmin: EventServiceAdmin = _

  // Get the event service by looking up the name with the location service.
  val eventService: EventService = Await.result(EventService(), timeout.duration)

  // Get the telemetry service by looking up the name with the location service. -- Used in the last test
  val telemetryService: TelemetryService = Await.result(TelemetryService(), timeout.duration)

  override def beforeAll(): Unit = {
    TestEnv.createTromboneAssemblyConfig()
  }

  override protected def afterAll(): Unit = {
    // Shutdown Redis (Only do this in tests that also started the server)
    //    Try(if (eventAdmin != null) Await.ready(eventAdmin.shutdown(), timeout.duration))
    TestKit.shutdownActorSystem(system)
  }

  val assemblyContext = AssemblyTestData.TestAssemblyContext
  val calculationConfig: TromboneCalculationConfig = assemblyContext.calculationConfig
  val controlConfig: TromboneControlConfig = assemblyContext.controlConfig

  import assemblyContext._

  def newFollower(usingNSS: BooleanItem, tromboneControl: ActorRef, aoPublisher: ActorRef, engPublisher: ActorRef): TestActorRef[FollowActor] = {
    // Used for creating followers
    val initialElevation = iElevation(assemblyContext.calculationConfig.defaultInitialElevation)
    val props = FollowActor.props(assemblyContext, initialElevation, usingNSS, Some(tromboneControl), Some(aoPublisher), Some(engPublisher))
    TestActorRef(props)
  }

  describe("Basic tests for connectivity") {
    val fakeTC = TestProbe()
    val fakePub = TestProbe()
    val fakeEng = TestProbe()

    it("should allow creation with defaults") {
      // test1
      val cal = newFollower(setNssInUse(false), fakeTC.ref, fakePub.ref, fakeEng.ref)

      cal.underlyingActor.initialElevation should be(iElevation(calculationConfig.defaultInitialElevation))

      fakeTC.expectNoMsg(1.seconds)
      system.stop(cal)
    }
  }

  describe("Test set initial elevation") {
    val fakeTC = TestProbe()
    val fakePub = TestProbe()
    val fakeEng = TestProbe()

    it("should be default before") {
      // test2
      val cal = newFollower(setNssInUse(false), fakeTC.ref, fakePub.ref, fakeEng.ref)

      cal.underlyingActor.initialElevation should be(iElevation(calculationConfig.defaultInitialElevation))
      system.stop(cal)
    }

    /*
    it("should change after setting") {
      val cal = newFollower(setNssInUse(false), fakeTC.ref, fakePub.ref, fakeEng.ref)

      val newEl = initialElevationKey -> 85.0 withUnits kilometers

      cal ! SetElevation(newEl)

      cal.underlyingActor.initialElevation should be(newEl)
    }
    */
  }

  // -------------- The following set of tests use an actual tromboneHCD for testing  --------------------
  // The following are used to start a tromboneHCD for testing purposes
  def startHCD: ActorRef = {
    val testInfo = HcdInfo(
      TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Set(AkkaType), 1.second
    )

    Supervisor(testInfo)
  }

  describe("Test for handling of Update events") {
    val fakeTC = TestProbe()
    val fakePub = TestProbe()
    val fakeEng = TestProbe()

    it("should at least handle and send messages") {
      // test3
      val cal = newFollower(setNssInUse(false), fakeTC.ref, fakePub.ref, fakeEng.ref)

      // This should result in two messages being sent, one to each actor in the given order
      cal ! UpdatedEventData(za(0), fe(0), EventTime())

      fakeTC.expectMsgClass(classOf[GoToStagePosition])
      fakePub.expectMsgClass(classOf[AOESWUpdate])
      system.stop(cal)
    }

    it("should ignore if units wrong") {
      // test4
      val cal = newFollower(setNssInUse(false), fakeTC.ref, fakePub.ref, fakeEng.ref)

      // This should result in two messages being sent, one to each actor in the given order
      info("Note: This produced an error message for improper units, which is okay!")
      cal ! UpdatedEventData(zenithAngleKey -> 0, focusErrorKey -> 0, EventTime())

      fakeTC.expectNoMsg(100.milli)
      system.stop(cal)
    }

    it("should ignore if inputs out of range") {
      // test5
      val cal = newFollower(setNssInUse(false), fakeTC.ref, fakePub.ref, fakeEng.ref)

      // This should result in two messages being sent, one to each actor in the given order
      info("Note: This produced two error messages for out of range data, which are okay!")
      cal ! UpdatedEventData(za(-10), fe(0), EventTime())
      fakeTC.expectNoMsg(100.milli)

      cal ! UpdatedEventData(za(0.0), fe(42.0), EventTime())
      fakeTC.expectNoMsg(100.milli)
      system.stop(cal)
    }
  }

  /**
   * Test Description: This test provides simulated UpdatedEventData events to the FollowActor and then tests that the
   * FollowActor sends the expected messages out including:
   * Events for AOESW
   * Positions for Trombone Stage
   * Engineering Status event
   * The events are received by "fake" actors played by TestProbes
   */
  describe("Test for reasonable results when setNssInUse(false)") {
    import Algorithms._
    import AssemblyTestData._

    val fakeTC = TestProbe()
    val fakePub = TestProbe()
    val fakeEng = TestProbe()

    it("should work when only changing zenith angle") {
      // test6
      val follower = newFollower(setNssInUse(false), fakeTC.ref, fakePub.ref, fakeEng.ref)

      // Generate a list of fake event updates for a range of zenith angles and focus error 10mm
      val testFocusError = 10.0
      // testZenithAngles is in AlgorithmData
      val events = testZenithAngles.map(f => UpdatedEventData(za(f), fe(testFocusError), EventTime()))

      // Send the events to the follow actor
      events.foreach(f => follower ! f)

      // Expect a set of AOESWUpdate messages to the fake publisher
      val aoEvts = fakePub.receiveN(testZenithAngles.size)
      //info(s"aoEvts: $aoEvts")

      // Expect a set of HCDTrombonePosition messages to the fake trombone sender
      val trPos = fakeTC.receiveN(testZenithAngles.size)
      //info(s"trPos: $trPos")

      // sender of eng msgs
      val engMsgs = fakeEng.receiveN(testZenithAngles.size)
      //info(s"engM: $engMsgs")

      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are proper
      val testdata = newRangeAndElData(testFocusError)

      // This checks the events for AO ESW event
      val aoeswExpected = testdata.map(f => AOESWUpdate(naElevationKey -> f._2 withUnits kilometers, naRangeDistanceKey -> f._1 withUnits kilometers))
      aoeswExpected should equal(aoEvts)

      // state position is total elevation in mm
      val stageExpected = testdata.map(f => GoToStagePosition(stagePositionKey -> f._1 withUnits stagePositionUnits))
      stageExpected should equal(trPos)

      val calcTestData = calculatedTestData(calculationConfig, controlConfig, testFocusError)

      val engExpected = calcTestData.map(f => EngrUpdate(focusErrorKey -> testFocusError withUnits micrometers, stagePositionKey -> rangeDistanceToStagePosition(gettrd(f)) withUnits millimeters, zenithAngleKey -> getza(f) withUnits degrees))
      engExpected should equal(engMsgs)
      system.stop(follower)
    }

    it("should get other events when nssInUse but not aoesw events") {
      // test7
      val follower = newFollower(setNssInUse(true), fakeTC.ref, fakePub.ref, fakeEng.ref)

      // Generate a list of fake event updates for a range of zenith angles and focus error 10mm
      val testFocusError = 10.0
      // testZenithAngles is in AlgorithmData
      val events = testZenithAngles.map(f => UpdatedEventData(za(f), fe(testFocusError), EventTime()))

      // Send the events to the follow actor
      events.foreach(f => follower ! f)

      // Expect a set of HCDTrombonePosition messages to the fake trombone sender
      val trPos = fakeTC.receiveN(testZenithAngles.size)
      //info(s"trPos: $trPos")

      // sender of eng msgs
      val engMsgs = fakeEng.receiveN(testZenithAngles.size)
      //info(s"engM: $engMsgs")

      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are proper
      val testdata = newRangeAndElData(testFocusError)

      // Expect no AOESWUpdate messages to the fake publisher when nssInuse, wait a bit to check that no messages arrive
      fakePub.expectNoMsg(200.milli)

      // state position is total elevation in mm
      val stageExpected = testdata.map(f => GoToStagePosition(stagePositionKey -> f._1 withUnits stagePositionUnits))
      stageExpected should equal(trPos)

      val calcTestData = calculatedTestData(calculationConfig, controlConfig, testFocusError)

      val engExpected = calcTestData.map(f => EngrUpdate(focusErrorKey -> testFocusError withUnits micrometers, stagePositionKey -> rangeDistanceToStagePosition(gettrd(f)) withUnits millimeters, zenithAngleKey -> getza(f) withUnits degrees))
      engExpected should equal(engMsgs)
      system.stop(follower)
    }

    /**
     * This expect message will absorb CurrentState messages as long as the current is not equal the desired destination
     * Then it collects the one where it is the destination and the end message
     *
     * @param tp   TestProbe that is receiving the CurrentState messages
     * @param dest a TestProbe acting as the assembly
     *
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
     * Test Description: This test creates a trombone HCD to receive events from the FollowActor when nssNotInUse.
     * This tests the entire path with fake TCS sending events through Event Service, which are received by
     * TromboneSubscriber and then processed by FollowActor, which sends them to TromboneControl
     * which sends them to the TromboneHCD, which replies with StateUpdates.
     * The FollowActor is also publishing eng and sodiumLayer StatusEvents, which are published to the event service
     * and subscribed to by test clients, that collect their events for checking at the end
     * The first part is about starting the HCD and waiting for it to reach the runing lifecycle state where it can receive events
     */
    it("creates fake TCS/RTC events with Event Service through FollowActor and back to HCD instance") {
      // test8
      import TestSubscriber._

      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      //info("Running")

      // This has HCD sending updates back to this Assembly
      fakeAssembly.send(tromboneHCD, Subscribe)

      // Ignoring the messages for TrombonePosition
      // Create the trombone publisher for publishing SystemEvents to AOESW
      val publisherActorRef = system.actorOf(TrombonePublisher.props(assemblyContext, Some(eventService), Some(telemetryService)))

      // Ignoring the messages for AO for the moment
      // Create the trombone publisher for publishing SystemEvents to AOESW
      val tromboneControl = system.actorOf(TromboneControl.props(assemblyContext))
      tromboneControl ! TromboneAssembly.UpdateTromboneHCD(Some(tromboneHCD))

      val nssUsage = setNssInUse(false)
      // Create the follow actor and give it the actor ref of the publisher for sending calculated events
      // The following uses the same publisher actor for both AOESW and Eng events
      val followActor = newFollower(nssUsage, tromboneControl, publisherActorRef, publisherActorRef)

      // create the subscriber that receives events from TCS for zenith angle and focus error from RTC
      val tromboneEventSubscriber = system.actorOf(TromboneEventSubscriber.props(assemblyContext, nssUsage, Some(followActor), eventService))

      // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
      val tcsRtc = Some(eventService)

      val testFE = 20.0
      // Publish a single focus error. This will generate a published event
      tcsRtc.foreach(_.publish(SystemEvent(focusErrorPrefix).add(fe(testFE))))

      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
      val resultSubscriber1 = system.actorOf(TestSubscriber.props())
      eventService.subscribe(resultSubscriber1, postLastEvents = false, aoSystemEventPrefix)

      val resultSubscriber2 = system.actorOf(TestSubscriber.props())
      eventService.subscribe(resultSubscriber2, postLastEvents = false, engStatusEventPrefix)

      // These are fake messages for the FollowActor that will be sent to simulate the TCS updating ZA
      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

      // This should result in the length of tcsEvents being published, which is 15
      //tcsEvents.map(f => tcsRtc.publish(f))
      // This should result in the length of tcsEvents being published, which is 15
      tcsEvents.foreach { f =>
        logger.info("Publish: " + f)
        tcsRtc.foreach(_.publish(f))
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

      resultSubscriber1 ! GetResults
      // Check the events received through the Event Service
      val result1 = expectMsgClass(classOf[Results])
      //logger.info("Result 1: " + result1)

      // Calculate expected events
      val testResult = newRangeAndElData(testFE)

      val firstOne = SystemEvent(aoSystemEventPrefix).madd(naElevationKey -> testResult.head._2 withUnits naElevationUnits, naRangeDistanceKey -> testResult.head._1 withUnits naRangeDistanceUnits)
      val zaExpected = testResult.map(f => SystemEvent(aoSystemEventPrefix).madd(naElevationKey -> f._2 withUnits naElevationUnits, naRangeDistanceKey -> f._1 withUnits naRangeDistanceUnits))
      val aoeswExpected = firstOne +: zaExpected
      //logger.info("aowes: " + aoeswExpected)
      //logger.info("size: " + aoeswExpected.size)
      result1.msgs should equal(aoeswExpected)

      resultSubscriber2 ! GetResults
      // Check the events received through the Event Service
      val result2 = expectMsgClass(classOf[Results])
      //logger.info("Result 2: " + result2)

      val calcTestData = calculatedTestData(calculationConfig, controlConfig, testFE)

      val firstStage = rangeDistanceToStagePosition(gettrd(calcTestData(0)))
      val firstZA = getza(calcTestData(0))

      val firstEng = StatusEvent(engStatusEventPrefix).madd(focusErrorKey -> testFE withUnits focusErrorUnits, stagePositionKey -> firstStage withUnits stagePositionUnits, zenithAngleKey -> firstZA withUnits zenithAngleUnits)

      val zaEngExpected = calcTestData.map(f => StatusEvent(engStatusEventPrefix).madd(focusErrorKey -> testFE withUnits focusErrorUnits, stagePositionKey -> rangeDistanceToStagePosition(gettrd(f)) withUnits stagePositionUnits, zenithAngleKey -> getza(f) withUnits zenithAngleUnits))
      val engExpected = firstEng +: zaEngExpected
      result2.msgs should equal(engExpected)

      tromboneHCD ! PoisonPill
      system.stop(publisherActorRef)
      system.stop(tromboneControl)
      system.stop(followActor)
      system.stop(tromboneEventSubscriber)
      system.stop(resultSubscriber1)
      system.stop(resultSubscriber2)
    }
  }

}
