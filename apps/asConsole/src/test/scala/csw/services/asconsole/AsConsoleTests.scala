package csw.services.asconsole

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.LocationService
import csw.services.trackLocation.TrackLocation
import org.scalatest.FunSuiteLike

import scala.concurrent.duration._
import scala.concurrent.Future

object AsConsoleTests {
  LocationService.initInterface()
  private val system = ActorSystem("Test")
}

/**
 * Tests the command line asconsole application by calling its main method with options
 */
class AsConsoleTests extends TestKit(AsConsoleTests.system) with FunSuiteLike with LazyLogging {
  implicit val sys = AsConsoleTests.system

  import system.dispatcher

  //  implicit val timeout = Timeout(60.seconds)

  // Get the test alarm service config file (ascf)
  val url = getClass.getResource("/test-alarms.conf")
  val ascf = Paths.get(url.toURI).toFile

  test("Test initializing the alarm service and then listing the alarms") {
    // Start redis and register it with the location service on port 7777.
    // The following is the equivalent of running this from the command line:
    //   tracklocation --name "Alarm Service Test" --command "redis-server --port 7777" --port 7777
    val asName = "Alarm Service Test"
    val port = 7777
    Future {
      TrackLocation.main(Array("--name", asName, "--command", s"redis-server --port $port", "--port", port.toString, "--no-exit"))
    }

    // Later, in another JVM, run the asconsole command to initialize the Redis database from the alarm service config file.
    AsConsole.main(Array("--as-name", asName, "--init", ascf.getAbsolutePath, "--list", "--no-exit", "--shutdown"))
  }
}
