package csw.examples.vslice.assembly

import java.net.URI

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.TestEnv
import csw.examples.vslice.assembly.DiagPublisher.{DiagnosticState, OperationsState}
import csw.examples.vslice.assembly.TrombonePublisher.{AxisStateUpdate, AxisStatsUpdate}
import csw.examples.vslice.hcd.TromboneHCD
import csw.examples.vslice.hcd.TromboneHCD.{GetAxisStats, GetAxisUpdate}
import csw.services.events.TelemetryService
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.LocationService
import csw.services.loc.LocationService.{Location, ResolvedAkkaLocation, Unresolved}
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
import csw.services.pkg.Supervisor
import csw.services.pkg.Supervisor.{HaltComponent, LifecycleInitialized, LifecycleRunning}
import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
import csw.util.config.Events.{StatusEvent, SystemEvent}
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, _}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Diag Pubisher Tests
 */
object DiagPublisherTests {
  LocationService.initInterface()
  val system = ActorSystem("DiagPublisherSystem")

  // Test subscriber actor for telemetry and system events
  object TestSubscriber {
    def props(): Props = Props(classOf[TestSubscriber])

    case object GetSysResults

    case object GetStatusResults

    case class SysResults(msgs: Vector[SystemEvent])

    case class StatusResults(msgs: Vector[StatusEvent])

  }

  /**
   * Test event service client, subscribes to some event
   */
  class TestSubscriber extends Actor with ActorLogging {

    import TestSubscriber._

    var sysmsgs = Vector.empty[SystemEvent]
    var statmsgs = Vector.empty[StatusEvent]

    def receive: Receive = {
      case event: SystemEvent =>
        sysmsgs = sysmsgs :+ event
        log.debug(s"Received system event: $event")
      case event: StatusEvent =>
        statmsgs = statmsgs :+ event
        log.debug(s"Received status event: $event")

      case GetSysResults    => sender() ! SysResults(sysmsgs)
      case GetStatusResults => sender() ! StatusResults(statmsgs)
    }
  }
}

class DiagPublisherTests extends TestKit(DiagPublisherTests.system) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with BeforeAndAfterEach with LazyLogging {

  import DiagPublisherTests._

  // Get the telemetry service by looking up the name with the location service.
  private implicit val timeout = Timeout(10.seconds)
  private val telemetryService: TelemetryService = Await.result(TelemetryService(), timeout.duration)

  private val assemblyContext = AssemblyTestData.TestAssemblyContext

  import assemblyContext._

  // This is possible since trombone HCD has only one HCD
  val tromboneHCDConnection: AkkaConnection = assemblyContext.info.connections.head.asInstanceOf[AkkaConnection]

  override protected def beforeEach(): Unit = {
    TestEnv.resetRedisServices()
  }

  override def beforeAll(): Unit = {
    TestEnv.createTromboneAssemblyConfig()
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def startHCD: ActorRef = {
    val testInfo = HcdInfo(
      TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Set(AkkaType), 1.second
    )

    Supervisor(testInfo)
  }

  def newDiagPublisher(currentStateReceiver: ActorRef, tromboneHCD: Option[ActorRef], eventPublisher: Option[ActorRef]): TestActorRef[DiagPublisher] = {
    val props = DiagPublisher.props(assemblyContext, tromboneHCD, eventPublisher)
    TestActorRef[DiagPublisher](props)
  }

  // Stop any actors created for a test to avoid conflict with other tests
  private def cleanup(tromboneHCD: ActorRef, a: ActorRef*): Unit = {
    val monitor = TestProbe()
    a.foreach { actorRef =>
      monitor.watch(actorRef)
      system.stop(actorRef)
      monitor.expectTerminated(actorRef)
    }

    monitor.watch(tromboneHCD)
    tromboneHCD ! HaltComponent
    monitor.expectTerminated(tromboneHCD)
  }

  describe("basic diag tests") {
    /**
     * Test Description: Stimulate DiagPublisher with CurrentState events to demonstrate diag publishing in operations state.
     */
    it("should see one type of messages sent to publisher in operations mode") {
      // test1
      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

      val fakePublisher = TestProbe()

      // Use HCD as currentStateReceiver
      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(fakePublisher.ref))

      // Sending GetAxisStats and GetAxisUpdate to tromboneHCD simulates generation of messages during motion -- at least for AxisUpdate
      // Operations mode ignores AxisStats messages
      tromboneHCD ! GetAxisStats
      // Check that nothing is happening here
      fakePublisher.expectNoMsg(20.milli)

      // Skip count is 5 so should get one message right away and then none for 4 more - just check for one
      tromboneHCD ! GetAxisUpdate
      fakePublisher.expectMsgClass(classOf[AxisStateUpdate])

      cleanup(tromboneHCD, dp)
    }

    /**
     * Test Description: Stimulate DiagPublisher with CurrentState events to demonstrate diag publishing in operations mode.
     * This test shows that in operations state the skip count is 5
     */
    it("should see one state message sent to publisher in operations mode for every skipCount messages") {
      // test2
      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

      val fakePublisher = TestProbe()

      // Use HCD as currentStateReceiver
      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(fakePublisher.ref))

      // Skip count is 5 so should get one message right away and then none for 4 more
      tromboneHCD ! GetAxisUpdate
      var msg = fakePublisher.expectMsgClass(classOf[AxisStateUpdate])
      tromboneHCD ! GetAxisUpdate
      fakePublisher.expectNoMsg(20.milli)
      tromboneHCD ! GetAxisUpdate
      fakePublisher.expectNoMsg(20.milli)
      tromboneHCD ! GetAxisUpdate
      fakePublisher.expectNoMsg(20.milli)
      tromboneHCD ! GetAxisUpdate
      fakePublisher.expectNoMsg(20.milli)
      tromboneHCD ! GetAxisUpdate
      msg = fakePublisher.expectMsgClass(classOf[AxisStateUpdate])

      cleanup(tromboneHCD, dp)
    }

    /**
     * Test Description: Stimulate DiagPublisher with CurrentState events to demonstrate diag publishing in diagnostic mode.
     * This test shows that in diagnostic state the skip count is 2
     */
    it("should see one state message sent to publisher in diagnostics mode for every update message") {
      // test3
      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

      val fakePublisher = TestProbe()

      // Use HCD as currentStateReceiver
      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(fakePublisher.ref))

      dp ! DiagnosticState

      // Skip count is 2 so should get a message for every other event
      tromboneHCD ! GetAxisUpdate
      fakePublisher.expectMsgClass(classOf[AxisStateUpdate])
      tromboneHCD ! GetAxisUpdate
      fakePublisher.expectNoMsg(20.milli)
      tromboneHCD ! GetAxisUpdate
      fakePublisher.expectMsgClass(classOf[AxisStateUpdate])
      tromboneHCD ! GetAxisUpdate
      fakePublisher.expectNoMsg(20.milli)
      tromboneHCD ! GetAxisUpdate
      fakePublisher.expectMsgClass(classOf[AxisStateUpdate])
      tromboneHCD ! GetAxisUpdate
      fakePublisher.expectNoMsg(20.milli)

      cleanup(tromboneHCD, dp)
    }

    /**
     * Test Description: This test shows that in diagnostic state there is also a stats event once/second.
     * This test waits for one message demonstrating that stats events are published
     */
    it("should see one stats message sent to publisher in diagnostics mode every second (current spec)") {
      // test4
      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

      val fakePublisher = TestProbe()

      // Use HCD as currentStateReceiver
      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(fakePublisher.ref))

      dp ! DiagnosticState

      // Because timeout is 3 seconds, we get the one stats event after 1 second
      fakePublisher.expectMsgClass(classOf[AxisStatsUpdate])

      cleanup(tromboneHCD, dp)
    }

    /**
     * Test Description: Demonstrate that stats events are published once/second by waiting for 3 seconds
     * The end of the test demonstrates that the stats events are turned off properl in operations state
     */
    it("should generate several timed events in diagnostic mode") {
      // test5
      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

      val fakeEventPublisher = TestProbe()

      // Use HCD as currentStateReceiver
      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(fakeEventPublisher.ref))
      dp ! DiagnosticState

      // Wait for a bit over 3 seconds
      val msgs = fakeEventPublisher.receiveWhile(3200.milli) {
        case asu: AxisStatsUpdate => asu
      }
      msgs.size shouldBe 3

      // Now turn them off
      dp ! OperationsState
      // A delay to see that no messages arrive after one second to ensure timer is off
      fakeEventPublisher.expectNoMsg(1200.milli)

      cleanup(tromboneHCD, dp)
    }

    def setLocation(loc: Location) = {
      // These times are important to allow time for test actors to get and process the state updates when running tests
      expectNoMsg(20.milli)
      system.eventStream.publish(loc)
      // This is here to allow the destination to run and set its state
      expectNoMsg(20.milli)
    }

    /**
     * Test Description: Test that updating the HCD actorRef during operations works properly by
     * first setting the HCD to None and then resetting it.
     */
    it("tromboneHCD update should work properly impacting timed events which contact the HCD") {
      // test6
      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

      val fakePublisher = TestProbe()

      // Use HCD as currentStateReceiver
      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(fakePublisher.ref))
      dp ! DiagnosticState
      // Wait for one update message
      fakePublisher.expectMsgClass(classOf[AxisStatsUpdate])

      // Setting HCD to None should turn off stats updates
      setLocation(Unresolved(tromboneHCDConnection))
      expectNoMsg(200.milli) // This is to let event bus and other actor work on slow machines
      fakePublisher.expectNoMsg(1.5.seconds)

      // Turn back on and wait for next event
      val uri = new URI("http://test") // Some fake URI for AkkaLocation
      setLocation(ResolvedAkkaLocation(tromboneHCDConnection, uri, "", Some(tromboneHCD)))
      // Wait for one update message
      fakePublisher.expectMsgClass(classOf[AxisStatsUpdate])

      cleanup(tromboneHCD, dp)
    }
  }

  /**
   * These tests tie the Telemetry Service to the DiagPublisher and verify that real events are published as needed
   */
  describe("functionality tests using Telemetry Service") {

    /**
     * Test Description: This test creates an HCD and uses TestSubscribers to listen for diag publisher events.
     * The diag publisher is in operations state so it requires 6 updates to produce one event
     */
    it("should receive status events in operations mode") {
      // test7
      import TestSubscriber._

      // Create the trombone publisher for publishing SystemEvents to AOESW
      val publisherActorRef = system.actorOf(TrombonePublisher.props(assemblyContext, None, Some(telemetryService)))

      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
      val resultSubscriber = TestActorRef(TestSubscriber.props())
      telemetryService.subscribe(resultSubscriber, postLastEvents = false, axisStateEventPrefix)

      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

      // Use HCD as currentStateReceiver
      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(publisherActorRef))

      // This should cause an event to be generated and received
      // This should cause two published events since skip count is 5
      tromboneHCD ! GetAxisUpdate
      tromboneHCD ! GetAxisUpdate
      tromboneHCD ! GetAxisUpdate
      tromboneHCD ! GetAxisUpdate
      tromboneHCD ! GetAxisUpdate
      tromboneHCD ! GetAxisUpdate

      // Need to give a little time for messages to flow about and back to the subscriber
      // On my machine in this testing envrironment this needs to be at least 1000 ms
      expectNoMsg(1.second)

      // Ask the local subscriber for all the ao events published for testing
      resultSubscriber ! GetStatusResults
      // Check the events received through the Event Service
      val result = expectMsgClass(classOf[StatusResults])
      result.msgs.size shouldBe 2
      //info("result: " + result)

      cleanup(tromboneHCD, dp)
    }

    /**
     * Test Description: This test creates an HCD and uses TestSubscribers to listen for diag publisher events.
     * The diag publisher is in diagnostic state so it publishes an event every 2 updates
     */
    it("should receive status events in diagnostic mode") {
      // test8
      import TestSubscriber._

      // Create the trombone publisher for publishing SystemEvents to AOESW
      val publisherActorRef = system.actorOf(TrombonePublisher.props(assemblyContext, None, Some(telemetryService)))

      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
      val resultSubscriber = TestActorRef(TestSubscriber.props())
      logger.info("Before subscribe")
      telemetryService.subscribe(resultSubscriber, postLastEvents = false, axisStateEventPrefix)
      logger.info("After subscribe")
      logger.info("After wait")

      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

      // Use HCD as currentStateReceiver
      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(publisherActorRef))
      // Turn on Diagnostic state
      dp ! DiagnosticState

      // This should cause an event to be generated and received
      // This should cause 4 published events since skip count is 2 in diag mode
      tromboneHCD ! GetAxisUpdate
      tromboneHCD ! GetAxisUpdate

      tromboneHCD ! GetAxisUpdate
      tromboneHCD ! GetAxisUpdate

      tromboneHCD ! GetAxisUpdate
      tromboneHCD ! GetAxisUpdate

      tromboneHCD ! GetAxisUpdate

      // Need to give a little time for messages to flow about and back to the subscriber, this is related to start up of various systems
      // On my machine in needs to be at least 750 ms for the subscribe to finish
      expectNoMsg(1.second)

      // Turn off timed events
      dp ! OperationsState

      // Ask the local subscriber for all the ao events published for testing
      logger.info("Requesting Status Now")
      resultSubscriber ! GetStatusResults
      // Check the events received through the Event Service
      val result = expectMsgClass(classOf[StatusResults])
      //result.msgs.size shouldBe 4
      info("result: " + result)

      cleanup(tromboneHCD, dp)
    }

    /**
     * Test Description: This test creates an HCD and uses TestSubscribers to listen for diag publisher events.
     * This test is checking that the 1 per second stats events are published properly in diagnostic state
     * It is also testing for concurrent generation of axis state events
     */
    it("should also receive stats events in diagnostic mode") {
      import TestSubscriber._

      // Create the trombone publisher for publishing SystemEvents to AOESW
      val publisherActorRef = system.actorOf(TrombonePublisher.props(assemblyContext, None, Some(telemetryService)))

      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
      val resultSubscriber = TestActorRef(TestSubscriber.props())
      telemetryService.subscribe(resultSubscriber, postLastEvents = false, axisStateEventPrefix)

      // Creates a subscriber for stats events
      val resultSubscriber2 = TestActorRef(TestSubscriber.props())
      telemetryService.subscribe(resultSubscriber2, postLastEvents = false, axisStatsEventPrefix)

      val tromboneHCD = startHCD

      val fakeAssembly = TestProbe()

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))

      // Use HCD as currentStateReceiver
      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(publisherActorRef))
      dp ! DiagnosticState

      // This should cause an event to be generated and received
      // This should cause 4 published events since skip count is 2 in diag state
      tromboneHCD ! GetAxisUpdate
      tromboneHCD ! GetAxisUpdate

      tromboneHCD ! GetAxisUpdate
      tromboneHCD ! GetAxisUpdate

      // Need to give a little time for messages to flow about and back to the subscriber
      // On my machine in needs to be at least 1000 ms with current event service
      expectNoMsg(1.second)

      // Ask the local subscriber for all the stats events received
      resultSubscriber ! GetStatusResults
      // Check the events received through the Event Service
      val result = expectMsgClass(classOf[StatusResults])
      result.msgs.size shouldBe 2 // because of 4 messages
      //info("result: " + result)

      // Now check for stats events
      // Wait 2 seconds for at least two timed events, should result in 2 or 3 depending on timing
      expectNoMsg(2.seconds)

      // Turn off timed events
      dp ! OperationsState

      resultSubscriber2 ! GetStatusResults
      // Check the events received through the Event Service
      val result2 = expectMsgClass(classOf[StatusResults])
      result2.msgs.size should be >= 2
      //info("result: " + result2)

      cleanup(tromboneHCD, dp)
    }
  }

}
