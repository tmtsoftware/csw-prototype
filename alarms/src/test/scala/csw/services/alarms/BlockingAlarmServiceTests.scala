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
import csw.services.trackLocation.TrackLocation
import org.scalatest.FunSuiteLike

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.Try

object BlockingAlarmServiceTests {
  LocationService.initInterface()
  val system = ActorSystem("Test")
}

/**
 * Test the blocking Alarm Service API
 */
class BlockingAlarmServiceTests extends TestKit(BlockingAlarmServiceTests.system) with FunSuiteLike with LazyLogging {
  implicit val sys = BlockingAlarmServiceTests.system

  import system.dispatcher

  implicit val timeout = Timeout(60.seconds)

  // Get the test alarm service config file (ascf)
  val url = getClass.getResource("/test-alarms.conf")
  val ascf = Paths.get(url.toURI).toFile

  test("Test initializing the alarm service, then set, get, list, monitor, acknowledge alarms") {
    // Note: This part is only for testing: Normally Redis would already be running and registered with the location service.
    // Start redis and register it with the location service on a random free port.
    // The following is the equivalent of running this from the command line:
    //   tracklocation --name "Alarm Service Test" --command "redis-server"
    val asName = "Blocking Alarm Service Test"
    Future {
      TrackLocation.main(Array("--name", asName, "--command", "redis-server --port %port", "--no-exit"))
    }

    // Set a low refresh rate for the test
    val refreshSecs = 1
    //    val refreshSecs = 5

    // Time until alarm severity expires
    val expireSecs = refreshSecs * AlarmService.maxMissedRefresh

    // Time in ms to wait for a Redis callback
    val shortDelayMs = 500 * refreshSecs

    // Time in ms to wait to see if an alarm severity expired
    val delayMs = expireSecs * 1000 * refreshSecs + shortDelayMs

    // Later, in another JVM...,
    // Get the alarm service by looking up the name with the location service.
    // (using a small value for refreshSecs for testing)
    val alarmService = BlockingAlarmService(asName, refreshSecs = refreshSecs)

    try {
      // initialize the list of alarms in Redis
      val problems = alarmService.initAlarms(ascf)
      Problem.printProblems(problems)
      assert(Problem.errorCount(problems) == 0)

      // List all the alarms that were written to Redis
      val alarms = alarmService.getAlarms(AlarmKey())
      alarms.foreach { alarm =>
        // XXX TODO: compare results
        logger.info(s"List Alarm: $alarm")
      }

      // For testing callback
      var callbackSev: CurrentSeverity = CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Disconnected)
      var callbackHealth: Option[Health] = None

      // Called when alarm severity changes
      def printAlarmStatus(alarmStatus: AlarmStatus): Unit = {
        val a = alarmStatus.alarmKey
        logger.info(s"Alarm Status: ${a.subsystem}:${a.component}:${a.name}: ${alarmStatus.currentSeverity}")
        callbackSev = alarmStatus.currentSeverity
      }

      // Called when the health status changes
      def printHealthStatus(healthStatus: HealthStatus): Unit = {
        val a = healthStatus.key
        logger.info(s"Health Status: ${a.subsystem}:${a.component}:${a.name}: ${healthStatus.health}")
        callbackHealth = Some(healthStatus.health)
      }

      // Test working with an alarm and monitoring the alarm severity level
      val key1 = AlarmKey("TCS", "tcsPk", "cpuExceededAlarm")
      val key2 = AlarmKey("NFIRAOS", "envCtrl", "minTemperature")
      val key3 = AlarmKey("NFIRAOS", "envCtrl", "maxTemperature")
      val badKey = AlarmKey("XXX", "xxx", "xxx")

      val alarmMonitor = alarmService.monitorHealth(key1, None, Some(printAlarmStatus _), Some(printHealthStatus _))
      Thread.sleep(shortDelayMs) // make sure actor has started

      alarmService.setSeverity(key1, SeverityLevel.Critical)
      Thread.sleep(delayMs) // wait for severity to expire

      // alarm is latched, so stays at critical
      assert(alarmService.getSeverity(key1) == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Critical))
      assert(callbackSev == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Critical))

      alarmService.setSeverity(key1, SeverityLevel.Warning)
      // alarm is latched, so stays at critical
      assert(alarmService.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Critical))
      Thread.sleep(shortDelayMs)
      assert(callbackSev == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Critical))

      // Acknowledge the alarm, which clears it, resets it back to Okay
      alarmService.acknowledgeAlarm(key1)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback, so the test below passes
      assert(alarmService.getSeverity(key1) == CurrentSeverity(SeverityLevel.Okay, SeverityLevel.Okay)) // alarm was cleared
      assert(callbackSev == CurrentSeverity(SeverityLevel.Okay, SeverityLevel.Okay))

      Thread.sleep(delayMs) // wait for severity to expire and become "Disconnected"
      assert(alarmService.getSeverity(key1) == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Disconnected)) // alarm severity key expired
      assert(callbackSev == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Disconnected))

      // Test alarm in shelved state
      alarmService.setShelvedState(key1, ShelvedState.Shelved)
      alarmService.setSeverity(key1, SeverityLevel.Warning)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      // getSeverity should return the severity that was set ...
      assert(alarmService.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))
      // but the callback should not have been called, since alarm is shelved (callbackSev should have previous value)
      assert(callbackSev == CurrentSeverity(SeverityLevel.Disconnected, SeverityLevel.Disconnected))
      // un-shelve the alarm and try it again
      alarmService.setShelvedState(key1, ShelvedState.Normal)
      alarmService.setSeverity(key1, SeverityLevel.Warning)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      assert(callbackSev == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))
      // Since the alarm is no longer shelved, the callback should be called this time
      assert(alarmService.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))

      // Test alarm in deactivated state
      alarmService.acknowledgeAlarm(key1)
      alarmService.setSeverity(key1, SeverityLevel.Okay)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      alarmService.setActivationState(key1, ActivationState.OutOfService)
      alarmService.setSeverity(key1, SeverityLevel.Warning)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      assert(alarmService.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))
      // callback should not have been called, callbackSev should have previous value
      assert(callbackSev == CurrentSeverity(SeverityLevel.Okay, SeverityLevel.Okay))
      // reactivate the alarm
      alarmService.setActivationState(key1, ActivationState.Normal)
      alarmService.setSeverity(key1, SeverityLevel.Warning)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      assert(callbackSev == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))
      // This time the callback should have been called
      assert(alarmService.getSeverity(key1) == CurrentSeverity(SeverityLevel.Warning, SeverityLevel.Warning))

      // Test health monitor
      alarmMonitor.stop()
      Thread.sleep(shortDelayMs)
      val nfKey = AlarmKey(subsystemOpt = Some("NFIRAOS"))
      val healthMonitor = alarmService.monitorHealth(nfKey, None, Some(printAlarmStatus _), Some(printHealthStatus _))
      Thread.sleep(shortDelayMs) // make sure actor has started
      alarmService.setSeverity(key2, SeverityLevel.Okay)
      alarmService.setSeverity(key3, SeverityLevel.Okay)
      Thread.sleep(shortDelayMs)
      assert(alarmService.getHealth(nfKey) == Health.Good)
      assert(callbackHealth.contains(Health.Good))

      Thread.sleep(delayMs) // wait for severity to expire and become "Disconnected"
      assert(callbackHealth.contains(Health.Bad))
      assert(alarmService.getHealth(nfKey) == Health.Bad)
      assert(alarmService.getHealth(AlarmKey()) == Health.Bad)

      alarmService.setSeverity(key2, SeverityLevel.Major)
      alarmService.setSeverity(key3, SeverityLevel.Okay)

      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      assert(callbackHealth.contains(Health.Ill))
      assert(alarmService.getHealth(nfKey) == Health.Ill)
      alarmService.acknowledgeAlarm(key2)

      alarmService.setSeverity(key2, SeverityLevel.Okay)
      alarmService.setSeverity(key3, SeverityLevel.Critical)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      assert(callbackHealth.contains(Health.Bad))
      assert(alarmService.getHealth(nfKey) == Health.Bad)

      // Test error conditions: Try to set an alarm that does not exist
      assert(Try(alarmService.getAlarm(badKey)).isFailure)
      assert(Try(alarmService.setSeverity(badKey, SeverityLevel.Critical)).isFailure)
      assert(Try(alarmService.getSeverity(badKey)).isFailure)
      assert(Try(alarmService.acknowledgeAlarm(badKey)).isFailure)
      assert(Try(alarmService.getHealth(badKey)).isFailure)
      assert(Try(alarmService.setShelvedState(badKey, ShelvedState.Normal)).isFailure)
      assert(Try(alarmService.setActivationState(badKey, ActivationState.Normal)).isFailure)

      // Stop the actors monitoring the alarm and health
      healthMonitor.stop()
    } finally {
      // Shutdown Redis
      alarmService.shutdown()
    }
  }
}
