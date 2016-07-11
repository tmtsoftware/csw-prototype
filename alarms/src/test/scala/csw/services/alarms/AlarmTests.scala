package csw.services.alarms

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.alarms.AlarmModel.{AlarmStatus, Health, HealthStatus, SeverityLevel}
import csw.services.alarms.AlarmState.{ActivationState, ShelvedState}
import csw.services.alarms.AscfValidation.Problem
import csw.services.loc.LocationService
import csw.services.trackLocation.TrackLocation
import org.scalatest.FunSuiteLike

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

object AlarmTests {
  LocationService.initInterface()
  val system = ActorSystem("Test")
}

/**
 * Test the alarm APIs
 */
class AlarmTests extends TestKit(AlarmTests.system) with FunSuiteLike with LazyLogging {
  implicit val sys = AlarmTests.system

  import system.dispatcher

  implicit val timeout = Timeout(60.seconds)

  // Get the test alarm service config file (ascf)
  val url = getClass.getResource("/test-alarms.conf")
  val ascf = Paths.get(url.toURI).toFile

  test("Test initializing the alarm service, then set, get, list, monitor, acknowledge alarms") {
    // Note: This part is only for testing: Normally Redis would already be running and registered with the location service.
    // Start redis and register it with the location service on port 7777.
    // The following is the equivalent of running this from the command line:
    //   tracklocation --name "Alarm Service Test" --command "redis-server --port 7777" --port 7777
    val asName = "Alarm Service Test"
    val port = 7777
    Future {
      TrackLocation.main(Array("--name", asName, "--command", s"redis-server --port $port", "--port", port.toString))
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
    val alarmService = Await.result(AlarmService(asName, refreshSecs = refreshSecs), timeout.duration)

    try {
      // initialize the list of alarms in Redis
      val problems = Await.result(alarmService.initAlarms(ascf), timeout.duration)
      Problem.printProblems(problems)
      assert(Problem.errorCount(problems) == 0)

      // List all the alarms that were written to Redis
      val alarms = Await.result(alarmService.getAlarms(AlarmKey()), timeout.duration)
      alarms.foreach { alarm ⇒
        // XXX TODO: compare results
        logger.info(s"List Alarm: $alarm")
      }

      // Test internal function
      val map1 = Await.result(alarmService.asInstanceOf[AlarmServiceImpl].getHealthInfoMap(AlarmKey()), timeout.duration)
      println(s"Total map: $map1")
      assert(map1.size == 3)

      val map2 = Await.result(alarmService.asInstanceOf[AlarmServiceImpl].getHealthInfoMap(AlarmKey(Some("NFIRAOS"))), timeout.duration)
      println(s"NFIRAOS map: $map2")
      assert(map2.size == 2)

      // For testing callback
      var callbackSev: SeverityLevel = SeverityLevel.Indeterminate
      var callbackHealth: Option[Health] = None

      // Called when alarm severity changes
      def printAlarmStatus(alarmStatus: AlarmStatus): Unit = {
        val a = alarmStatus.alarmKey
        logger.info(s"Alarm Status: ${a.subsystem}:${a.component}:${a.name}: ${alarmStatus.severity}")
        callbackSev = alarmStatus.severity
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

      Await.ready(alarmService.setSeverity(key1, SeverityLevel.Critical), timeout.duration)
      Thread.sleep(delayMs) // wait for severity to expire

      assert(Await.result(alarmService.getSeverity(key1), timeout.duration) == SeverityLevel.Critical) // alarm is latched, so stays at critical
      assert(callbackSev == SeverityLevel.Critical)

      Await.ready(alarmService.setSeverity(key1, SeverityLevel.Warning), timeout.duration)
      assert(Await.result(alarmService.getSeverity(key1), timeout.duration) == SeverityLevel.Critical) // alarm is latched, so stays at critical
      assert(callbackSev == SeverityLevel.Critical)

      // Acknowledge the alarm, which clears it, resets it back to Okay
      Await.ready(alarmService.acknowledgeAlarm(key1), timeout.duration)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback, so the test below passes
      assert(Await.result(alarmService.getSeverity(key1), timeout.duration) == SeverityLevel.Okay) // alarm was cleared
      assert(callbackSev == SeverityLevel.Okay)

      Thread.sleep(delayMs) // wait for severity to expire and become "Indeterminate"
      assert(Await.result(alarmService.getSeverity(key1), timeout.duration) == SeverityLevel.Indeterminate) // alarm severity key expired
      assert(callbackSev == SeverityLevel.Indeterminate)

      // Test alarm in shelved state
      Await.ready(alarmService.setShelvedState(key1, ShelvedState.Shelved), timeout.duration)
      Await.ready(alarmService.setSeverity(key1, SeverityLevel.Warning), timeout.duration)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      assert(Await.result(alarmService.getSeverity(key1), timeout.duration) == SeverityLevel.Warning)
      assert(callbackSev == SeverityLevel.Indeterminate)
      Await.ready(alarmService.setShelvedState(key1, ShelvedState.Normal), timeout.duration)
      Await.ready(alarmService.setSeverity(key1, SeverityLevel.Warning), timeout.duration)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      assert(callbackSev == SeverityLevel.Warning)
      assert(Await.result(alarmService.getSeverity(key1), timeout.duration) == SeverityLevel.Warning)

      // Test alarm in deactivated state
      Await.ready(alarmService.acknowledgeAlarm(key1), timeout.duration)
      Await.ready(alarmService.setSeverity(key1, SeverityLevel.Okay), timeout.duration)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      Await.ready(alarmService.setActivationState(key1, ActivationState.OutOfService), timeout.duration)
      Await.ready(alarmService.setSeverity(key1, SeverityLevel.Warning), timeout.duration)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      assert(Await.result(alarmService.getSeverity(key1), timeout.duration) == SeverityLevel.Warning)
      assert(callbackSev == SeverityLevel.Okay)
      Await.ready(alarmService.setActivationState(key1, ActivationState.Normal), timeout.duration)
      Await.ready(alarmService.setSeverity(key1, SeverityLevel.Warning), timeout.duration)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      assert(callbackSev == SeverityLevel.Warning)
      assert(Await.result(alarmService.getSeverity(key1), timeout.duration) == SeverityLevel.Warning)

      // Test health monitor
      alarmMonitor.stop()
      Thread.sleep(shortDelayMs)
      val nfKey = AlarmKey(subsystemOpt = Some("NFIRAOS"))
      val healthMonitor = alarmService.monitorHealth(nfKey, None, Some(printAlarmStatus _), Some(printHealthStatus _))
      Thread.sleep(shortDelayMs) // make sure actor has started
      Await.ready(alarmService.setSeverity(key2, SeverityLevel.Okay), timeout.duration)
      Await.ready(alarmService.setSeverity(key3, SeverityLevel.Okay), timeout.duration)
      Thread.sleep(shortDelayMs)
      assert(Await.result(alarmService.getHealth(nfKey), timeout.duration) == Health.Good)
      assert(callbackHealth.contains(Health.Good))

      Thread.sleep(delayMs) // wait for severity to expire and become "Indeterminate"
      assert(callbackHealth.contains(Health.Bad))
      assert(Await.result(alarmService.getHealth(nfKey), timeout.duration) == Health.Bad)
      assert(Await.result(alarmService.getHealth(AlarmKey()), timeout.duration) == Health.Bad)

      Await.ready(alarmService.setSeverity(key2, SeverityLevel.Major), timeout.duration)
      Await.ready(alarmService.setSeverity(key3, SeverityLevel.Okay), timeout.duration)

      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      assert(callbackHealth.contains(Health.Ill))
      assert(Await.result(alarmService.getHealth(nfKey), timeout.duration) == Health.Ill)
      Await.ready(alarmService.acknowledgeAlarm(key2), timeout.duration)

      Await.ready(alarmService.setSeverity(key2, SeverityLevel.Okay), timeout.duration)
      Await.ready(alarmService.setSeverity(key3, SeverityLevel.Critical), timeout.duration)
      Thread.sleep(shortDelayMs) // Give redis time to notify the callback
      assert(callbackHealth.contains(Health.Bad))
      assert(Await.result(alarmService.getHealth(nfKey), timeout.duration) == Health.Bad)

      // Test error conditions: Try to set an alarm that does not exist
      assert(Try(Await.result(alarmService.getAlarm(badKey), timeout.duration)).isFailure)
      assert(Try(Await.result(alarmService.setSeverity(badKey, SeverityLevel.Critical), timeout.duration)).isFailure)
      assert(Try(Await.result(alarmService.getSeverity(badKey), timeout.duration)).isFailure)
      assert(Try(Await.result(alarmService.acknowledgeAlarm(badKey), timeout.duration)).isFailure)
      assert(Try(Await.result(alarmService.getHealth(badKey), timeout.duration)).isFailure)
      assert(Try(Await.result(alarmService.setShelvedState(badKey, ShelvedState.Normal), timeout.duration)).isFailure)
      assert(Try(Await.result(alarmService.setActivationState(badKey, ActivationState.Normal), timeout.duration)).isFailure)

      // Stop the actors monitoring the alarm and health
      healthMonitor.stop()
    } finally {
      // Shutdown Redis
      alarmService.shutdown()
    }
  }
}
