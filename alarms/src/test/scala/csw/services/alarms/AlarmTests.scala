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
  private val system = ActorSystem("Test")
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

  test("Test initializing the alarm service and then listing the alarms") {
    // Start redis and register it with the location service on port 7777.
    // The following is the equivalent of running this from the command line:
    //   tracklocation --name "Alarm Service Test" --command "redis-server --port 7777" --port 7777
    val asName = "Alarm Service Test"
    val port = 7777
    Future {
      TrackLocation.main(Array("--name", asName, "--command", s"redis-server --port $port", "--port", port.toString))
    }

    // Later, in another JVM, initialize the list of alarms in Redis
    val alarmService = Await.result(AlarmService(asName), timeout.duration)
    try {
      val problems = Await.result(alarmService.initAlarms(ascf), timeout.duration)
      Problem.printProblems(problems)
      assert(Problem.errorCount(problems) == 0)

      // List the alarms that were written to Redis
      val alarms = Await.result(alarmService.getAlarms(), timeout.duration)
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
      alarmService.monitorAlarms(Some("TCS"), Some("tcsPk"), Some("cpuExceededAlarm"), None, Some(printAlarmStatus _))
      Thread.sleep(2000)
      val expireSecs = 1
      alarmService.setSeverity("TCS", "tcsPk", "cpuExceededAlarm", SeverityLevel.Critical, expireSecs)
      Thread.sleep(2000)
      val sev1 = Await.result(alarmService.getSeverity("TCS", "tcsPk", "cpuExceededAlarm"), timeout.duration)
      assert(sev1 == SeverityLevel.Indeterminate)
      assert(callbackSev == SeverityLevel.Indeterminate)
      alarmService.setSeverity("TCS", "tcsPk", "cpuExceededAlarm", SeverityLevel.Warning, expireSecs)
      Thread.sleep(200)
      val sev2 = Await.result(alarmService.getSeverity("TCS", "tcsPk", "cpuExceededAlarm"), timeout.duration)
      assert(sev2 == SeverityLevel.Warning)
      assert(callbackSev == SeverityLevel.Warning)
      Thread.sleep(2000)
      assert(callbackSev == SeverityLevel.Indeterminate)
    } catch {
      case e: Exception ⇒ e.printStackTrace()
    } finally {
      // Shutdown Redis
      alarmService.shutdown()
    }
  }
}
