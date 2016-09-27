package csw.examples.vslice.assembly

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import csw.examples.vslice.assembly.FollowActor.UpdatedEventData
import csw.services.events.{EventService, EventServiceSettings, EventSubscriber}
import csw.util.config.DoubleItem
import csw.util.config.Events.{EventTime, SystemEvent}
import csw.util.config.UnitsOfMeasure.{degrees, kilometers, micrometers}
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, _}

import scala.concurrent.duration._

/**
 * TMT Source Code: 8/17/16.
 */
@DoNotDiscover
class EventPublishTests extends TestKit(ActorSystem("TromboneAssemblyCalulationTests")) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll {

  override def afterAll = {
    TestKit.shutdownActorSystem(system)
  }

  import Algorithms._
  import TromboneAssembly._

  implicit val execContext = system.dispatcher

  val testEventServiceSettings = EventServiceSettings("localhost", 7777)

  val calculationConfig = AlgorithmData.TestCalculationConfig

  def eventConnection: EventService = EventService(testEventServiceSettings)

  val initialElevation = 90.0

  def newCalculator(tromboneControl: Option[ActorRef], publisher: Option[ActorRef]): TestActorRef[FollowActor] = {
    val props = FollowActor.props(calculationConfig, tromboneControl, publisher)
    TestActorRef(props)
  }

  def newTestElPublisher(tromboneControl: Option[ActorRef]): TestActorRef[FollowActor] = {
    val testEventServiceProps = TrombonePublisher.props(Some(testEventServiceSettings))
    val publisherActorRef = system.actorOf(testEventServiceProps)
    newCalculator(tromboneControl, Some(publisherActorRef))
  }

  def za(angle: Double): DoubleItem = zenithAngleKey -> angle withUnits degrees

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

  describe("Create calculation actor with publisher and subscriber") {
    import TestSubscriber._

    it("should allow me to create actors without error") {

      val fakeTC = TestProbe()
      val ap = newTestElPublisher(Some(fakeTC.ref))

      fakeTC.expectNoMsg(100.milli)
      ap.underlyingActor.tromboneControl should be(Some(fakeTC.ref))
    }

    it("should allow publishing one event simulating event from fake TromboneEventSubscriber") {
      // Create a new publisher with no trombone position actor
      val ap = newTestElPublisher(None)

      val resultSubscriber = TestActorRef(TestSubscriber.props(aoSystemEventPrefix))

      val fakeTromboneEventSubscriber = TestProbe()

      // This should result in two messages being sent, one to each actor in the given order
      fakeTromboneEventSubscriber.send(ap, UpdatedEventData(za(0), fe(0), EventTime()))

      // This is to give actors time to run
      expectNoMsg(100.milli)

      // Ask our test subscriber for the published events
      resultSubscriber ! GetResults

      val result = expectMsgClass(classOf[Results])
      result.msgs.size should be(1)
      result.msgs should equal(Vector(SystemEvent(aoSystemEventPrefix).madd(naLayerElevationKey -> 94.0 withUnits kilometers, naLayerRangeDistanceKey -> 0.0 withUnits kilometers)))
    }

    it("should allow publishing several events with fake tromboneEventSubscriber") {
      import AlgorithmData._

      // Ignoring the messages for TrombonePosition (set to None)
      val ap = newTestElPublisher(None)

      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
      val resultSubscriber = TestActorRef(TestSubscriber.props(aoSystemEventPrefix))

      // These are fake messages for the CalculationActor from the EventSubscriber
      val events = elevationTestValues.map(_._1).map(f => UpdatedEventData(za(f), fe(10.0), EventTime()))

      // This should result in two messages being sent, one to each actor in the given order
      val fakeTromboneSubscriber = TestProbe()
      events.foreach(ev => fakeTromboneSubscriber.send(ap, ev))

      // This is to give actors time to run
      expectNoMsg(100.milli)

      resultSubscriber ! GetResults
      // Check the events received through the Event Service
      val result = expectMsgClass(classOf[Results])
      // Calculate expected events
      val rangeExpected = focusToRangeDistance(calculationConfig, 10.0)
      val elExpected = elevationTestValues.map(_._1).map(f => naLayerElevation(calculationConfig, calculationConfig.defaultInitialElevation, f)).map(f => SystemEvent(aoSystemEventPrefix).madd(naLayerElevationKey -> f withUnits kilometers, naLayerRangeDistanceKey -> rangeExpected withUnits kilometers))

      elExpected should equal(result.msgs)
    }

    it("should allow publishing several events through the event service") {
      import AlgorithmData._

      // Ignoring the messages for TrombonePosition
      // Create the trombone publisher for publishing SystemEvents to AOESW
      val publisherActorRef = system.actorOf(TrombonePublisher.props(Some(testEventServiceSettings)))
      // Create the calculator actor and give it the actor ref of the publisher for sending calculated events
      val calculatorActorRef = system.actorOf(FollowActor.props(calculationConfig, None, Some(publisherActorRef)))
      // create the subscriber that listens for events from TCS for zenith angle and focus error from RTC
      system.actorOf(TromboneEventSubscriber.props(Some(calculatorActorRef), Some(testEventServiceSettings)))

      // This creates a local subscriber to get all aoSystemEventPrefix SystemEvents published for testing
      val resultSubscriber = TestActorRef(TestSubscriber.props(aoSystemEventPrefix))

      // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
      val tcsRtc = EventService(testEventServiceSettings)

      // Publish a single focus error. This will generate a published event
      tcsRtc.publish(SystemEvent(focusErrorPrefix).add(fe(10.0)))

      // These are fake messages for the CalculationActor that will be sent to simulate the TCS
      val tcsEvents = elevationTestValues.map(_._1).map(f => SystemEvent(zConfigKey.prefix).add(za(f)))

      // This should result in the length of tcsEvents being published, which is 15
      tcsEvents.map(f => tcsRtc.publish(f))

      // This is to give actors time to run and subscriptions to register
      expectNoMsg(250.milli)

      // Ask the local subscriber for all the ao events published for testing
      resultSubscriber ! GetResults
      // Check the events received through the Event Service
      val result = expectMsgClass(classOf[Results])
      // Calculate expected events
      val rangeExpected = focusToRangeDistance(calculationConfig, 10.0)
      // First event is due to setting the focus error
      val elExpected1 = SystemEvent(aoSystemEventPrefix).madd(naLayerElevationKey -> naLayerElevation(calculationConfig, calculationConfig.defaultInitialElevation, 0.0) withUnits kilometers, naLayerRangeDistanceKey -> rangeExpected withUnits kilometers)
      // The others are due to going through the range of zenith angles
      val elExpected = elExpected1 +: elevationTestValues.map(_._1).map(f => naLayerElevation(calculationConfig, calculationConfig.defaultInitialElevation, f)).map(f => SystemEvent(aoSystemEventPrefix).madd(naLayerElevationKey -> f withUnits kilometers, naLayerRangeDistanceKey -> rangeExpected withUnits kilometers))

      // Here is the test for equality - total 16 messages
      elExpected should equal(result.msgs)
    }
  }
}