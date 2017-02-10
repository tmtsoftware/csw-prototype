package csw.services.alarms

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import csw.services.alarms.AlarmModel.{AlarmStatus, CurrentSeverity, Health, HealthStatus, SeverityLevel}
import csw.services.alarms.AlarmState.{ActivationState, ShelvedState}
import csw.services.alarms.AscfValidation.Problem
import csw.services.loc.LocationService
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object BlockingAlarmServiceTests {
  LocationService.initInterface()
  val system = ActorSystem("Test")
}

/**
 * Test the blocking Alarm Service API
 */
class BlockingAlarmServiceTests extends TestKit(BlockingAlarmServiceTests.system) with FunSuiteLike with LazyLogging with BeforeAndAfterAll {
  implicit val sys = BlockingAlarmServiceTests.system

  import system.dispatcher

  implicit val timeout = Timeout(15.seconds)

  // Set a low refresh rate for the test (should not be used in non-test code)
  val refreshSecs = 1
  System.setProperty("csw.services.alarms.refreshSecs", refreshSecs.toString)

  // Get the test alarm service config file (ascf)
  val url = getClass.getResource("/test-alarms.conf")
  val ascf = Paths.get(url.toURI).toFile

  // Get the alarm service by looking up the name with the location service.
  val alarmService = Await.result(AlarmService(), timeout.duration)

  // Used to start and stop the alarm service Redis instance used for the test
  //  val alarmAdmin = AlarmServiceAdmin(alarmService)

  override protected def beforeAll(): Unit = {
    // Note: This part is only for testing: Normally Redis would already be running and registered with the location service.
    // Start redis and register it with the location service on a random free port.
    // The following is the equivalent of running this from the command line:
    //   tracklocation --name "Alarm Service Test" --command "redis-server --port %port"
    //    AlarmServiceAdmin.startAlarmService(asName)
    // Get the alarm service by looking up the name with the location service.
    // (using a small value for refreshSecs for testing)
    //    alarmService = Await.result(AlarmService(asName, refreshSecs = refreshSecs), timeout.duration)
    //    alarmAdmin = AlarmServiceAdmin(alarmService)
  }

  override protected def afterAll(): Unit = {
    // Shutdown Redis (Only do this in tests that also started the server)
    //    Try(if (alarmAdmin != null) Await.ready(alarmAdmin.shutdown(), timeout.duration))
    TestKit.shutdownActorSystem(system)
  }

  test("Test initializing the alarm service, then set, get, list, monitor, acknowledge alarms") {

    // Time until alarm severity expires
    val expireSecs = refreshSecs * AlarmService.maxMissedRefresh

    // Time in ms to wait for a Redis callback
    val shortDelayMs = 500 * refreshSecs

    // Time in ms to wait to see if an alarm severity expired
    val delayMs = expireSecs * 1000 * refreshSecs + shortDelayMs

    val bas = BlockingAlarmService()
    val basAdmin = BlockingAlarmServiceAdmin(BlockingAlarmService())

    // initialize the list of alarms in Redis (This is only for the test and should not be done by normal clients)
    val problems = basAdmin.initAlarms(ascf)
    Problem.printProblems(problems)
    assert(Problem.errorCount(problems) == 0)

    // List all the alarms that were written to Redis
    val alarms = basAdmin.getAlarms(AlarmKey())
    alarms.foreach { alarm =>
      // XXX TODO: compare results
      logger.debug(s"List Alarm: $alarm")
    }

    // For testing callback
    var callbackSev: CurrentSeverity = CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Disconnected)
    var callbackHealth: Option[Health] = None

    // Called when alarm severity changes
    def printAlarmStatus(alarmStatus: AlarmStatus): Unit = {
      val a = alarmStatus.alarmKey
      logger.debug(s"Alarm Status: ${a.subsystem}:${a.component}:${a.name}: ${alarmStatus.currentSeverity}")
      callbackSev = alarmStatus.currentSeverity
    }

    // Called when the health status changes
    def printHealthStatus(healthStatus: HealthStatus): Unit = {
      val a = healthStatus.key
      logger.debug(s"Health Status: ${a.subsystem}:${a.component}:${a.name}: ${healthStatus.health}")
      callbackHealth = Some(healthStatus.health)
    }

    // Test working with an alarm and monitoring the alarm severity level
    val key1 = AlarmKey("TCS", "tcsPk", "cpuExceededAlarm")
    val key2 = AlarmKey("nfiraos", "nfiraos.cc.trombone", "tromboneAxisHighLimitAlarm")
    val key3 = AlarmKey("nfiraos", "nfiraos.cc.trombone", "tromboneAxisLowLimitAlarm")
    val badKey = AlarmKey("XXX", "xxx", "xxx")

    val alarmMonitor = basAdmin.monitorAlarms(key1, printAlarmStatus, printHealthStatus, notifyAll = false)
    Thread.sleep(shortDelayMs) // make sure actor has started

    bas.setSeverity(key1, SeverityLevel.Critical)
    Thread.sleep(delayMs) // wait for severity to expire

    // alarm is latched, so stays at critical
    assert(basAdmin.getSeverity(key1) == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Critical))
    assert(callbackSev == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Critical))

    bas.setSeverity(key1, SeverityLevel.Warning)
    // alarm is latched, so stays at critical
    assert(basAdmin.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Critical))
    Thread.sleep(shortDelayMs)
    assert(callbackSev == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Critical))

    // Acknowledge the alarm, which clears it, resets it back to Okay
    basAdmin.acknowledgeAndResetAlarm(key1)
    bas.setSeverity(key1, SeverityLevel.Okay)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback, so the test below passes
    assert(basAdmin.getSeverity(key1) == CurrentSeverity(SeverityLevel.Okay, SeverityLevel.Okay)) // alarm was cleared
    assert(callbackSev == CurrentSeverity(SeverityLevel.Okay, SeverityLevel.Okay))

    Thread.sleep(delayMs) // wait for severity to expire and become "Disconnected"
    assert(basAdmin.getSeverity(key1) == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Disconnected)) // alarm severity key expired
    assert(callbackSev == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Disconnected))

    // Test alarm in shelved state
    basAdmin.setShelvedState(key1, ShelvedState.Shelved)
    bas.setSeverity(key1, SeverityLevel.Warning)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    // getSeverity should return the severity that was set ...
    assert(basAdmin.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))
    // but the callback should not have been called, since alarm is shelved (callbackSev should have previous value)
    assert(callbackSev == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Disconnected))
    // un-shelve the alarm and try it again
    basAdmin.setShelvedState(key1, ShelvedState.Normal)
    bas.setSeverity(key1, SeverityLevel.Warning)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    assert(callbackSev == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))
    // Since the alarm is no longer shelved, the callback should be called this time
    assert(basAdmin.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))

    // Test alarm in deactivated state
    basAdmin.acknowledgeAndResetAlarm(key1)
    bas.setSeverity(key1, SeverityLevel.Okay)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    basAdmin.setActivationState(key1, ActivationState.OutOfService)
    bas.setSeverity(key1, SeverityLevel.Warning)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    assert(basAdmin.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))
    // callback should not have been called, callbackSev should have previous value
    assert(callbackSev == CurrentSeverity(SeverityLevel.Okay, SeverityLevel.Okay))
    // reactivate the alarm
    basAdmin.setActivationState(key1, ActivationState.Normal)
    bas.setSeverity(key1, SeverityLevel.Warning)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    assert(callbackSev == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))
    // This time the callback should have been called
    assert(basAdmin.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))

    // Test health monitor
    alarmMonitor.stop()
    Thread.sleep(shortDelayMs)
    val nfKey = AlarmKey(subsystemOpt = Some("nfiraos"))
    val healthMonitor = basAdmin.monitorAlarms(nfKey, printAlarmStatus, printHealthStatus, notifyAll = false)
    Thread.sleep(shortDelayMs) // make sure actor has started
    bas.setSeverity(key2, SeverityLevel.Okay)
    bas.setSeverity(key3, SeverityLevel.Okay)
    Thread.sleep(shortDelayMs)
    assert(basAdmin.getHealth(nfKey) == Health.Good)
    assert(callbackHealth.contains(Health.Good))

    Thread.sleep(delayMs) // wait for severity to expire and become "Disconnected"
    assert(callbackHealth.contains(Health.Bad))
    assert(basAdmin.getHealth(nfKey) == Health.Bad)
    assert(basAdmin.getHealth(AlarmKey()) == Health.Bad)

    bas.setSeverity(key2, SeverityLevel.Major)
    bas.setSeverity(key3, SeverityLevel.Okay)

    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    assert(callbackHealth.contains(Health.Ill))
    assert(basAdmin.getHealth(nfKey) == Health.Ill)
    basAdmin.acknowledgeAndResetAlarm(key2)

    bas.setSeverity(key2, SeverityLevel.Okay)
    bas.setSeverity(key3, SeverityLevel.Critical)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    assert(callbackHealth.contains(Health.Bad))
    assert(basAdmin.getHealth(nfKey) == Health.Bad)

    // Test error conditions: Try to set an alarm that does not exist
    assert(Try(basAdmin.getAlarm(badKey)).isFailure)
    assert(Try(bas.setSeverity(badKey, SeverityLevel.Critical)).isFailure)
    assert(Try(basAdmin.getSeverity(badKey)).isFailure)
    assert(Try(basAdmin.acknowledgeAndResetAlarm(badKey)).isFailure)
    assert(Try(basAdmin.getHealth(badKey)).isFailure)
    assert(Try(basAdmin.setShelvedState(badKey, ShelvedState.Normal)).isFailure)
    assert(Try(basAdmin.setActivationState(badKey, ActivationState.Normal)).isFailure)

    // Stop the actors monitoring the alarm and health
    healthMonitor.stop()
  }
}
