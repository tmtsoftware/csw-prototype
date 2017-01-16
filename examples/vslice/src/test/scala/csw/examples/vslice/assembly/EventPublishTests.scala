package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.TestEnv
import csw.examples.vslice.assembly.FollowActor.UpdatedEventData
import csw.services.events.{Event, EventService, TelemetryService}
import csw.services.loc.LocationService
import csw.services.loc.LocationService.ResolvedTcpLocation
import csw.util.config.Events.{EventTime, StatusEvent, SystemEvent}
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, _}

import scala.concurrent.Await
import scala.concurrent.duration._

object EventPublishTests {
  LocationService.initInterface()
  val system = ActorSystem("EventPublishTests")

  //  val initialElevation = 90.0

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
 * TMT Source Code: 8/17/16.
 */
class EventPublishTests extends TestKit(EventPublishTests.system) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with BeforeAndAfterEach with LazyLogging {

  import system._
  import EventPublishTests._

  val assemblyContext = AssemblyTestData.TestAssemblyContext
  import assemblyContext._

  implicit val timeout = Timeout(10.seconds)

  // Get the event and telemetry service by looking up the name with the location service.
  private val eventService = Await.result(EventService(), timeout.duration)

  private val telemetryService = Await.result(TelemetryService(), timeout.duration)

  override protected def beforeEach(): Unit = {
    TestEnv.resetRedisServices()
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  // Used for creating followers
  private val initialElevation = naElevation(assemblyContext.calculationConfig.defaultInitialElevation)

  // Stop any actors created for a test to avoid conflict with other tests
  private def cleanup(a: ActorRef*): Unit = {
    //    val monitor = TestProbe()
    a.foreach { actorRef =>
      //      monitor.watch(actorRef)
      system.stop(actorRef)
      //      monitor.expectTerminated(actorRef)
    }
  }

  // Publisher behaves the same whether nss is in use or not so always nssNotInUse
  def newTestFollower(tromboneControl: Option[ActorRef], publisher: Option[ActorRef]): ActorRef = {
    val props = FollowActor.props(assemblyContext, initialElevation, setNssInUse(false), tromboneControl, publisher)
    system.actorOf(props)
  }

  def newTestPublisher(eventService: Option[EventService], telemetryService: Option[TelemetryService]): ActorRef = {
    val testEventPublisherProps = TrombonePublisher.props(assemblyContext, eventService, telemetryService)
    system.actorOf(testEventPublisherProps)
  }

  describe("Create follow actor with publisher and subscriber") {
    import TestSubscriber._

    /**
     * Test Description: This test uses a "fakeSubscriber" which is simulating the subscription to TCS and RTC
     * events and ships UpdatedEventData messages to the FollowActor which calculates trombone positions and
     * other things and publishes events. This one checks for the events for AOESW in the form of
     * the System event for AOESW. One event for zenith angle 0 and focus error 0 is used for testing.
     * In this case range distance and elevation are the same, which is initial elevation in this case.
     */
    it("should allow publishing one event simulating event from fake TromboneEventSubscriber") {
      // test1
      // Create a new publisher with no trombone position actor
      val pub = newTestPublisher(Some(eventService), None)
      val fol = newTestFollower(None, Some(pub))

      val resultSubscriber = system.actorOf(TestSubscriber.props())
      eventService.subscribe(resultSubscriber, postLastEvents = false, aoSystemEventPrefix)
      expectNoMsg(1.second) // Wait for the connection

      val fakeTromboneEventSubscriber = TestProbe()

      // This should result in two messages being sent, one to each actor in the given order
      fakeTromboneEventSubscriber.send(fol, UpdatedEventData(za(0), fe(0), EventTime()))

      // This is to give actors time to run
      expectNoMsg(100.milli)

      // Ask our test subscriber for the published events
      resultSubscriber ! GetResults

      val result = expectMsgClass(classOf[Results])
      result.msgs.size should be(1)
      result.msgs should equal(Vector(SystemEvent(aoSystemEventPrefix).madd(naElevation(calculationConfig.defaultInitialElevation), rd(calculationConfig.defaultInitialElevation))))

      cleanup(pub, fol)
    }

    /**
     * Test Description: This test is similar to the last but a set of events are used that vary the zenith angle while holding
     * the focus error constant to see that multiple events are generated. The computed, expected values are computed with
     * AlgorithmData. If you change the algorithm you need to update the test helpers.
     */
    it("should allow publishing several events with fake tromboneEventSubscriber") {
      // test2
      import AssemblyTestData._

      // Ignoring the messages for TrombonePosition (set to None)
      val pub = newTestPublisher(Some(eventService), None)
      val fol = newTestFollower(None, Some(pub))

      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
      val resultSubscriber = system.actorOf(TestSubscriber.props())
      eventService.subscribe(resultSubscriber, postLastEvents = false, aoSystemEventPrefix)
      expectNoMsg(1.second) // Wait for the connection

      val testFE = 10.0

      // These are fake messages for the CalculationActor from the EventSubscriber
      val events = testZenithAngles.map(td => UpdatedEventData(za(td), fe(testFE), EventTime()))

      // This should result in two messages being sent, one to each actor in the given order
      val fakeTromboneSubscriber = TestProbe()
      events.foreach(ev => fakeTromboneSubscriber.send(fol, ev))

      // This is to give actors time to run
      expectNoMsg(100.milli)

      resultSubscriber ! GetResults
      // Check the events received through the Event Service
      val result = expectMsgClass(classOf[Results])

      // Calculate expected events
      val testResult = newRangeAndElData(testFE)

      val aoeswExpected = testResult.map(f => SystemEvent(aoSystemEventPrefix).madd(naElevationKey -> f._2 withUnits naElevationUnits, naRangeDistanceKey -> f._1 withUnits naRangeDistanceUnits))
      //info("aowes: " + aoeswExpected)

      aoeswExpected should equal(result.msgs)

      cleanup(pub, fol)
    }

    /**
     * Test Description: This takes it one step further and replaced the fakeTromboneSubscriber with the actual TromboneEventSubscriber
     * and uses the event service to publish events. The focus error of 10 is published then the set of data varying the zenith angle.
     * The TromboneEventSubscriber receives the events forwards them to the follow actor which then sends out updates.
     * Note that the EventSubscriber and FollowActor are separate so that the FollowActor can be tested as a standalone actor without the
     * event service as is done in this and the previous tests.
     */
    it("should allow publishing several events through the event service") {
      // test3
      import AssemblyTestData._
      // Ignoring the messages for TrombonePosition
      // Create the trombone publisher for publishing SystemEvents to AOESW
      val publisherActorRef = system.actorOf(TrombonePublisher.props(assemblyContext, Some(eventService), Some(telemetryService)))
      // Create the calculator actor and give it the actor ref of the publisher for sending calculated events
      val followActorRef = system.actorOf(FollowActor.props(assemblyContext, initialElevation, setNssInUse(false), None, Some(publisherActorRef)))
      // create the subscriber that listens for events from TCS for zenith angle and focus error from RTC
      val es = system.actorOf(TromboneEventSubscriber.props(assemblyContext, setNssInUse(false), Some(followActorRef), eventService))
      // This injects the event service location
      val evLocation = ResolvedTcpLocation(EventService.eventServiceConnection(), "localhost", 7777)
      es ! evLocation

      // This creates a local subscriber to get all aoSystemEventPrefix SystemEvents published for testing
      val resultSubscriber = system.actorOf(TestSubscriber.props())
      eventService.subscribe(resultSubscriber, postLastEvents = false, aoSystemEventPrefix)
      expectNoMsg(1.second) // Wait for the connection

      // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
      val tcsRtc = eventService

      val testFE = 10.0
      // Publish a single focus error. This will generate a published event
      tcsRtc.publish(SystemEvent(focusErrorPrefix).add(fe(testFE)))

      // These are fake messages for the FollowActor that will be sent to simulate the TCS
      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

      // This should result in the length of tcsEvents being published
      tcsEvents.foreach { f =>
        logger.info(s"Publish: $f")
        tcsRtc.publish(f)
      }

      // This is to give actors time to run and subscriptions to register
      expectNoMsg(500.milli)

      // Ask the local subscriber for all the ao events published for testing
      resultSubscriber ! GetResults
      // Check the events received through the Event Service
      val result = expectMsgClass(classOf[Results])
      info("result: " + result)
      info("resultsize: " + result.msgs.size)

      // Calculate expected events
      val testResult = newRangeAndElData(testFE)

      val firstOne = SystemEvent(aoSystemEventPrefix).madd(
        naElevationKey -> testResult.head._2 withUnits naElevationUnits,
        naRangeDistanceKey -> testResult.head._1 withUnits naRangeDistanceUnits
      )
      info("First: " + firstOne)

      val zaExpected = testResult.map(f => SystemEvent(aoSystemEventPrefix).madd(
        naElevationKey -> f._2 withUnits naElevationUnits,
        naRangeDistanceKey -> f._1 withUnits naRangeDistanceUnits
      ))
      val aoeswExpected = firstOne +: zaExpected
      info("aowes: " + aoeswExpected)
      info("aoesw size: " + aoeswExpected.size)

      // Here is the test for equality - total 16 messages
      aoeswExpected should equal(result.msgs)

      cleanup(publisherActorRef, followActorRef, es, resultSubscriber)
    }

    /**
     * Test Description: This test simulates some status data for the publisher.
     */
    it("should allow publishing TromboneState to publisher") {
      // test4
      import TromboneStateActor._

      val s1 = TromboneState(cmdItem(cmdUninitialized), moveItem(moveUnindexed), sodiumItem(false), nssItem(false))
      val s2 = TromboneState(cmdItem(cmdReady), moveItem(moveUnindexed), sodiumItem(false), nssItem(false))
      val s3 = TromboneState(cmdItem(cmdReady), moveItem(moveIndexing), sodiumItem(false), nssItem(false))
      val s4 = TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false))

      // Create a new publisher with no trombone position actor
      val tp = newTestPublisher(None, Some(telemetryService))

      val resultSubscriber = system.actorOf(TestSubscriber.props())
      telemetryService.subscribe(resultSubscriber, postLastEvents = false, tromboneStateStatusEventPrefix)
      expectNoMsg(1.second) // Wait for the connection

      val fakeStateProducer = TestProbe()

      def makeStatusEvent(ts: TromboneState): StatusEvent = StatusEvent(tromboneStateStatusEventPrefix).madd(ts.cmd, ts.move, ts.sodiumLayer, ts.nss)

      // This should result in two messages being sent, one to each actor in the given order
      fakeStateProducer.send(tp, s1)
      fakeStateProducer.send(tp, s2)
      fakeStateProducer.send(tp, s3)
      fakeStateProducer.send(tp, s4)

      // This is to give actors time to run
      expectNoMsg(1.seconds)

      // Ask our test subscriber for the published events
      resultSubscriber ! GetResults

      val result = expectMsgClass(classOf[Results])
      result.msgs.size should be(4)
      result.msgs should equal(Seq(s1, s2, s3, s4).map(makeStatusEvent))

      cleanup(tp, resultSubscriber)
    }
  }

}