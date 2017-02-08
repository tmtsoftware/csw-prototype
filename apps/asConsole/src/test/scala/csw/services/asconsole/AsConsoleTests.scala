//package csw.services.asconsole
//
//import java.nio.file.Paths
//
//import akka.actor.ActorSystem
//import akka.testkit.TestKit
//import com.typesafe.scalalogging.LazyLogging
//import csw.services.loc.LocationService
//import csw.services.trackLocation.TrackLocation
//import org.scalatest.FunSuiteLike
//
//import scala.concurrent.Future
//
//object AsConsoleTests {
//  LocationService.initInterface()
//  private val system = ActorSystem("Test")
//}
//
///**
// * Tests the command line asconsole application by calling its main method with options
// */
//class AsConsoleTests extends TestKit(AsConsoleTests.system) with FunSuiteLike with LazyLogging {
//  implicit val sys = AsConsoleTests.system
//
//  import system.dispatcher
//
//  //  implicit val timeout = Timeout(60.seconds)
//
//  // Get the test alarm service config file (ascf)
//  val url = getClass.getResource("/test-alarms.conf")
//  val ascf = Paths.get(url.toURI).toFile
//
//  test("Test initializing the alarm service and then listing the alarms") {
//    // Start redis and register it with the location service on a random port.
//    // The following is the equivalent of running this from the command line:
//    //   tracklocation --name "Alarm Service Test" --command "redis-server --port %port"
//    //    val asName = "Alarm Service Test"
//    //    Future {
//    //      TrackLocation.main(Array("--name", asName, "--command", s"redis-server --protected-mode no --port %port", "--no-exit"))
//    //    }
//
//    // Later, in another JVM, run the asconsole command to initialize the Redis database from the alarm service config file.
//    AsConsole.main(Array("--init", ascf.getAbsolutePath, "--list", "--no-exit", "--shutdown")) // XXX only use --shutdown if you start it!
//
//    // XXX TODO: Test the options
//  }
//}
