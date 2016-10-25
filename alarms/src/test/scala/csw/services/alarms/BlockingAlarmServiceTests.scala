package csw.services.alarms

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
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

  // Set a low refresh rate for the test
  val refreshSecs = 1
  //    val refreshSecs = 5

  // Get the test alarm service config file (ascf)
  val url = getClass.getResource("/test-alarms.conf")
  val ascf = Paths.get(url.toURI).toFile
  val asName = "Blocking Alarm Service Test"

  // Used to start and stop the alarm service Redis instance used for the test
  var alarmAdmin: AlarmServiceAdmin = _

  // Get the alarm service by looking up the name with the location service.
  var alarmService: AlarmService = _

  override protected def beforeAll(): Unit = {
    // Note: This part is only for testing: Normally Redis would already be running and registered with the location service.
    // Start redis and register it with the location service on a random free port.
    // The following is the equivalent of running this from the command line:
    //   tracklocation --name "Alarm Service Test" --command "redis-server --port %port"
    AlarmServiceAdmin.startAlarmService(asName)
    // Get the alarm service by looking up the name with the location service.
    // (using a small value for refreshSecs for testing)
    alarmService = Await.result(AlarmService(asName, refreshSecs = refreshSecs), timeout.duration)
    alarmAdmin = AlarmServiceAdmin(alarmService)
  }

  override protected def afterAll(): Unit = {
    // Shutdown Redis (Only do this in tests that also started the server)
    if (alarmAdmin != null) Await.ready(alarmAdmin.shutdown(), timeout.duration)
  }

  test("Test initializing the alarm service, then set, get, list, monitor, acknowledge alarms") {

    // Time until alarm severity expires
    val expireSecs = refreshSecs * AlarmService.maxMissedRefresh

    // Time in ms to wait for a Redis callback
    val shortDelayMs = 500 * refreshSecs

    // Time in ms to wait to see if an alarm severity expired
    val delayMs = expireSecs * 1000 * refreshSecs + shortDelayMs

    val bas = BlockingAlarmService(asName, refreshSecs = refreshSecs)

    // initialize the list of alarms in Redis (This is only for the test and should not be done by normal clients)
    val problems = Await.result(alarmAdmin.initAlarms(ascf), timeout.duration)
    Problem.printProblems(problems)
    assert(Problem.errorCount(problems) == 0)

    // List all the alarms that were written to Redis
    val alarms = bas.getAlarms(AlarmKey())
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
    val key2 = AlarmKey("NFIRAOS", "envCtrl", "minTemperature")
    val key3 = AlarmKey("NFIRAOS", "envCtrl", "maxTemperature")
    val badKey = AlarmKey("XXX", "xxx", "xxx")

    val alarmMonitor = bas.monitorAlarms(key1, None, Some(printAlarmStatus _), Some(printHealthStatus _))
    Thread.sleep(shortDelayMs) // make sure actor has started

    bas.setSeverity(key1, SeverityLevel.Critical)
    Thread.sleep(delayMs) // wait for severity to expire

    // alarm is latched, so stays at critical
    assert(bas.getSeverity(key1) == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Critical))
    assert(callbackSev == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Critical))

    bas.setSeverity(key1, SeverityLevel.Warning)
    // alarm is latched, so stays at critical
    assert(bas.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Critical))
    Thread.sleep(shortDelayMs)
    assert(callbackSev == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Critical))

    // Acknowledge the alarm, which clears it, resets it back to Okay
    bas.acknowledgeAndResetAlarm(key1)
    bas.setSeverity(key1, SeverityLevel.Okay)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback, so the test below passes
    assert(bas.getSeverity(key1) == CurrentSeverity(SeverityLevel.Okay, SeverityLevel.Okay)) // alarm was cleared
    assert(callbackSev == CurrentSeverity(SeverityLevel.Okay, SeverityLevel.Okay))

    Thread.sleep(delayMs) // wait for severity to expire and become "Disconnected"
    assert(bas.getSeverity(key1) == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Disconnected)) // alarm severity key expired
    assert(callbackSev == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Disconnected))

    // Test alarm in shelved state
    bas.setShelvedState(key1, ShelvedState.Shelved)
    bas.setSeverity(key1, SeverityLevel.Warning)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    // getSeverity should return the severity that was set ...
    assert(bas.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))
    // but the callback should not have been called, since alarm is shelved (callbackSev should have previous value)
    assert(callbackSev == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Disconnected))
    // un-shelve the alarm and try it again
    bas.setShelvedState(key1, ShelvedState.Normal)
    bas.setSeverity(key1, SeverityLevel.Warning)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    assert(callbackSev == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))
    // Since the alarm is no longer shelved, the callback should be called this time
    assert(bas.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))

    // Test alarm in deactivated state
    bas.acknowledgeAndResetAlarm(key1)
    bas.setSeverity(key1, SeverityLevel.Okay)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    bas.setActivationState(key1, ActivationState.OutOfService)
    bas.setSeverity(key1, SeverityLevel.Warning)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    assert(bas.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))
    // callback should not have been called, callbackSev should have previous value
    assert(callbackSev == CurrentSeverity(SeverityLevel.Okay, SeverityLevel.Okay))
    // reactivate the alarm
    bas.setActivationState(key1, ActivationState.Normal)
    bas.setSeverity(key1, SeverityLevel.Warning)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    assert(callbackSev == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))
    // This time the callback should have been called
    assert(bas.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))

    // Test health monitor
    alarmMonitor.stop()
    Thread.sleep(shortDelayMs)
    val nfKey = AlarmKey(subsystemOpt = Some("NFIRAOS"))
    val healthMonitor = bas.monitorAlarms(nfKey, None, Some(printAlarmStatus _), Some(printHealthStatus _))
    Thread.sleep(shortDelayMs) // make sure actor has started
    bas.setSeverity(key2, SeverityLevel.Okay)
    bas.setSeverity(key3, SeverityLevel.Okay)
    Thread.sleep(shortDelayMs)
    assert(bas.getHealth(nfKey) == Health.Good)
    assert(callbackHealth.contains(Health.Good))

    Thread.sleep(delayMs) // wait for severity to expire and become "Disconnected"
    assert(callbackHealth.contains(Health.Bad))
    assert(bas.getHealth(nfKey) == Health.Bad)
    assert(bas.getHealth(AlarmKey()) == Health.Bad)

    bas.setSeverity(key2, SeverityLevel.Major)
    bas.setSeverity(key3, SeverityLevel.Okay)

    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    assert(callbackHealth.contains(Health.Ill))
    assert(bas.getHealth(nfKey) == Health.Ill)
    bas.acknowledgeAndResetAlarm(key2)

    bas.setSeverity(key2, SeverityLevel.Okay)
    bas.setSeverity(key3, SeverityLevel.Critical)
    Thread.sleep(shortDelayMs) // Give redis time to notify the callback
    assert(callbackHealth.contains(Health.Bad))
    assert(bas.getHealth(nfKey) == Health.Bad)

    // Test error conditions: Try to set an alarm that does not exist
    assert(Try(bas.getAlarm(badKey)).isFailure)
    assert(Try(bas.setSeverity(badKey, SeverityLevel.Critical)).isFailure)
    assert(Try(bas.getSeverity(badKey)).isFailure)
    assert(Try(bas.acknowledgeAndResetAlarm(badKey)).isFailure)
    assert(Try(bas.getHealth(badKey)).isFailure)
    assert(Try(bas.setShelvedState(badKey, ShelvedState.Normal)).isFailure)
    assert(Try(bas.setActivationState(badKey, ActivationState.Normal)).isFailure)

    // Stop the actors monitoring the alarm and health
    healthMonitor.stop()
  }
}
