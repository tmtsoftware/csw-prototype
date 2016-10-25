package csw.examples.vslice.assembly

import java.io.File

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.hcd.TromboneHCD
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.alarms.AlarmModel.SeverityLevel.{Okay, Warning}
import csw.services.alarms.{AlarmService, AlarmServiceAdmin}
import csw.services.ccs.CommandStatus2.CommandStatus2
import csw.services.ccs.SequentialExecution.SequentialExecutor.ExecuteOne
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.LocationService
import csw.services.pkg.Component.{HcdInfo, RegisterAndTrackServices}
import csw.services.pkg.Supervisor3
import csw.services.pkg.Supervisor3.{LifecycleInitialized, LifecycleRunning}
import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
import csw.util.config.StateVariable.CurrentState
import csw.util.config.UnitsOfMeasure.encoder
import org.scalatest.{BeforeAndAfterAll, _}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * These tests are for the Trombone AlarmMonitor.
 */
object AlarmMonitorTests {
  LocationService.initInterface()
  val system = ActorSystem("AlarmMonitorTests")
}

/**
 * AlarmMonitorTests
 */
class AlarmMonitorTests extends TestKit(AlarmMonitorTests.system) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {

  import TromboneAlarmMonitor._
  import TromboneStateHandler._
  import system.dispatcher

  implicit val timeout = Timeout(10.seconds)

  // Name of the alarm service Redis instance to use
  val asName = "AlarmMonitorTests"

  // Used to start and stop the alarm service Redis instance used for the test
  var alarmAdmin: AlarmServiceAdmin = _

  // Get the alarm service by looking up the name with the location service.
  var alarmService: AlarmService = _

  override def beforeAll(): Unit = {
    // Note: This part is only for testing: Normally Redis would already be running and registered with the location service.
    // Start redis and register it with the location service on a random free port.
    // The following is the equivalent of running this from the command line:
    //   tracklocation --name "Alarm Service Test" --command "redis-server --port %port"
    logger.info("Starting alarm service")
    AlarmServiceAdmin.startAlarmService(asName)
    // Get the alarm service by looking up the name with the location service.
    // (using a small value for refreshSecs for testing)
    logger.info("Looking up alarm service")
    alarmService = Await.result(AlarmService(asName), timeout.duration)
    alarmAdmin = AlarmServiceAdmin(alarmService)

    logger.info("Initializing alarm data")
    // Initialize the available alarms from a file (location depends on how you run this test - XXX maybe should load it as a Config)
    val testAlarms1 = new File("src/test/resources/test-alarms.conf")
    val testAlarms2 = new File("examples/vslice/src/test/resources/test-alarms.conf")
    val file = if (testAlarms1.exists()) testAlarms1 else testAlarms2
    if (file.exists())
      Await.result(alarmAdmin.initAlarms(file), timeout.duration)
    else logger.error(s"$file does not exist")
    logger.info("Initialized alarm service")
  }

  override def afterAll(): Unit = {
    // Shutdown Redis (Only do this in tests that also started the server)
    if (alarmAdmin != null) Await.ready(alarmAdmin.shutdown(), timeout.duration)
    TestKit.shutdownActorSystem(system)
  }

  val ac = AssemblyTestData.TestAssemblyContext

  import ac._

  def setupState(ts: TromboneState) = {
    // These times are important to allow time for test actors to get and process the state updates when running tests
    expectNoMsg(20.milli)
    system.eventStream.publish(ts)
    // This is here to allow the destination to run and set its state
    expectNoMsg(20.milli)
  }

  // Initialize HCD for testing
  def startHCD: ActorRef = {
    val testInfo = HcdInfo(
      TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      RegisterAndTrackServices, Set(AkkaType), 1.second
    )

    Supervisor3(testInfo)
  }

  def newCommandHandler(tromboneHCD: ActorRef, allEventPublisher: Option[ActorRef] = None) = {
    //val thandler = TestActorRef(TromboneCommandHandler.props(configs, tromboneHCD, allEventPublisher), "X")
    //thandler
    system.actorOf(TromboneCommandHandler.props(ac, Some(tromboneHCD), allEventPublisher))
  }

  // Test Low Limit
  val testLowLimitEvent = CurrentState(TromboneHCD.axisStateCK).madd(
    positionKey -> 0 withUnits encoder,
    stateKey -> AXIS_IDLE,
    inLowLimitKey -> true, // Test case
    inHighLimitKey -> false,
    inHomeKey -> false
  )

  // Test High Limit
  val testHighLimitEvent = CurrentState(TromboneHCD.axisStateCK).madd(
    positionKey -> 0 withUnits encoder,
    stateKey -> AXIS_IDLE,
    inLowLimitKey -> false,
    inHighLimitKey -> true, // Test case
    inHomeKey -> false
  )

  // Test Clear
  val testClearLimitEvent = CurrentState(TromboneHCD.axisStateCK).madd(
    positionKey -> 0 withUnits encoder,
    stateKey -> AXIS_IDLE,
    inLowLimitKey -> false,
    inHighLimitKey -> false,
    inHomeKey -> false
  )

  describe("Basic alarm monitor tests with test alarm service running") {
    /**
     * Test Description: this uses a fake trombone HCD to send  a CurrentState with low limit set.
     * This causes the monitor to send the warning severity to the Alarm Service
     * Then the alarm is cleared. In both cases, the admin interface of the Alarm Service is used to check that
     * the monitor actually did set the alarm severity.
     */
    it("monitor should set a low alarm when receiving simulated encoder low limit") {
      val fakeTromboneHCD = TestProbe()

      // Create an alarm monitor
      val am = system.actorOf(TromboneAlarmMonitor.props(fakeTromboneHCD.ref, alarmService))
      expectNoMsg(100.milli) // A delay waiting for monitor to find AlarmService with LocationService

      // the fake trombone HCD sends a CurrentState event that has the low limit sent
      fakeTromboneHCD.send(am, testLowLimitEvent)

      expectNoMsg(50.milli) // A bit of time for processing and update of AlarmService

      // This is checking that the value in the alarm service has been set using admin interface
      var alarmValue = Await.result(alarmService.getSeverity(lowLimitAlarm), timeout.duration)
      alarmValue.reported shouldBe Warning

      // This simulates that the alarm has been cleared
      fakeTromboneHCD.send(am, testClearLimitEvent)

      expectNoMsg(50.milli) // A bit of time

      // use the alarm service admin to see that it is cleared,
      alarmValue = Await.result(alarmService.getSeverity(lowLimitAlarm), timeout.duration)
      alarmValue.reported shouldBe Okay
    }

    /**
     * Test Description: this uses a fake trombone HCD to send  a CurrentState with high limit set.
     * This causes the monitor to send the warning severity to the Alarm Service
     * Then the alarm is cleared. In both cases, the admin interface of the Alarm Service is used to check that
     * the monitor actually did set the alarm severity.
     */
    it("monitor should set a low alarm when receiving simulated encoder high limit") {
      val fakeTromboneHCD = TestProbe()

      // Create an alarm monitor
      val am = system.actorOf(TromboneAlarmMonitor.props(fakeTromboneHCD.ref, alarmService))
      expectNoMsg(100.milli) // A delay waiting for monitor to find AlarmService with LocationService

      // the fake trombone HCD sends a CurrentState event that has the high limit sent
      fakeTromboneHCD.send(am, testHighLimitEvent)

      expectNoMsg(50.milli) // A bit of time for processing and update of AlarmService

      // This is checking that the value in the alarm service has been set using admin interface
      var alarmValue = Await.result(alarmService.getSeverity(highLimitAlarm), timeout.duration)
      alarmValue.reported shouldBe Warning

      // This simulates that the alarm has been cleared
      fakeTromboneHCD.send(am, testClearLimitEvent)

      expectNoMsg(50.milli) // A bit of time

      // use the alarm service admin to see that it is cleared,
      alarmValue = Await.result(alarmService.getSeverity(highLimitAlarm), timeout.duration)
      alarmValue.reported shouldBe Okay
    }

    /**
     * Test Description: This test uses the actual HCD to drive the axis to the low limit and verify that the low
     * alarm is set and that the AlarmMonitor sets the alarm in the alarm service to warning
     */
    it("monitor should set a low alarm when receiving real encoder low limit using real HCD to generate data") {
      val tromboneHCD = startHCD
      val fakeAssembly = TestProbe()

      // This is checking that the value in the alarm service has been set using admin interface
      Await.result(alarmService.setSeverity(lowLimitAlarm, Okay, refresh = true), timeout.duration)
      var alarmValue = Await.result(alarmService.getSeverity(lowLimitAlarm), timeout.duration)
      logger.info("Initial alarm value should be okay or disconnected")
      alarmValue.reported shouldBe Okay

      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      //info("Running")

      // Create an alarm monitor
      system.actorOf(TromboneAlarmMonitor.props(tromboneHCD, alarmService))
      expectNoMsg(100.milli) // A delay waiting for monitor to find AlarmService with LocationService

      // The command handler sends commands to the trombone HCD
      val ch = newCommandHandler(tromboneHCD)

      setupState(TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)))

      // Move to the 0 position
      val limitPosition = 0.0
      ch ! ExecuteOne(moveSC(limitPosition), Some(fakeAssembly.ref))
      // Watch for command completion
      fakeAssembly.expectMsgClass(35.seconds, classOf[CommandStatus2])

      expectNoMsg(50.milli) // A bit of time for processing and update of AlarmService

      // This is checking that the value in the alarm service has been set using admin interface
      alarmValue = Await.result(alarmService.getSeverity(lowLimitAlarm), timeout.duration)
      // use the alarm service admin to see that it is cleared,
      alarmValue.reported shouldBe Warning

      // Now move it out of the limit and see that the alarm is cleared
      val clearPosition = 100.0
      ch ! ExecuteOne(moveSC(clearPosition), Some(fakeAssembly.ref))
      fakeAssembly.expectMsgClass(35.seconds, classOf[CommandStatus2])

      expectNoMsg(50.milli) // A bit of time for processing and update of AlarmService

      // This is checking that the value in the alarm service has been set using admin interface
      alarmValue = Await.result(alarmService.getSeverity(lowLimitAlarm), timeout.duration)
      alarmValue.reported shouldBe Okay
    }

    /**
     * Test Description: This test uses the actual HCD to drive the axis to the high limit and verify that the high
     * alarm is set and that the AlarmMonitor sets the alarm in the alarm service to warning
     */
    it("monitor should set a high alarm when receiving real encoder high limit using real HCD to generate data") {
      val tromboneHCD = startHCD
      val fakeAssembly = TestProbe()

      // This is checking that the value in the alarm service has been set using admin interface
      Await.result(alarmService.setSeverity(highLimitAlarm, Okay, refresh = true), timeout.duration)
      var alarmValue = Await.result(alarmService.getSeverity(highLimitAlarm), timeout.duration)
      logger.info("Initial alarm value should be okay or disconnected")
      alarmValue.reported shouldBe Okay

      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      //info("Running")

      // Create an alarm monitor
      system.actorOf(TromboneAlarmMonitor.props(tromboneHCD, alarmService))
      expectNoMsg(100.milli) // A delay waiting for monitor to find AlarmService with LocationService

      // The command handler sends commands to the trombone HCD
      val ch = newCommandHandler(tromboneHCD)

      setupState(TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)))

      val limitPosition = 2000.0
      ch ! ExecuteOne(moveSC(limitPosition), Some(fakeAssembly.ref))

      fakeAssembly.expectMsgClass(35.seconds, classOf[CommandStatus2])

      expectNoMsg(500.milli) // A bit of time for processing and update of AlarmService due to move

      // This is checking that the value in the alarm service has been set using admin interface
      alarmValue = Await.result(alarmService.getSeverity(highLimitAlarm), timeout.duration)
      // use the alarm service admin to see that it is cleared,
      alarmValue.reported shouldBe Warning

      // Now move it out of the limit and see that the alarm is cleared
      val clearPosition = AssemblyTestData.maxReasonableStage
      ch ! ExecuteOne(moveSC(clearPosition), Some(fakeAssembly.ref))
      fakeAssembly.expectMsgClass(35.seconds, classOf[CommandStatus2])

      expectNoMsg(50.milli) // A bit of time for processing and update of AlarmService

      // This is checking that the value in the alarm service has been set using admin interface
      alarmValue = Await.result(alarmService.getSeverity(highLimitAlarm), timeout.duration)
      alarmValue.reported shouldBe Okay
    }
  }

}
