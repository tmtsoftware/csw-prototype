//package csw.examples.vsliceJava.assembly
//
//import akka.actor.{ActorRef, ActorSystem, Props}
//import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
//import com.typesafe.scalalogging.slf4j.LazyLogging
//import csw.services.events.{EventService, EventServiceSettings, EventSubscriber}
//import csw.services.loc.ConnectionType.AkkaType
//import csw.services.loc.LocationService
//import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
//import csw.services.pkg.Supervisor3
//import csw.services.pkg.Supervisor3.{LifecycleInitialized, LifecycleRunning}
//import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
//import csw.util.config.Events.{StatusEvent, SystemEvent}
//
//import scala.concurrent.duration._
//
///**
// * Diag Pubisher Tests
// */
//object DiagPublisherTests {
//  LocationService.initInterface()
//  val system = ActorSystem("DiagPublisherSystem")
//
//}
//
//class DiagPublisherTests extends TestKit(DiagPublisherTests.system) with ImplicitSender
//    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {
//
//  def startHCD: ActorRef = {
//    val testInfo = HcdInfo(
//      TromboneHCD.componentName,
//      TromboneHCD.trombonePrefix,
//      TromboneHCD.componentClassName,
//      DoNotRegister, Set(AkkaType), 1.second
//    )
//
//    Supervisor3(testInfo)
//  }
//
//  override def afterAll = TestKit.shutdownActorSystem(system)
//
//  implicit val execContext = system.dispatcher
//
//  val testEventServiceSettings = EventServiceSettings("localhost", 7777)
//
//  val assemblyContext = AssemblyTestData.TestAssemblyContext
//
//  def eventConnection: EventService = EventService(testEventServiceSettings)
//
//  def newDiagPublisher(currentStateReceiver: ActorRef, tromboneHCD: Option[ActorRef], eventPublisher: Option[ActorRef]): TestActorRef[DiagPublisher] = {
//    val props = DiagPublisher.props(currentStateReceiver, tromboneHCD, eventPublisher)
//    TestActorRef[DiagPublisher](props)
//  }
//
//  // Test subscriber actor for telemetry and system events
//  object TestSubscriber {
//    def props(prefix: String): Props = Props(new TestSubscriber(prefix))
//
//    case object GetSysResults
//    case object GetStatusResults
//
//    case class SysResults(msgs: Vector[SystemEvent])
//    case class StatusResults(msgs: Vector[StatusEvent])
//  }
//
//  /**
//   * Test event service client, subscribes to some event
//   * @param prefix the prefix that will be subscribed to
//   */
//  class TestSubscriber(prefix: String) extends EventSubscriber(Some(testEventServiceSettings)) {
//
//    import TestSubscriber._
//
//    var sysmsgs = Vector.empty[SystemEvent]
//    var statmsgs = Vector.empty[StatusEvent]
//
//    subscribe(prefix)
//    log.info(s"Test subscriber for prefix: $prefix")
//
//    def receive: Receive = {
//      case event: SystemEvent =>
//        sysmsgs = sysmsgs :+ event
//        log.debug(s"Received system event: $event")
//      case event: StatusEvent =>
//        statmsgs = statmsgs :+ event
//        log.debug(s"Received status event: $event")
//
//      case GetSysResults    => sender() ! SysResults(sysmsgs)
//      case GetStatusResults => sender() ! StatusResults(statmsgs)
//    }
//  }
//
//  describe("basic diag tests") {
//
//    /**
//     * Test Description: Stimulate DiagPublisher with CurrentState events to demonstrate diag publishing in operations state.
//     */
//    it("should see one type of messages sent to publisher in operations mode") {
//      val tromboneHCD = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      // The following is to synchronize the test with the HCD entering Running state
//      // This is boiler plate for setting up an HCD for testing
//      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//      val fakePublisher = TestProbe()
//
//      // Use HCD as currentStateReceiver
//      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(fakePublisher.ref))
//
//      // Sending GetAxisStats and GetAxisUpdate to tromboneHCD simulates generation of messages during motion -- at least for AxisUpdate
//      // Operations mode ignores AxisStats messages
//      tromboneHCD ! GetAxisStats
//      // Check that nothing is happening here
//      fakePublisher.expectNoMsg(20.milli)
//
//      // Skip count is 5 so should get one message right away and then none for 4 more - just check for one
//      tromboneHCD ! GetAxisUpdate
//      fakePublisher.expectMsgClass(classOf[AxisStateUpdate])
//
//      system.stop(dp)
//      system.stop(tromboneHCD)
//    }
//
//    /**
//     * Test Description: Stimulate DiagPublisher with CurrentState events to demonstrate diag publishing in operations mode.
//     * This test shows that in operations state the skip count is 5
//     */
//    it("should see one state message sent to publisher in operations mode for every skipCount messages") {
//      val tromboneHCD = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      // The following is to synchronize the test with the HCD entering Running state
//      // This is boiler plate for setting up an HCD for testing
//      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//      val fakePublisher = TestProbe()
//
//      // Use HCD as currentStateReceiver
//      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(fakePublisher.ref))
//
//      // Skip count is 5 so should get one message right away and then none for 4 more
//      tromboneHCD ! GetAxisUpdate
//      var msg = fakePublisher.expectMsgClass(classOf[AxisStateUpdate])
//      tromboneHCD ! GetAxisUpdate
//      fakePublisher.expectNoMsg(20.milli)
//      tromboneHCD ! GetAxisUpdate
//      fakePublisher.expectNoMsg(20.milli)
//      tromboneHCD ! GetAxisUpdate
//      fakePublisher.expectNoMsg(20.milli)
//      tromboneHCD ! GetAxisUpdate
//      fakePublisher.expectNoMsg(20.milli)
//      tromboneHCD ! GetAxisUpdate
//      msg = fakePublisher.expectMsgClass(classOf[AxisStateUpdate])
//
//      system.stop(dp)
//      system.stop(tromboneHCD)
//    }
//
//    /**
//     * Test Description: Stimulate DiagPublisher with CurrentState events to demonstrate diag publishing in diagnostic mode.
//     * This test shows that in diagnostic state the skip count is 2
//     */
//    it("should see one state message sent to publisher in diagnostics mode for every update message") {
//      val tromboneHCD = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      // The following is to synchronize the test with the HCD entering Running state
//      // This is boiler plate for setting up an HCD for testing
//      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//      val fakePublisher = TestProbe()
//
//      // Use HCD as currentStateReceiver
//      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(fakePublisher.ref))
//
//      dp ! DiagnosticState
//
//      // Skip count is 2 so should get a message for every other event
//      tromboneHCD ! GetAxisUpdate
//      var msg = fakePublisher.expectMsgClass(classOf[AxisStateUpdate])
//      tromboneHCD ! GetAxisUpdate
//      fakePublisher.expectNoMsg(20.milli)
//      tromboneHCD ! GetAxisUpdate
//      msg = fakePublisher.expectMsgClass(classOf[AxisStateUpdate])
//      tromboneHCD ! GetAxisUpdate
//      fakePublisher.expectNoMsg(20.milli)
//      tromboneHCD ! GetAxisUpdate
//      msg = fakePublisher.expectMsgClass(classOf[AxisStateUpdate])
//      tromboneHCD ! GetAxisUpdate
//      fakePublisher.expectNoMsg(20.milli)
//
//      system.stop(dp)
//      system.stop(tromboneHCD)
//    }
//
//    /**
//     * Test Description: This test shows that in diagnostic state there is also a stats event once/second.
//     * This test waits for one message demonstrating that stats events are published
//     */
//    it("should see one stats message sent to publisher in diagnostics mode every second (current spec)") {
//      val tromboneHCD = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      // The following is to synchronize the test with the HCD entering Running state
//      // This is boiler plate for setting up an HCD for testing
//      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//      val fakePublisher = TestProbe()
//
//      // Use HCD as currentStateReceiver
//      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(fakePublisher.ref))
//
//      dp ! DiagnosticState
//
//      // Because timeout is 3 seconds, we get the one stats event after 1 second
//      fakePublisher.expectMsgClass(classOf[AxisStatsUpdate])
//    }
//
//    /**
//     * Test Description: Demonstrate that stats events are published once/second by waiting for 3 seconds
//     * The end of the test demonstrates that the stats events are turned off properl in operations state
//     */
//    it("should generate several timed events in diagnostic mode") {
//      val tromboneHCD = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      // The following is to synchronize the test with the HCD entering Running state
//      // This is boiler plate for setting up an HCD for testing
//      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//      val fakeEventPublisher = TestProbe()
//
//      // Use HCD as currentStateReceiver
//      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(fakeEventPublisher.ref))
//      dp ! DiagnosticState
//      var msgs = Seq[AxisStatsUpdate]()
//
//      // Wait for a bit over 3 seconds
//      fakeEventPublisher.receiveWhile(3200.milli) {
//        case asu: AxisStatsUpdate =>
//          msgs = asu +: msgs
//      }
//      msgs.size shouldBe 3
//      msgs.head shouldBe a[AxisStatsUpdate]
//
//      // Now turn them off
//      dp ! OperationsState
//      // A delay to see that no messages arrive after one second to ensure timer is off
//      fakeEventPublisher.expectNoMsg(1200.milli)
//
//      system.stop(dp)
//      system.stop(tromboneHCD)
//    }
//
//    /**
//     * Test Description: Test that updating the HCD actorRef during operations works properly by
//     * first setting the HCD to None and then resetting it.
//     */
//    it("tromboneHCD update should work properly impacting timed events which contact the HCD") {
//      val tromboneHCD = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      // The following is to synchronize the test with the HCD entering Running state
//      // This is boiler plate for setting up an HCD for testing
//      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//      val fakePublisher = TestProbe()
//
//      // Use HCD as currentStateReceiver
//      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(fakePublisher.ref))
//      dp ! DiagnosticState
//      // Wait for one update message
//      fakePublisher.expectMsgClass(classOf[AxisStatsUpdate])
//      // Setting HCD to None should turn off stats updates
//      dp ! UpdateTromboneHCD(None)
//      fakePublisher.expectNoMsg(1.5.seconds)
//
//      // Turn back on and wait for next event
//      dp ! UpdateTromboneHCD(Some(tromboneHCD))
//      // Wait for one update message
//      fakePublisher.expectMsgClass(classOf[AxisStatsUpdate])
//
//      system.stop(dp)
//      system.stop(tromboneHCD)
//    }
//  }
//
//  /**
//   * These tests tie the Event Service to the DiagPublisher and verify that real events are published as needed
//   */
//  describe("functionality tests using Event Service") {
//
//    /**
//     * Test Description: This test creates an HCD and uses TestSubscribers to listen for diag publisher events.
//     * The diag publisher is in operations state so it requires 6 updates
//     */
//    it("should receive status events in operations mode") {
//      import TestSubscriber._
//
//      // Create the trombone publisher for publishing SystemEvents to AOESW
//      val publisherActorRef = system.actorOf(TrombonePublisher.props(assemblyContext, Some(testEventServiceSettings)))
//
//      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
//      val resultSubscriber = TestActorRef(TestSubscriber.props(axisStateEventPrefix))
//      //expectNoMsg(50.milli)
//
//      val tromboneHCD = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      // The following is to synchronize the test with the HCD entering Running state
//      // This is boiler plate for setting up an HCD for testing
//      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//      // Use HCD as currentStateReceiver
//      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(publisherActorRef))
//
//      // This should cause an event to be generated and received
//      // This should cause two published events since skip count is 5
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//
//      // Need to give a little time for messages to flow about and back to the subscriber
//      // On my machine in this testing envrionement this needs to be at least 100 ms
//      expectNoMsg(120.millis)
//
//      // Ask the local subscriber for all the ao events published for testing
//      resultSubscriber ! GetStatusResults
//      // Check the events received through the Event Service
//      val result = expectMsgClass(classOf[StatusResults])
//      result.msgs.size shouldBe 2
//      //info("result: " + result)
//
//      system.stop(dp)
//      system.stop(publisherActorRef)
//      system.stop(tromboneHCD)
//    }
//
//    /**
//     * Test Description: This test creates an HCD and uses TestSubscribers to listen for diag publisher events.
//     * The diag publisher is in diagnostic state so it publishes an event every 2 updates
//     */
//    it("should receive status events in diagnostic mode") {
//      import TestSubscriber._
//
//      // Create the trombone publisher for publishing SystemEvents to AOESW
//      val publisherActorRef = system.actorOf(TrombonePublisher.props(assemblyContext, Some(testEventServiceSettings)))
//
//      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
//      val resultSubscriber = TestActorRef(TestSubscriber.props(axisStateEventPrefix))
//      //expectNoMsg(50.milli)
//
//      val tromboneHCD = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      // The following is to synchronize the test with the HCD entering Running state
//      // This is boiler plate for setting up an HCD for testing
//      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//      // Use HCD as currentStateReceiver
//      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(publisherActorRef))
//      // Turn on Diagnostic state
//      dp ! DiagnosticState
//
//      // This should cause an event to be generated and received
//      // This should cause 4 published events since skip count is 2 in diag mode
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//
//      tromboneHCD ! GetAxisUpdate
//
//      // Need to give a little time for messages to flow about and back to the subscriber
//      // On my machine in needs to be at least 100 ms
//      expectNoMsg(120.millis)
//
//      // Turn off timed events
//      dp ! OperationsState
//
//      // Ask the local subscriber for all the ao events published for testing
//      resultSubscriber ! GetStatusResults
//      // Check the events received through the Event Service
//      val result = expectMsgClass(classOf[StatusResults])
//      result.msgs.size shouldBe 4
//      //info("result: " + result)
//
//      system.stop(dp)
//      system.stop(publisherActorRef)
//      system.stop(tromboneHCD)
//    }
//
//    /**
//     * Test Description: This test creates an HCD and uses TestSubscribers to listen for diag publisher events.
//     * This test is checking that the 1 per second stats events are published properly in diagnostic state
//     * It is also testing for concurrent generation of axis state events
//     */
//    it("should also receive stats events in diagnostic mode") {
//      import TestSubscriber._
//
//      // Create the trombone publisher for publishing SystemEvents to AOESW
//      val publisherActorRef = system.actorOf(TrombonePublisher.props(assemblyContext, Some(testEventServiceSettings)))
//
//      // This creates a subscriber to get all aoSystemEventPrefix SystemEvents published
//      val resultSubscriber = TestActorRef(TestSubscriber.props(axisStateEventPrefix))
//      // Creates a subscriber for stats events
//      val resultSubscriber2 = TestActorRef(TestSubscriber.props(axisStatsEventPrefix))
//
//      val tromboneHCD = startHCD
//
//      val fakeAssembly = TestProbe()
//
//      // The following is to synchronize the test with the HCD entering Running state
//      // This is boiler plate for setting up an HCD for testing
//      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//      // Use HCD as currentStateReceiver
//      val dp = newDiagPublisher(tromboneHCD, Some(tromboneHCD), Some(publisherActorRef))
//      dp ! DiagnosticState
//
//      // This should cause an event to be generated and received
//      // This should cause 4 published events since skip count is 2 in diag state
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//
//      tromboneHCD ! GetAxisUpdate
//      tromboneHCD ! GetAxisUpdate
//
//      // Need to give a little time for messages to flow about and back to the subscriber
//      // On my machine in needs to be at least 100 ms
//      expectNoMsg(120.millis)
//
//      // Ask the local subscriber for all the stats events received
//      resultSubscriber ! GetStatusResults
//      // Check the events received through the Event Service
//      val result = expectMsgClass(classOf[StatusResults])
//      result.msgs.size shouldBe 2 // because of 4 messages
//      //info("result: " + result)
//
//      // Now check for stats events
//      // Wait 2 seconds for at least two timed events
//      expectNoMsg(2.seconds)
//
//      // Turn off timed events
//      dp ! OperationsState
//
//      resultSubscriber2 ! GetStatusResults
//      // Check the events received through the Event Service
//      val result2 = expectMsgClass(classOf[StatusResults])
//      result2.msgs.size shouldBe 2
//      //info("result: " + result2)
//
//      system.stop(dp)
//      system.stop(publisherActorRef)
//      system.stop(tromboneHCD)
//    }
//
//  }
//
//}
