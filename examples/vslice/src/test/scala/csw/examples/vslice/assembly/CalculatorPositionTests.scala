package csw.examples.vslice.assembly

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.assembly.FollowActor.UpdatedEventData
import csw.examples.vslice.hcd.TromboneHCD
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.ccs.HcdController._
import csw.services.events.{EventService, EventServiceSettings, EventSubscriber}
import csw.services.loc.ConnectionType.AkkaType
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
import csw.services.pkg.Supervisor3
import csw.services.pkg.Supervisor3.{LifecycleInitialized, LifecycleRunning}
import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
import csw.util.config.Configurations.SetupConfig
import csw.util.config.DoubleItem
import csw.util.config.Events.{EventTime, SystemEvent}
import csw.util.config.StateVariable.CurrentState
import csw.util.config.UnitsOfMeasure._
import org.scalatest.{FunSpecLike, _}

import scala.concurrent.duration._

/**
 * These tests are about testing the calculated values for the trombone position when following.
 */
@DoNotDiscover
class CalculatorPositionTests extends TestKit(ActorSystem("TromboneAssemblyCalulationTests")) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {
  import Algorithms._
  import AlgorithmData._
  import TromboneAssembly._

  implicit val execContext = system.dispatcher

  val testEventServiceSettings = EventServiceSettings("localhost", 7777)

  val controlConfig = TestControlConfig
  val calculationConfig = TestCalculationConfig

  val initialElevation = 90.0

  def pos(position: Double): DoubleItem = stagePositionKey -> position withUnits stagePositionUnits

  def newCalculator(tromboneControl: Option[ActorRef], publisher: Option[ActorRef]): TestActorRef[FollowActor] = {
    val props = FollowActor.props(calculationConfig, tromboneControl, publisher)
    TestActorRef(props)
  }

  def newTestElPublisher(tromboneControl: Option[ActorRef]): TestActorRef[FollowActor] = {
    val testEventServiceProps = TrombonePublisher.props(Some(testEventServiceSettings))
    val publisherActorRef = system.actorOf(testEventServiceProps)
    newCalculator(tromboneControl, Some(publisherActorRef))
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

  // Test subscriber actor for telemetry
  object TestSubscriber {
    def props(prefix: String): Props = Props(new TestSubscriber(prefix))

    case object GetResults

    case class Results(msgs: Vector[SystemEvent])

  }

  class TestSubscriber(prefix: String) extends EventSubscriber(Some(testEventServiceSettings)) {

    import TestSubscriber._

    var msgs = Vector.empty[SystemEvent]

    subscribe(prefix)
    info(s"Test subscriber for prefix: $prefix")

    def receive: Receive = {
      case event: SystemEvent =>
        msgs = msgs :+ event
        log.info(s"Received event: $event")

      case GetResults => sender() ! Results(msgs)
    }
  }

  /**
   * Test Description: This test tests the CalculatorActor to a fake TromboneHCD to inspect the messages
   * provided by the CalculatorActor.  fakeTromboneEventSubscriber sends an UpdatedEventData event to
   * CalculatorActor, which after performing a calculation provides an HCDTromboneUpdate message to the
   * fakeTrombonePublisher. This tests input/output of CalculatorActor.
   */
  describe("connect output of calculator actor to the trombone publisher") {
    import AlgorithmData._
    import TromboneControl._

    /*
      it("tests total to encoder within values") {
        val maxEncoder = rangeDistanceTransform(controlConfig, maxTotal)

        val minEncoder = rangeDistanceTransform(controlConfig, minTotal)
      }
*/

    // This isn't a great test, but a real system would know this transformation and test it
    it("encoder values should test") {
      val result = encoderTestValues.map(_._1).map(f => rangeDistanceTransform(controlConfig, pos(f)))
      val answers = encoderTestValues.map(_._2)

      result should equal(answers)
    }

    it("should allow one update") {

      val fakeTromboneControl = TestProbe()

      val fakeTromboneEventSubscriber = TestProbe()

      // The following None ingores the events for AOESW from calculator
      val calculatorActor = newCalculator(Some(fakeTromboneControl.ref), None)

      // This should result in two messages being sent, one to each actor in the given order
      // zenith angle 0 = 94 km, fe 0 = 0 so total is 94, which maps to encoder 720
      fakeTromboneEventSubscriber.send(calculatorActor, UpdatedEventData(za(0), fe(0), EventTime()))

      // This is to give actors time to run
      expectNoMsg(20.milli)

      val msg = fakeTromboneControl.expectMsgClass(classOf[RangeDistance])
      msg should equal(RangeDistance(stagePositionKey -> 94.0 withUnits stagePositionUnits))
    }

    /**
     * Test Description: Similar to previous test, but with many values to test calculation and event flow.
     */
    it("should create a proper set of HCDPositionUpdate messages") {

      val fakeTromboneControl = TestProbe()

      // The following None ingores the events for AOESW from calculator
      val calculatorActor = newCalculator(Some(fakeTromboneControl.ref), None)

      // These are the events that will be sent to the calculator to trigger position updates - range of ZA
      val updateMessages = elevationTestValues.map(_._1).map(f => UpdatedEventData(za(f), fe(10.0), EventTime()))

      // fakeTromboneEventSubscriber simulates the events recevied from the Event Service and sent to CalculatorActor
      // This should result in two messages being sent, one to each actor in the given order
      val fakeTromboneEventSubscriber = TestProbe()
      updateMessages.foreach(ev => fakeTromboneEventSubscriber.send(calculatorActor, ev))

      // The following constructs the expected messages that contain the encoder positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are proper
      // First keep focus error fixed at 10 mm
      val rangeExpected = focusToRangeDistance(calculationConfig, 10.0)
      // Create a set of expected elevation values so we can combine to get total elevation
      val totalElExpected = elevationTestValues.map(_._1).map(f => naLayerElevation(calculationConfig, calculationConfig.defaultInitialElevation, f)).map(el => el + rangeExpected)
      // This uses the total elevation to get expected values for encoder position
      val encExpected = totalElExpected.map(f => TromboneControl.rangeDistanceTransform(controlConfig, pos(f)))
      // This uses to two to create the expected messages from the calculatorActor
      val msgsExpected = totalElExpected.zip(encExpected).map(p => RangeDistance(stagePositionKey -> p._1 withUnits stagePositionUnits))

      // This collects the messages from the calculator setup above that are generated by the updateMessages.foreach above
      val msgs = fakeTromboneControl.receiveN(elevationTestValues.size)

      // The two should be equal
      msgsExpected should equal(msgs)
    }

    /**
     * Test Description: This adds the use of the Event Service. The test sends Zenith Angle updates from the
     * "TCS" through event service and generates trombone positions that are received by the fakeTromboneControl.
     */
    it("should create a proper published events from fake TCS flowing through Event Service to produce HCD encoder motion updates") {

      // Fake actor that handles sending to HCD
      val fakeTromboneControl = TestProbe()

      // Create the calculator actor and give it the actor ref of the publisher for sending calculated events
      val calculatorActor = system.actorOf(FollowActor.props(calculationConfig, Some(fakeTromboneControl.ref), None))
      // create the subscriber that listens for events from TCS for zenith angle and focus error from RTC
      system.actorOf(TromboneEventSubscriber.props(Some(calculatorActor), Some(testEventServiceSettings)))

      // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
      val tcsRtc = EventService(testEventServiceSettings)

      // Publish a single focus error. This will generate a published event
      tcsRtc.publish(SystemEvent(focusErrorPrefix).add(fe(10.0)))

      // These are fake messages for the CalculationActor that will be sent to simulate the TCS updating ZA
      val tcsEvents = elevationTestValues.map(_._1).map(f => SystemEvent(zConfigKey.prefix).add(za(f)))

      // This should result in the length of tcsEvents being published, which is 15
      tcsEvents.map(f => tcsRtc.publish(f))

      // This is to give actors time to run and subscriptions to register
      expectNoMsg(100.milli)

      // The following constructs the expected messages that contain the encoder positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are proper
      // First keep focus error fixed at 10 mm
      val rangeExpected = focusToRangeDistance(calculationConfig, 10.0)
      // Create a set of expected elevation values so we can combine to get total elevation
      val totalElExpected = elevationTestValues.map(_._1).map(f => naLayerElevation(calculationConfig, calculationConfig.defaultInitialElevation, f)).map(el => el + rangeExpected)
      // This uses the total elevation to get expected values for encoder position
      val encExpected = totalElExpected.map(f => TromboneControl.rangeDistanceTransform(TestControlConfig, pos(f)))
      // This uses to two to create the expected messages from the calculatorActor
      val msgsExpected = totalElExpected.zip(encExpected).map(p => RangeDistance(stagePositionKey -> p._1 withUnits stagePositionUnits))

      // Expect one message for the setting fe
      fakeTromboneControl.expectMsg(msgsExpected(0))
      // This collects the messages from the calculator setup above
      val msgs = fakeTromboneControl.receiveN(elevationTestValues.size)

      // The two should be equal
      msgsExpected should equal(msgs)
    }
  }

  /**
   * Test Description: This test sends one upate through CalculatorActor to a fakeTromboneHCD,
   * through the actual TromboneControl actor.
   */
  describe("check output of calculator actor to the TromboneHCD") {
    import AlgorithmData._

    it("should allow one update") {
      import TromboneHCD._

      val fakeTromboneHCD = TestProbe()

      // Create the trombone control actor with the fake tromboneHCD
      val tromboneControl = system.actorOf(TromboneControl.props(controlConfig, Some(fakeTromboneHCD.ref)))

      // This is simulating the events that are received from RTC and TCS
      val fakeTromboneEventSubscriber = TestProbe()

      // The following None ingores the events for AOESW from calculator
      val calculatorActor = newCalculator(Some(tromboneControl), None)

      // This should result in two messages being sent, one to each actor in the given order
      // zenith angle 0 = 94 km, fe 0 = 0 so total is 94, which maps to encoder 720
      fakeTromboneEventSubscriber.send(calculatorActor, UpdatedEventData(za(0), fe(0), EventTime()))

      // This is to give actors time to run
      //expectNoMsg(100.milli)

      // Difference here is that fakeTromboneHCD receives a Submit commaand with an encoder value only
      val msg = fakeTromboneHCD.expectMsgClass(classOf[Submit])
      msg should equal(Submit(SetupConfig(axisMoveCK).add(positionKey -> 720 withUnits encoder)))
    }

    /**
     * Test Description:
     */
    it("should create a proper set of Submit messages for the fakeTromboneHCD") {
      import TromboneHCD._

      val fakeTromboneHCD = TestProbe()

      // Create the trombone control actor with the fake tromboneHCD
      val tromboneControl = system.actorOf(TromboneControl.props(controlConfig, Some(fakeTromboneHCD.ref)))

      // The following None ingores the events for AOESW from calculator
      val calculatorActor = newCalculator(Some(tromboneControl), None)

      // These are the events that will be sent to the calculator to trigger position updates
      val updateMessages = elevationTestValues.map(_._1).map(f => UpdatedEventData(za(f), fe(10.0), EventTime()))

      // This should result in two messages being sent, one to each actor in the given order
      val fakeTromboneSubscriber = TestProbe()
      updateMessages.foreach(ev => fakeTromboneSubscriber.send(calculatorActor, ev))

      // The following constructs the expected messages that contain the encoder positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are proper
      // First keep focus error fixed at 10 mm
      val rangeExpected = focusToRangeDistance(calculationConfig, 10.0)
      // Create a set of expected elevation values so we can combine to get total elevation
      val totalElExpected = elevationTestValues.map(_._1).map(f => naLayerElevation(calculationConfig, calculationConfig.defaultInitialElevation, f)).map(el => el + rangeExpected)
      // This uses the total elevation to get expected values for encoder position
      val encExpected = totalElExpected.map(f => TromboneControl.rangeDistanceTransform(TestControlConfig, pos(f)))
      // This uses to two to create the expected messages from the calculatorActor
      val msgsExpected = encExpected.map(p => Submit(positionSC(p)))

      // This collects the set of messages from the calculator setup above
      val msgs = fakeTromboneHCD.receiveN(elevationTestValues.size)

      // The two should be equal
      msgsExpected should equal(msgs)
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

      Supervisor3(testInfo)
    }

    /**
     * This will accept CurrentState messages until a state value is AXIS_IDLE
     * This is useful when you know there is one move and it will end without being updated
     * @param tp TestProbe that is the destination of the CurrentState messages
     * @return a Sequence of CurrentState messages
     */
    def waitForMoveMsgs(tp: TestProbe): Seq[CurrentState] = {
      val msgs = tp.receiveWhile(5.seconds) {
        case m @ CurrentState(ck, items) if ck.prefix.contains(TromboneHCD.axisStatePrefix) && m(TromboneHCD.stateKey).head == TromboneHCD.AXIS_MOVING => m
        // This is present to pick up the first status message
        case st @ CurrentState(ck, items) if ck.prefix.equals(TromboneHCD.axisStatsPrefix) => st
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
        case m @ CurrentState(ck, items) if ck.prefix.contains(TromboneHCD.axisStatePrefix) && m(TromboneHCD.positionKey).head != dest => m
        // This is present to pick up the first status message
        case st @ CurrentState(ck, items) if ck.prefix.equals(TromboneHCD.axisStatsPrefix) => st
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

      // startHCD creates an instance of the HCD
      val tromboneHCD = startHCD

      // A test probe to act as the assembly for receiving CurrentState updates
      val fakeAssembly = TestProbe()

      // Create the trombone control actor with the fake tromboneHCD
      val tromboneControl = system.actorOf(TromboneControl.props(controlConfig, Some(tromboneHCD)))

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
      val calculatorActor = newCalculator(Some(tromboneControl), None)

      // These are the events that will be sent to the calculator to trigger position updates
      val updateMessages = elevationTestValues.map(_._1).map(f => UpdatedEventData(za(f), fe(10.0), EventTime()))

      // Fake TromboneSubscriber is acting as the actor that receives events from EventService
      // This should result in two messages being sent, one to each actor in the given order
      val fakeTromboneSubscriber = TestProbe()
      updateMessages.foreach(ev => fakeTromboneSubscriber.send(calculatorActor, ev))

      // The following constructs the expected messages that contain the encoder positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are proper
      // First keep focus error fixed at 10 mm
      val rangeExpected = focusToRangeDistance(calculationConfig, 10.0)
      // Create a set of expected elevation values so we can combine to get total elevation
      val totalElExpected = elevationTestValues.map(_._1).map(f => naLayerElevation(calculationConfig, calculationConfig.defaultInitialElevation, f)).map(el => el + rangeExpected)
      // This uses the total elevation to get expected values for encoder position
      val encExpected = totalElExpected.map(f => TromboneControl.rangeDistanceTransform(TestControlConfig, pos(f)))
      //println("encEx: " + encExpected)

      // This collects the messages from the calculator setup above - it is difficult to predict what messages will arrive from the HCD because it depends on timing of inputs
      // So we only wait for messages to stop and inspect the last message that indicates we are in the right place
      val msgs = waitForMoveMsgs(fakeAssembly)
      msgs.last(positionKey).head should equal(encExpected.last)
      msgs.last(stateKey).head should equal(AXIS_IDLE)
      msgs.last(inLowLimitKey).head should equal(false)
      msgs.last(inHighLimitKey).head should equal(false)
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
      val tromboneControl = system.actorOf(TromboneControl.props(controlConfig, Some(tromboneHCD)))

      // The following ingores the events for AOESW from calculator
      val calculatorActor = newCalculator(Some(tromboneControl), None)

      val fevalues = -10 to 10 by 1
      val rangeExpected = fevalues.map(f => focusToRangeDistance(calculationConfig, f))

      // These are the events that will be sent to the calculator to trigger position updates
      val updateMessages = rangeExpected.map(f => UpdatedEventData(za(0.0), fe(f), EventTime()))

      // This should result in two messages being sent, one to each actor in the given order
      val fakeTromboneSubscriber = TestProbe()
      updateMessages.foreach { ev =>
        fakeTromboneSubscriber.send(calculatorActor, ev)
        // This delay is not needed, but makes the timing more challenging for the HCD motions
        Thread.sleep(40)
      }

      // The following constructs the expected messages that contain the encoder positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are proper
      // First keep zenith angle at 0.0 and vary the focus error values and construct the total altitude

      val baseAltitude = naLayerElevation(calculationConfig, calculationConfig.defaultInitialElevation, 0.0)
      // Create a set of expected elevation values so we can combine to get total elevation
      val totalElExpected = rangeExpected.map(f => focusToRangeDistance(calculationConfig, f)).map(f => baseAltitude + f)
      // This uses the total elevation to get expected values for encoder position
      val encExpected = totalElExpected.map(f => TromboneControl.rangeDistanceTransform(TestControlConfig, pos(f)))
      println("encEx: " + encExpected)

      // This collects the messages from the calculator setup above - it is difficult to predict what messages will arrive from the HCD because it depends on timing of inputs
      val msgs = expectMoveMsgsWithDest(fakeAssembly, encExpected.last)
      msgs.last(positionKey).head should equal(encExpected.last)
      msgs.last(stateKey).head should equal(AXIS_IDLE)
      msgs.last(inLowLimitKey).head should equal(false)
      msgs.last(inHighLimitKey).head should equal(false)
    }

    /**
     * Test Description: This test creates a trombone HCD to receive events from the CalculatorActor.
     * This tests the entire path with fake TCS sending events through Event Service, which are received by
     * TromboneSubscriber and then processed by CalculatorActor, and sends them to TromboneControl
     * which sends them to the TromboneHCD, which replies with StateUpdates.
     * The first part is about starting the HCD and waiting for it to reach the runing lifecycle state where it can receive events
     * The fake Assembly subscribes to CurrentState messages from the HCD to check for completion
     */
    it("creates fake TCS/RTC events with Event Service through calculator and back to HCD instance") {

      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      //info("Running")

      // This has HCD sending updates back to this Assembly
      fakeAssembly.send(tromboneHCD, Subscribe)

      // Ignoring the messages for AO for the moment
      // Create the trombone publisher for publishing SystemEvents to AOESW
      val tromboneControl = system.actorOf(TromboneControl.props(controlConfig, Some(tromboneHCD)))

      // Create the calculator actor and give it the actor ref of the publisher for sending calculated events
      // The following None ingores the events for AOESW from calculator
      val calculatorActor = newCalculator(Some(tromboneControl), None)

      // create the subscriber that receives events from TCS for zenith angle and focus error from RTC
      system.actorOf(TromboneEventSubscriber.props(Some(calculatorActor), Some(testEventServiceSettings)))

      // This eventService is used to simulate the TCS and RTC publishing zenith angle and focus error
      val tcsRtc = EventService(testEventServiceSettings)

      // Publish a single focus error. This will generate a published event
      tcsRtc.publish(SystemEvent(focusErrorPrefix).add(fe(10.0)))
      Thread.sleep(50)

      // The following constructs the expected messages that contain the encoder positions
      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are working properly.
      // First keep focus error fixed at 10 mm
      val rangeExpected = focusToRangeDistance(calculationConfig, 10.0)
      // Create a set of expected elevation values so we can combine to get total elevation
      val totalElExpected = elevationTestValues.map(_._1).map(f => naLayerElevation(calculationConfig, calculationConfig.defaultInitialElevation, f)).map(el => el + rangeExpected)
      // This uses the total elevation to get expected values for encoder position
      val encExpected = totalElExpected.map(f => TromboneControl.rangeDistanceTransform(TestControlConfig, pos(f)))

      // This gets the first set of CurrentState messages for moving to the FE 10 mm position
      var msgs = waitForMoveMsgs(fakeAssembly)
      msgs.last(positionKey).head should be(encExpected.head)
      msgs.last(stateKey).head should equal(AXIS_IDLE)
      msgs.last(inLowLimitKey).head should equal(false)
      msgs.last(inHighLimitKey).head should equal(false)

      // These are fake messages for the CalculationActor that will be sent to simulate the TCS
      val tcsEvents = elevationTestValues.map(_._1).map(f => SystemEvent(zConfigKey.prefix).add(za(f)))

      // This should result in the length of tcsEvents being published, which is 15
      tcsEvents.foreach { f =>
        logger.info("Publish: " + f)
        tcsRtc.publish(f)
        // The following is not required, but is added to make the event timing more interesting
        // Varying this delay from 50 to 10 shows completion of moves and at 10 update of move positions before finishing
        Thread.sleep(15)
      }

      // This collects the messages from the calculator setup above - it is difficult to predict what messages will arrive from the HCD because it depends on timing of inputs
      msgs = expectMoveMsgsWithDest(fakeAssembly, encExpected.last)
      msgs.last(positionKey).head should equal(encExpected.last)
      msgs.last(stateKey).head should equal(AXIS_IDLE)
      msgs.last(inLowLimitKey).head should equal(false)
      msgs.last(inHighLimitKey).head should equal(false)
    }

  }
}
