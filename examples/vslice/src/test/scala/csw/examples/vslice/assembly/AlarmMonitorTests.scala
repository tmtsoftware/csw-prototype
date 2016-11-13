package csw.examples.vslice.assembly

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.hcd.TromboneHCD
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.alarms.AlarmModel.SeverityLevel.{Okay, Warning}
import csw.services.alarms.{AlarmKey, AlarmService, AlarmServiceAdmin}
import csw.services.ccs.CommandStatus2.CommandStatus2
import csw.services.ccs.SequentialExecutor.ExecuteOne
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.LocationService
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
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
  import TromboneStateActor._

  implicit val timeout = Timeout(10.seconds)

  // Get the alarm service by looking up the name with the location service.
  private val alarmService = Await.result(AlarmService(), timeout.duration)

  // Used to start and stop the alarm service Redis instance used for the test
  val alarmAdmin = AlarmServiceAdmin(alarmService)

  override def beforeAll(): Unit = {
    // Note: This part is only for testing: Normally Redis would already be running and registered with the location service.
    // Start redis and register it with the location service on a random free port.
    // The following is the equivalent of running this from the command line:
    //   tracklocation --name "Alarm Service Test" --command "redis-server --port %port"
    //    logger.info("Starting alarm service")
    //    AlarmServiceAdmin.startAlarmService(asName)
    // Get the alarm service by looking up the name with the location service.
    // (using a small value for refreshSecs for testing)
    //    logger.info("Looking up alarm service")
    //    alarmService = Await.result(AlarmService(asName), timeout.duration)
    //    alarmAdmin = AlarmServiceAdmin(alarmService)

    setupAlarms()

  }

  def setupAlarms(): Unit = {
    Await.result(alarmAdmin.acknowledgeAndResetAlarm(TromboneAlarmMonitor.lowLimitAlarm), timeout.duration)
    Await.result(alarmAdmin.acknowledgeAndResetAlarm(TromboneAlarmMonitor.highLimitAlarm), timeout.duration)
    logger.info("Initializing alarm data")
  }

  override def afterAll(): Unit = {
    // Shutdown Redis (Only do this in tests that also started the server)
    //    if (alarmAdmin != null) Await.ready(alarmAdmin.shutdown(), timeout.duration)
    TestKit.shutdownActorSystem(system)
  }

  val ac = AssemblyTestData.TestAssemblyContext

  import ac._

//  def setupState(ts: TromboneState) = {
//    // These times are important to allow time for test actors to get and process the state updates when running tests
//    expectNoMsg(20.milli)
//    system.eventStream.publish(ts)
//    // This is here to allow the destination to run and set its state
//    expectNoMsg(20.milli)
//  }

  // Initialize HCD for testing
  def startHCD: ActorRef = {
    val testInfo = HcdInfo(
      TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Set(AkkaType), 1.second
    )

    Supervisor3(testInfo)
  }

  def newCommandHandler(tromboneHCD: ActorRef, allEventPublisher: Option[ActorRef] = None): ActorRef = {
    //val thandler = TestActorRef(TromboneCommandHandler.props(configs, tromboneHCD, allEventPublisher), "X")
    //thandler
    system.actorOf(TromboneCommandHandler.props(ac, Some(tromboneHCD), allEventPublisher))
  }

  // Test Low Limit
  private val testLowLimitEvent = CurrentState(TromboneHCD.axisStateCK).madd(
    positionKey -> 0 withUnits encoder,
    stateKey -> AXIS_IDLE,
    inLowLimitKey -> true, // Test case
    inHighLimitKey -> false,
    inHomeKey -> false
  )

  // Test High Limit
  private val testHighLimitEvent = CurrentState(TromboneHCD.axisStateCK).madd(
    positionKey -> 0 withUnits encoder,
    stateKey -> AXIS_IDLE,
    inLowLimitKey -> false,
    inHighLimitKey -> true, // Test case
    inHomeKey -> false
  )

  // Test Clear
  private val testClearLimitEvent = CurrentState(TromboneHCD.axisStateCK).madd(
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
      testLimitEvent(testLowLimitEvent, lowLimitAlarm)
    }

    /**
     * Test Description: this uses a fake trombone HCD to send  a CurrentState with high limit set.
     * This causes the monitor to send the warning severity to the Alarm Service
     * Then the alarm is cleared. In both cases, the admin interface of the Alarm Service is used to check that
     * the monitor actually did set the alarm severity.
     */
    it("monitor should set a high alarm when receiving simulated encoder high limit") {
      testLimitEvent(testHighLimitEvent, highLimitAlarm)
    }

    /**
     * Test Description: This test uses the actual HCD to drive the axis to the high limit and verify that the high
     * alarm is set and that the AlarmMonitor sets the alarm in the alarm service to warning
     */
    it("monitor should set a high alarm when receiving real encoder high limit using real HCD to generate data") {
      testLimitAlarm(highLimitAlarm, 2000.0, AssemblyTestData.maxReasonableStage)
    }

    /**
     * Test Description: This test uses the actual HCD to drive the axis to the low limit and verify that the low
     * alarm is set and that the AlarmMonitor sets the alarm in the alarm service to warning
     */
    it("monitor should set a low alarm when receiving real encoder low limit using real HCD to generate data") {
      testLimitAlarm(lowLimitAlarm, 0.0, 100.0)
    }
  }

  def testLimitEvent(limitEvent: CurrentState, alarmKey: AlarmKey): Unit = {
    val fakeTromboneHCD = TestProbe()

    // Create an alarm monitor
    val am = system.actorOf(TromboneAlarmMonitor.props(fakeTromboneHCD.ref, alarmService))
    expectNoMsg(100.milli) // A delay waiting for monitor to find AlarmService with LocationService

    // the fake trombone HCD sends a CurrentState event that has the high limit sent
    fakeTromboneHCD.send(am, limitEvent)

    expectNoMsg(50.milli) // A bit of time for processing and update of AlarmService

    // This is checking that the value in the alarm service has been set using admin interface
    val alarmValue = Await.result(alarmAdmin.getSeverity(alarmKey), timeout.duration)
    alarmValue.reported shouldBe Warning

    // This simulates that the alarm has been cleared
    fakeTromboneHCD.send(am, testClearLimitEvent)

    expectNoMsg(50.milli) // A bit of time

    // use the alarm service admin to see that it is cleared,
    val alarmValue2 = Await.result(alarmAdmin.getSeverity(alarmKey), timeout.duration)
    alarmValue2.reported shouldBe Okay

    system.stop(am)
  }

  def testLimitAlarm(alarmKey: AlarmKey, limitPosition: Double, clearPosition: Double) {
    import TromboneStateActor._
    // For setting state
    val tromboneHCD = startHCD
    val fakeAssembly = TestProbe()

    // This is checking that the value in the alarm service has been set using admin interface
    setupAlarms()
    Await.result(alarmService.setSeverity(alarmKey, Okay), timeout.duration)
    val alarmValue = Await.result(alarmAdmin.getSeverity(alarmKey), timeout.duration)
    logger.info("Initial alarm value should be okay or disconnected")
    alarmValue.reported shouldBe Okay

    // This is boiler plate for setting up an HCD for testing
    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
    //info("Running")

    // Create an alarm monitor
    val am = system.actorOf(TromboneAlarmMonitor.props(tromboneHCD, alarmService))
    expectNoMsg(100.milli) // A delay waiting for monitor to find AlarmService with LocationService

    // The command handler sends commands to the trombone HCD
    val ch = newCommandHandler(tromboneHCD)

    val needToSetStateForMoveCommand = system.actorOf(TromboneStateActor.props())
    needToSetStateForMoveCommand ! SetState(cmdReady, moveIndexed, sodiumLayer = false, nss = false)
    expectNoMsg(900.milli)

    // Move to the 0 position
    ch ! ExecuteOne(moveSC(limitPosition), Some(fakeAssembly.ref))
    // Watch for command completion
    val result = fakeAssembly.expectMsgClass(35.seconds, classOf[CommandStatus2])
    logger.info("Result: " + result)

    expectNoMsg(500.milli) // A bit of time for processing and update of AlarmService

    // This is checking that the value in the alarm service has been set using admin interface
    val alarmValue2 = Await.result(alarmAdmin.getSeverity(alarmKey), timeout.duration)
    // use the alarm service admin to see that it is cleared,
    alarmValue2.reported shouldBe Warning // XXX

    // Now move it out of the limit and see that the alarm is cleared
    ch ! ExecuteOne(moveSC(clearPosition), Some(fakeAssembly.ref))
    fakeAssembly.expectMsgClass(35.seconds, classOf[CommandStatus2])

    expectNoMsg(50.milli) // A bit of time for processing and update of AlarmService

    // This is checking that the value in the alarm service has been set using admin interface
    val alarmValue3 = Await.result(alarmAdmin.getSeverity(alarmKey), timeout.duration)
    alarmValue3.reported shouldBe Okay

    //    expectNoMsg(3.seconds)
    system.stop(ch)
    system.stop(needToSetStateForMoveCommand)
    system.stop(am)
    tromboneHCD ! PoisonPill
  }
}
