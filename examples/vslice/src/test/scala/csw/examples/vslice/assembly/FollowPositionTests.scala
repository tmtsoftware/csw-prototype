package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.TestEnv
import csw.examples.vslice.assembly.FollowActor.UpdatedEventData
import csw.examples.vslice.assembly.TromboneControl.GoToStagePosition
import csw.examples.vslice.hcd.TromboneHCD
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.ccs.HcdController._
import csw.services.events.EventService
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.LocationService
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
import csw.services.pkg.Supervisor
import csw.services.pkg.Supervisor.{HaltComponent, LifecycleInitialized, LifecycleRunning}
import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
import csw.util.config.Configurations.SetupConfig
import csw.util.config.DoubleItem
import csw.util.config.Events.{EventTime, SystemEvent}
import csw.util.config.StateVariable.CurrentState
import csw.util.config.UnitsOfMeasure._
import org.scalatest.{FunSpecLike, _}

import scala.concurrent.Await
import scala.concurrent.duration._

object FollowPositionTests {
  LocationService.initInterface()
  val system = ActorSystem("FollowPositionTests")

  //  val initialElevation = 90.0

  // Test subscriber actor for telemetry
  object TestSubscriber {
    def props(): Props = Props(new TestSubscriber())

    case object GetResults

    case class Results(msgs: Vector[SystemEvent])

  }

  class TestSubscriber() extends Actor with ActorLogging {

    import TestSubscriber._

    var msgs = Vector.empty[SystemEvent]

    def receive: Receive = {
      case event: SystemEvent =>
        msgs = msgs :+ event
        log.info(s"Received event: $event")

      case GetResults => sender() ! Results(msgs)
    }
  }
}

/**
 * These tests are about testing the calculated values for the trombone position when following.
 */
class FollowPositionTests extends TestKit(FollowPositionTests.system) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with BeforeAndAfterEach with LazyLogging {
  import system._
  import Algorithms._
  import TromboneAssembly._

  implicit val timeout = Timeout(10.seconds)

  // Get the event service by looking up the name with the location service.
  val eventService = Await.result(EventService(), timeout.duration)

  override protected def beforeEach(): Unit = {
    TestEnv.resetRedisServices()
  }

  override def beforeAll(): Unit = {
    TestEnv.createTromboneAssemblyConfig()
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val assemblyContext = AssemblyTestData.TestAssemblyContext
  val controlConfig = assemblyContext.controlConfig
  val calculationConfig = assemblyContext.calculationConfig
  import assemblyContext._

  def pos(position: Double): DoubleItem = stagePositionKey -> position withUnits stagePositionUnits

  // Used for creating followers
  val initialElevation = naElevation(assemblyContext.calculationConfig.defaultInitialElevation)
  def newFollower(tromboneControl: Option[ActorRef], publisher: Option[ActorRef]): TestActorRef[FollowActor] = {
    val props = FollowActor.props(assemblyContext, initialElevation, setNssInUse(false), tromboneControl, publisher)
    TestActorRef(props)
  }

  def newTestElPublisher(tromboneControl: Option[ActorRef]): TestActorRef[FollowActor] = {
    val testEventServiceProps = TrombonePublisher.props(assemblyContext, Some(eventService))
    val publisherActorRef = system.actorOf(testEventServiceProps)
    newFollower(tromboneControl, Some(publisherActorRef))
  }

  /**
   * Shortcut for creating zenith angle DoubleItem
   * @param angle angle in degrees
   * @return DoubleItem with value and degrees
   */
  def za(angle: Double): DoubleItem = zenithAngleKey -> angle withUnits degrees

  /**
   * Shortcut for creating focus error DoubleItem
   * @param error focus error in millimeters
   * @return DoubleItem with value and millimeters units
   */
  def fe(error: Double): DoubleItem = focusErrorKey -> error withUnits micrometers

  // Stop any actors created for a test to avoid conflict with other tests
  private def cleanup(hcd: Option[ActorRef], a: ActorRef*): Unit = {
    val monitor = TestProbe()
    a.foreach { actorRef =>
      monitor.watch(actorRef)
      system.stop(actorRef)
      monitor.expectTerminated(actorRef)
    }

    hcd.foreach { tromboneHCD =>
      monitor.watch(tromboneHCD)
      tromboneHCD ! HaltComponent
      monitor.expectTerminated(tromboneHCD)
    }
  }

  /**
   * Test Description: This test tests the CalculatorActor to a fake TromboneHCD to inspect the messages
   * provided by the CalculatorActor.  fakeTromboneEventSubscriber sends an UpdatedEventData event to
   * CalculatorActor, which after performing a calculation provides an HCDTromboneUpdate message to the
   * fakeTrombonePublisher. This tests input/output of CalculatorActor.
   */
  describe("connect output of calculator actor to the trombone publisher") {
    import AssemblyTestData._
    import Algorithms._

    it("tests total RD to encoder is within values") {
      // test1
      val maxEncoder = stagePositionToEncoder(controlConfig, rangeDistanceToStagePosition(maxTotalRD))
      val minEncoder = stagePositionToEncoder(controlConfig, rangeDistanceToStagePosition(minTotalRD))

      minEncoder shouldBe >(controlConfig.minEncoderLimit)
      maxEncoder shouldBe <(controlConfig.maxEncoderLimit)
    }

    // This isn't a great test, but a real system would know this transformation and test it
    // Using expected encoder values for test inputs
    it("encoder values should test") {
      // test2
      val result = encoderTestValues.map(_._1).map(f => stagePositionToEncoder(controlConfig, f))
      val answers = encoderTestValues.map(_._2)

      result should equal(answers)
    }

    /**
     * Test Description: This test uses a fake trombone event subscriber to send an UpdatedEventData message to the
     * followActor to see that it generates a RangeDistance message to send to the trombone hardware HCD
     */
    it("should allow one update") {
      // test3

      val fakeTromboneControl = TestProbe()

      val fakeTromboneEventSubscriber = TestProbe()

      // The following None ingores the events for AOESW from calculator
      val followActor = newFollower(Some(fakeTromboneControl.ref), None)

      // This should result in two messages being sent, one to each actor in the given order
      // zenith angle 0 = 94 km, fe 0 = 0 so total is default initial value
      fakeTromboneEventSubscriber.send(followActor, UpdatedEventData(za(0), fe(0), EventTime()))

      val msg = fakeTromboneControl.expectMsgClass(classOf[GoToStagePosition])
      msg should equal(GoToStagePosition(stagePositionKey -> calculationConfig.defaultInitialElevation withUnits stagePositionUnits))

      cleanup(None, followActor)
    }

    /**
     * Test Description: Similar to previous test, but with many values to test calculation and event flow.
     * Values are precalculated to it's not testing algorithms, it's testing the flow from input events to output
     */
    it("should create a proper set of HCDPositionUpdate messages") {
      // test4

      val fakeTromboneControl = TestProbe()

      // The following None ingores the events for AOESW from calculator
      val followActor = newFollower(Some(fakeTromboneControl.ref), None)

      val testFE = 10.0
      val testdata = newRangeAndElData(testFE)
      // These are the events that will be sent to the calculator to trigger position updates - range of ZA
      val updateMessages = testZenithAngles.map(f => UpdatedEventData(za(f), fe(testFE), EventTime()))

      // fakeTromboneEventSubscriber simulates the events recevied from the Event Service and sent to CalculatorActor
      // This should result in two messages being sent, one to each actor in the given order
      val fakeTromboneEventSubscriber = TestProbe()
      updateMessages.foreach(ev => fakeTromboneEventSubscriber.send(followActor, ev))

      // The following constructs the expected messages that contain the stage positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are proper
      // First keep focus error fixed at 10 um

      // This uses the new range values from above to create RangeDistance messages that are being delivered tothe trombonecontrol actor
      val msgsExpected = testdata.map(p => GoToStagePosition(stagePositionKey -> p._1 withUnits stagePositionUnits))

      // This collects the messages from the calculator setup above that are generated by the updateMessages.foreach above
      val msgs = fakeTromboneControl.receiveN(msgsExpected.size)

      // The two should be equal
      msgsExpected should equal(msgs)

      cleanup(None, followActor)
    }

    /**
     * Test Description: This adds the use of the Event Service. The test sends Zenith Angle updates from the
     * "TCS" through event service and generates trombone positions that are received by the fakeTromboneControl.
     */
    it("should create a proper published events from fake TCS flowing through Event Service to produce HCD encoder motion updates") {
      // test5

      // Fake actor that handles sending to HCD
      val fakeTromboneControl = TestProbe()

      val nssUse = setNssInUse(false)
      // Create the follow actor and give it the actor ref of the publisher for sending calculated events
      val followActor = system.actorOf(FollowActor.props(assemblyContext, initialElevation, nssUse, Some(fakeTromboneControl.ref), None))
      // create the subscriber that listens for events from TCS for zenith angle and focus error from RTC
      val tromboneEventSubscriber = system.actorOf(TromboneEventSubscriber.props(assemblyContext, nssUse, Some(followActor), eventService))

      // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
      val tcsRtc = eventService

      val testFE = 10.0
      // Publish a single focus error. This will generate a published event
      tcsRtc.publish(SystemEvent(focusErrorPrefix).add(fe(testFE)))

      // These are fake messages for the FollowActor that will be sent to simulate the TCS updating ZA
      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

      // This should result in the length of tcsEvents being published, which is 15
      tcsEvents.foreach(tcsRtc.publish)

      // This is to give actors time to run and subscriptions to register
      expectNoMsg(100.milli)

      // The following constructs the expected messages that contain the encoder positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are proper
      // First keep focus error fixed at 10 um
      val testdata = newRangeAndElData(testFE)
      // This uses the new range values from above to create RangeDistance messages that are being delivered tothe trombonecontrol actor
      val msgsExpected = testdata.map(p => GoToStagePosition(stagePositionKey -> p._1 withUnits stagePositionUnits))

      // Expect one message for the setting fe
      fakeTromboneControl.expectMsg(msgsExpected(0))
      // This collects the messages from the calculator setup above
      val msgs = fakeTromboneControl.receiveN(msgsExpected.size)

      // The two should be equal
      msgsExpected should equal(msgs)

      cleanup(None, followActor, tromboneEventSubscriber)
    }
  }

  /**
   * Test Description: This test sends one upate through FollowActor to a fakeTromboneHCD,
   * through the actual TromboneControl actor that converts stage position to encoder units and commands for HCD.
   */
  describe("check output of follow actor to the TromboneHCD through the trombone control sending one event") {
    import AssemblyTestData._

    it("should allow one update") {
      // test6
      import TromboneHCD._

      val fakeTromboneHCD = TestProbe()

      // Create the trombone control actor with the fake tromboneHCD
      val tromboneControl = system.actorOf(TromboneControl.props(assemblyContext))
      tromboneControl ! UpdateTromboneHCD(Some(fakeTromboneHCD.ref))

      // This is simulating the events that are received from RTC and TCS
      val fakeTromboneEventSubscriber = TestProbe()

      // The following None ingores the events for AOESW from calculator
      val followActor = newFollower(Some(tromboneControl), None)

      // This should result in one message being sent to the fakeTromboneHCD
      val testFE = 0.0
      val testZA = 0.0
      fakeTromboneEventSubscriber.send(followActor, UpdatedEventData(za(testZA), fe(testFE), EventTime()))

      val (totalRange, _) = focusZenithAngleToElevationAndRangeDistance(calculationConfig, calculationConfig.defaultInitialElevation, testFE, testZA)
      val expectedEnc = stagePositionToEncoder(controlConfig, rangeDistanceToStagePosition(totalRange))

      // Difference here is that fakeTromboneHCD receives a Submit commaand with an encoder value only
      val msg = fakeTromboneHCD.expectMsgClass(classOf[Submit])
      msg should equal(Submit(SetupConfig(axisMoveCK).add(positionKey -> expectedEnc withUnits positionUnits)))

      cleanup(None, tromboneControl, followActor)
    }

    /**
     * Test Description: This test creates a set of UpdatedEventData messages, sends them to FollowActor, which
     * passes them to the TromboneControl which creates the Submit messages for the HCD which are received by
     * a "fake" HCD and tested
     */
    it("should create a proper set of Submit messages for the fakeTromboneHCD") {
      // test7
      import TromboneHCD._

      val fakeTromboneHCD = TestProbe()

      // Create the trombone control actor with the fake tromboneHCD
      val tromboneControl = system.actorOf(TromboneControl.props(assemblyContext))
      tromboneControl ! UpdateTromboneHCD(Some(fakeTromboneHCD.ref))

      // The following None ingores the events for AOESW from calculator
      val followActor = newFollower(Some(tromboneControl), None)

      // These are the events that will be sent to the calculator to trigger position updates
      val testFE = -10.0
      val updateMessages = testZenithAngles.map(f => UpdatedEventData(za(f), fe(testFE), EventTime()))

      // This should result in two messages being sent, one to each actor in the given order
      val fakeTromboneSubscriber = TestProbe()
      updateMessages.foreach { ev =>
        fakeTromboneSubscriber.send(followActor, ev)
        // This allows the processed messages to interleave
        Thread.sleep(5)
      }

      // The following constructs the expected messages that contain the encoder positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are proper
      val calcData = calculatedTestData(calculationConfig, controlConfig, testFE)
      // This uses to two to create the expected messages from the calculatorActor
      val msgsExpected = calcData.map(p => Submit(positionSC(getenc(p))))

      // This collects the set of messages from the calculator setup above
      val msgs = fakeTromboneHCD.receiveN(msgsExpected.size)

      // The two should be equal
      msgsExpected should equal(msgs)

      cleanup(None, tromboneControl, followActor)
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
     * Test Description: This test creates a trombone HCD to receive events from the CalculatorActor.
     * The first part is about starting the HCD and waiting for it to reach the running lifecycle state where it can receive events
     * UpdatedEventData messages are constructed and sent to the CalculatorActor, which uses them to create position updates.
     * A fake TCS sends Zenith Angle SystemEvents to the CalculatorActor which receives them
     * processes them, and sends them to the HCD which replies with CurrentState updates.
     * The fake Assembly subscribes to CurrentState messages from the HCD to check for completion and other purposes.
     */
    it("should create a proper set of HCDPositionUpdate messages for zenith angle changes through to HCD instance") {
      // test8

      // startHCD creates an instance of the HCD
      val tromboneHCD = startHCD

      // A test probe to act as the assembly for receiving CurrentState updates
      val fakeAssembly = TestProbe()

      // Create the trombone control actor with the fake tromboneHCD
      val tromboneControl = system.actorOf(TromboneControl.props(assemblyContext))
      tromboneControl ! UpdateTromboneHCD(Some(tromboneHCD))

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      //info("Running")

      // This has HCD sending updates back to Assembly
      fakeAssembly.send(tromboneHCD, Subscribe)

      // Now we are ready to test
      // The following None ingores the events for AOESW from calculator
      val followActor = newFollower(Some(tromboneControl), None)

      // These are the events that will be sent to the calculator to trigger position updates
      val testFE = 10.0
      val updateMessages = testZenithAngles.map(f => UpdatedEventData(za(f), fe(testFE), EventTime()))

      // Fake TromboneSubscriber is acting as the actor that receives events from EventService
      // This should result in two messages being sent, one to each actor in the given order
      val fakeTromboneSubscriber = TestProbe()
      updateMessages.foreach { ev =>
        fakeTromboneSubscriber.send(followActor, ev)
        // This sleep is not required, but it makes the test more interesting by allowing the actions of the assembly and HCD to interleave
        Thread.sleep(10)
      }

      // The following constructs the expected messages that contain the encoder positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are proper
      // First keep focus error fixed at 10 mm

      val calcData = calculatedTestData(calculationConfig, controlConfig, testFE)
      val encExpected = calcData.map(getenc)
      //info(s"encEx: $encExpected")

      // This collects the messages from the follower setup above - it is difficult to predict what messages will arrive from the HCD because it depends on timing of inputs
      // So we only wait for messages to stop and inspect the last message that indicates we are in the right place
      val msgs = expectMoveMsgsWithDest(fakeAssembly, encExpected.last)
      msgs.last(positionKey).head should equal(encExpected.last)
      msgs.last(stateKey).head should equal(AXIS_IDLE)
      msgs.last(inLowLimitKey).head should equal(false)
      msgs.last(inHighLimitKey).head should equal(false)

      cleanup(Some(tromboneHCD), tromboneControl, followActor)
    }

    /**
     * Test Description: This test is similar to the previous test, but it simulates changes to the focus error
     * rather than changes to the zenith angle.
     * This test creates a trombone HCD to receive events from the CalculatorActor.
     * The first part is about starting the HCD and waiting for it to reach the runing lifecycle state where it can receive events
     * A fake RTC sends focus error events to the CalculatorActor which receives them, processes them, calculates new values,
     * and sends commands to the HCD which replies with CurrentState updates.
     * The fake Assembly subscribes to CurrentState messages from the HCD to check for completion
     */
    it("should create a proper set of HCDPositionUpdate messages for focus error changes through HCD") {
      // test9

      // startHCD creates an instance of the HCD
      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      //info("Running")

      // This has HCD sending updates back to Assembly
      fakeAssembly.send(tromboneHCD, Subscribe)

      // Create the trombone control actor with the fake tromboneHCD
      val tromboneControl = system.actorOf(TromboneControl.props(assemblyContext))
      tromboneControl ! UpdateTromboneHCD(Some(tromboneHCD))

      // The following ingores the events for AOESW from calculator
      val followActor = newFollower(Some(tromboneControl), None)

      val testZA = 20.0
      // These are the events that will be sent to the calculator to trigger position updates
      val updateMessages = testFocusErrors.map(f => UpdatedEventData(za(testZA), fe(f), EventTime()))

      // This should result in two messages being sent, one to each actor in the given order
      val fakeTromboneSubscriber = TestProbe()
      updateMessages.foreach { ev =>
        fakeTromboneSubscriber.send(followActor, ev)
        // This delay is not needed, but makes the timing more challenging for the HCD motions
        Thread.sleep(10)
      }

      // The following constructs the expected messages that contain the encoder positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are proper
      // First keep zenith angle at 10.0 and vary the focus error values and construct the total altitude
      // Look at final fe value
      //val lastFE = testFocusErrors.last

      // This produces a vector of ((fe, rangedistnace), enc) values
      val testdata = calculatedFETestData(calculationConfig, controlConfig, calculationConfig.defaultInitialElevation, testZA)
      val encExpected = getenc(testdata.last)
      //info("encEx: " + encExpected)

      // This collects the messages from the calculator setup above - it is difficult to predict what messages will arrive from the HCD because it depends on timing of inputs
      val msgs = expectMoveMsgsWithDest(fakeAssembly, encExpected)
      msgs.last(positionKey).head should equal(encExpected) // XXX TODO FIXME: test gets: 208 did not equal 343
      msgs.last(stateKey).head should equal(AXIS_IDLE)
      msgs.last(inLowLimitKey).head should equal(false)
      msgs.last(inHighLimitKey).head should equal(false)

      cleanup(Some(tromboneHCD), tromboneControl, followActor)
    }

    /**
     * Test Description: This test creates a trombone HCD to receive events from the FollowActor.
     * This tests the entire path with fake TCS sending events through Event Service, which are received by
     * TromboneSubscriber and then processed by FollowActor, and sends them to TromboneControl
     * which sends them to the TromboneHCD, which replies with StateUpdates.
     * The first part is about starting the HCD and waiting for it to reach the runing lifecycle state where it can receive events
     * The fake Assembly subscribes to CurrentState messages from the HCD to check for completion
     */
    it("creates fake TCS/RTC events with Event Service through calculator and back to HCD instance") {
      // test10

      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      //info("Running")

      // This has HCD sending updates back to this Assembly
      fakeAssembly.send(tromboneHCD, Subscribe)

      // Ignoring the messages for AO for the moment
      // Create the trombone control for receiving axis updates
      val tromboneControl = system.actorOf(TromboneControl.props(assemblyContext, Some(tromboneHCD)))

      // Create the follow actor and give it the actor ref of the publisher for sending calculated events
      // The following None ingores the events for AOESW from calculator
      val followActor = newFollower(Some(tromboneControl), None)

      // create the subscriber that receives events from TCS for zenith angle and focus error from RTC
      val tromboneEventSubscriber = system.actorOf(TromboneEventSubscriber.props(assemblyContext, setNssInUse(false), Some(followActor), eventService))

      fakeAssembly.expectNoMsg(200.milli)

      // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
      val tcsRtc = eventService

      val testFE = 10.0
      // Publish a single focus error. This will generate a published event
      tcsRtc.publish(SystemEvent(focusErrorPrefix).add(fe(testFE)))
      //Thread.sleep(50)

      // The following constructs the expected messages that contain the encoder positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are working properly.
      // First keep focus error fixed at 10 um
      val testdata = calculatedTestData(calculationConfig, controlConfig, testFE)

      // This uses the total elevation to get expected values for encoder position
      var encExpected = getenc(testdata.head)
      //info(s"encExpected1: $encExpected")

      // This gets the first set of CurrentState messages for moving to the FE 10 mm position
      var msgs = waitForMoveMsgs(fakeAssembly)
      msgs.last(positionKey).head should be(encExpected)
      msgs.last(stateKey).head should equal(AXIS_IDLE)
      msgs.last(inLowLimitKey).head should equal(false)
      msgs.last(inHighLimitKey).head should equal(false)

      // These are fake messages for the FollowActor that will be sent to simulate the TCS
      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

      // This should result in the length of tcsEvents being published, which is 15
      tcsEvents.foreach { f =>
        logger.info("Publish: " + f)
        tcsRtc.publish(f)
        // The following is not required, but is added to make the event timing more interesting
        // Varying this delay from 50 to 10 shows completion of moves and at 10 update of move positions before finishing
        Thread.sleep(15)
      }

      // This collects the messages from the calculator setup above - it is difficult to predict what messages will arrive from the HCD because it depends on timing of inputs
      encExpected = getenc(testdata.last)
      //info(s"encExpected2: $encExpected")
      msgs = expectMoveMsgsWithDest(fakeAssembly, encExpected)
      msgs.last(positionKey).head should equal(encExpected)
      msgs.last(stateKey).head should equal(AXIS_IDLE)
      msgs.last(inLowLimitKey).head should equal(false)
      msgs.last(inHighLimitKey).head should equal(false)

      cleanup(Some(tromboneHCD), tromboneEventSubscriber, tromboneControl, followActor)
    }

  }
}
