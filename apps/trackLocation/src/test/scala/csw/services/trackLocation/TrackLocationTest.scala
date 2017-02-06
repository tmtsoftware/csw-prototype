package csw.services.trackLocation

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.Connection.TcpConnection
import csw.services.loc.ConnectionType.TcpType
import csw.services.loc.LocationService.ResolvedTcpLocation
import csw.services.loc.{ComponentId, ComponentType, LocationService}
import org.scalatest.FunSuiteLike

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object TrackLocationTest {
  println("\nTrackLocationTest:\n")
  LocationService.initInterface()
  private val system = ActorSystem("Test")
}

/**
 * Test the trackLocation app in-line
 */
class TrackLocationTest extends TestKit(TrackLocationTest.system) with FunSuiteLike with LazyLogging {
  implicit val sys: ActorSystem = TrackLocationTest.system

  import system.dispatcher

  implicit val timeout = Timeout(60.seconds)

  test("Test with command line args") {
    logger.debug("Test1 started")
    val name = "test1"
    val port = 9999
    Future {
      TrackLocation.main(Array(
        "--name", name,
        "--command", "sleep 10",
        "--port", port.toString,
        "--no-exit"
      ))
    }

    val connection = TcpConnection(ComponentId(name, ComponentType.Service))
    val locationsReady = Await.result(LocationService.resolve(Set(connection)), timeout.duration)
    logger.debug(s"Found $locationsReady")
    assert(locationsReady.locations.size == 1)
    val loc = locationsReady.locations.head
    assert(loc.isResolved)
    assert(loc.connection.connectionType == TcpType)
    assert(loc.connection.componentId.name == name)
    val tcpLoc = loc.asInstanceOf[ResolvedTcpLocation]
    assert(tcpLoc.port == port)
    logger.debug(s"$name passed")
    logger.debug("Test1 done")
  }

  test("Test with config file") {
    logger.debug("Test2 started")
    val name = "test2"
    val port = 8888
    val url = getClass.getResource("/test2.conf")
    val configFile = Paths.get(url.toURI).toFile.getAbsolutePath

    Future {
      TrackLocation.main(Array("--name", name, "--no-exit", configFile))
    }

    val connection = TcpConnection(ComponentId(name, ComponentType.Service))
    val locationsReady = Await.result(LocationService.resolve(Set(connection)), timeout.duration)
    logger.debug(s"Found $locationsReady")
    assert(locationsReady.locations.size == 1)
    val loc = locationsReady.locations.head
    assert(loc.isResolved)
    assert(loc.connection.connectionType == TcpType)
    assert(loc.connection.componentId.name == name)
    val tcpLoc = loc.asInstanceOf[ResolvedTcpLocation]
    assert(tcpLoc.port == port)
    logger.debug(s"$name passed")
    logger.debug("Test2 done")
  }

  // XXX: This test is no longer valid, since the config service is expected to be already running in the test environment
  //  test("Test with config service") {
  //    logger.debug("Test3 started")
  //    val name = "test3"
  //    val path = "test3/test3.conf"
  //    val port = 8888
  //
  //    // create a test repository and use it to create the actor
  //    val settings = ConfigServiceSettings(system)
  //    val manager = TestRepo.getTestRepoConfigManager(settings)
  //    val csActor = system.actorOf(ConfigServiceActor.props(manager, registerWithLocationService = true), name = "configService")
  //    val csClient = new BlockingConfigServiceClient(ConfigServiceClient(csActor, settings.name))
  //    val appConfigStr =
  //      s"""
  //        |$name {
  //        |  command = sleep 10
  //        |  port = $port
  //        |}
  //      """.stripMargin
  //    csClient.create(new File(path), ConfigData(appConfigStr), oversize = false, "test")
  //
  //    Future {
  //      TrackLocation.main(Array("--name", name, "--no-exit", path))
  //    }
  //
  //    val connection = TcpConnection(ComponentId(name, ComponentType.Service))
  //    val locationsReady = Await.result(LocationService.resolve(Set(connection)), timeout.duration)
  //    logger.debug(s"Found $locationsReady")
  //    assert(locationsReady.locations.size == 1)
  //    val loc = locationsReady.locations.head
  //    assert(loc.isResolved)
  //    assert(loc.connection.connectionType == TcpType)
  //    assert(loc.connection.componentId.name == name)
  //    val tcpLoc = loc.asInstanceOf[ResolvedTcpLocation]
  //    assert(tcpLoc.port == port)
  //    logger.debug(s"$name passed")
  //    logger.debug("Test3 done")
  //  }
}

