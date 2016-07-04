package csw.services.alarms

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import AlarmService.Problem
import csw.services.alarms.AlarmModel.{AlarmStatus, SeverityLevel}
import csw.services.loc.LocationService
import csw.services.trackLocation.TrackLocation
import org.scalatest.FunSuiteLike

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

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

  test("Test validating the alarm service config file") {
    val problems = AlarmService.validate(ascf)
    problems.foreach(p ⇒ println(p.toString))
    assert(Problem.errorCount(problems) == 0)
  }

  test("Test initializing the alarm service, then set, get, list, monitor, acknowledge alarms") {
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

    // Time until alarm severity expires
    val expireSecs = refreshSecs * AlarmService.maxMissedRefresh

    // Time of ms to wait to see if an alarm severity expired
    val delayMs = expireSecs*1000+500

    // Later, in another JVM, initialize the list of alarms in Redis (using a small value for refreshSecs for testing)
    val alarmService = Await.result(AlarmService(asName, refreshSecs = refreshSecs), timeout.duration)
    try {
      val problems = Await.result(alarmService.initAlarms(ascf), timeout.duration)
      Problem.printProblems(problems)
      assert(Problem.errorCount(problems) == 0)

      // List all the alarms that were written to Redis
      val alarms = Await.result(alarmService.getAlarms(AlarmKey()), timeout.duration)
      alarms.foreach { alarm ⇒
        // XXX TODO: compare results
        logger.info(s"List Alarm: $alarm")
      }

      // For testing callback
      var callbackSev: SeverityLevel = SeverityLevel.Indeterminate

      // Called when alarm severity changes
      def printAlarmStatus(alarmStatus: AlarmStatus): Unit = {
        val a = alarmStatus.alarm
        logger.info(s"Alarm Status: ${a.subsystem}:${a.component}:${a.name}: ${alarmStatus.severity}")
        callbackSev = alarmStatus.severity
      }

      // Test setting and monitoring the alarm severity level
      val key = AlarmKey("TCS", "tcsPk", "cpuExceededAlarm")
      val alarmMonitor = alarmService.monitorAlarms(key, None, Some(printAlarmStatus _))
      Thread.sleep(1000) // make sure actor has started

      Await.ready(alarmService.setSeverity(key, SeverityLevel.Critical), timeout.duration)
      Thread.sleep(delayMs) // wait for severity to expire

      val sev1 = Await.result(alarmService.getSeverity(key), timeout.duration)
      assert(sev1 == SeverityLevel.Critical) // alarm is latched, so stays at critical
      assert(callbackSev == SeverityLevel.Critical)

      Await.ready(alarmService.setSeverity(key, SeverityLevel.Warning), timeout.duration)
      val sev2 = Await.result(alarmService.getSeverity(key), timeout.duration)
      assert(sev2 == SeverityLevel.Critical) // alarm is latched, so stays at critical
      assert(callbackSev == SeverityLevel.Critical)

      // Acknowledge the alarm, which clears it, resets it back to Okay
      Await.ready(alarmService.acknowledgeAlarm(key), timeout.duration)

      Thread.sleep(500) // Give redis time to notify the callback, so the test below passes
      val sev3 = Await.result(alarmService.getSeverity(key), timeout.duration)
      assert(sev3 == SeverityLevel.Okay) // alarm was cleared
      assert(callbackSev == SeverityLevel.Okay)

      Thread.sleep(delayMs) // wait for severity to expire and become "Indeterminate"
      val sev4 = Await.result(alarmService.getSeverity(key), timeout.duration)
      assert(sev4 == SeverityLevel.Indeterminate) // alarm severity key expired
      assert(callbackSev == SeverityLevel.Indeterminate)

      // Stop the actor monitoring the alarm
      alarmMonitor.stop()
    } finally {
      // Shutdown Redis
      alarmService.shutdown()
    }
  }
}
