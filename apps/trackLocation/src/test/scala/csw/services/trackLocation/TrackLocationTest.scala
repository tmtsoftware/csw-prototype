package csw.services.trackLocation

import java.io.File
import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.cs.akka.{ConfigServiceActor, ConfigServiceClient, ConfigServiceSettings, TestRepo}
import csw.services.cs.core.ConfigData
import csw.services.loc.Connection.HttpConnection
import csw.services.loc.ConnectionType.HttpType
import csw.services.loc.LocationService.ResolvedHttpLocation
import csw.services.loc.{ComponentId, ComponentType, LocationService}
import org.scalatest.FunSuiteLike

import scala.concurrent.duration._
import scala.util._
import scala.concurrent.{Await, Future}

object TrackLocationTest {
  LocationService.initInterface()
  private val system = ActorSystem("Test")
}

/**
 * Test the trackLocation app in-line
 */
class TrackLocationTest extends TestKit(TrackLocationTest.system) with FunSuiteLike with LazyLogging {
  implicit val sys = TrackLocationTest.system

  import system.dispatcher

  implicit val timeout = Timeout(60.seconds)

  test("Test with command line args") {
    logger.info("Test1 started")
    val name = "test1"
    val port = 9999
    Future {
      TrackLocation.main(Array(
        "--name", name,
        "--command", "sleep 10",
        "--port", port.toString,
        "--no-exit", "true"
      ))
    }

    val connection = HttpConnection(ComponentId(name, ComponentType.Service))
    val locationsReady = Await.result(LocationService.resolve(Set(connection)), timeout.duration)
    logger.info(s"Found $locationsReady")
    assert(locationsReady.locations.size == 1)
    val loc = locationsReady.locations.head
    assert(loc.isResolved)
    assert(loc.connection.connectionType == HttpType)
    assert(loc.connection.componentId.name == name)
    val httpLoc = loc.asInstanceOf[ResolvedHttpLocation]
    assert(httpLoc.uri.getPort == port)
    logger.info(s"$name passed")
    logger.info("Test1 done")
  }

  test("Test with config file") {
    logger.info("Test2 started")
    val name = "test2"
    val port = 8888
    val url = getClass.getResource("/test2.conf")
    val configFile = Paths.get(url.toURI).toFile.getAbsolutePath

    Future {
      TrackLocation.main(Array("--name", name, configFile))
    }

    val connection = HttpConnection(ComponentId(name, ComponentType.Service))
    val locationsReady = Await.result(LocationService.resolve(Set(connection)), timeout.duration)
    logger.info(s"Found $locationsReady")
    assert(locationsReady.locations.size == 1)
    val loc = locationsReady.locations.head
    assert(loc.isResolved)
    assert(loc.connection.connectionType == HttpType)
    assert(loc.connection.componentId.name == name)
    val httpLoc = loc.asInstanceOf[ResolvedHttpLocation]
    assert(httpLoc.uri.getPort == port)
    logger.info(s"$name passed")
    logger.info("Test2 done")
  }

  test("Test with config service") {
    logger.info("Test3 started")
    val name = "test3"
    val path = "test3/test3.conf"
    val port = 8888

    // create a test repository and use it to create the actor
    val settings = ConfigServiceSettings(system)
    val manager = TestRepo.getTestRepoConfigManager(settings)
    val csActor = system.actorOf(ConfigServiceActor.props(manager), name = "configService")
    csActor ! ConfigServiceActor.RegisterWithLocationService
    val csClient = ConfigServiceClient(csActor, settings.name)
    val appConfigStr =
      s"""
        |$name {
        |  command = sleep 10
        |  port = $port
        |}
      """.stripMargin
    Await.ready(csClient.create(new File(path), ConfigData(appConfigStr), oversize = false, "test"), timeout.duration)

    Future {
      TrackLocation.main(Array("--name", name, path))
    }

    val connection = HttpConnection(ComponentId(name, ComponentType.Service))
    val locationsReady = Await.result(LocationService.resolve(Set(connection)), timeout.duration)
    logger.info(s"Found $locationsReady")
    assert(locationsReady.locations.size == 1)
    val loc = locationsReady.locations.head
    assert(loc.isResolved)
    assert(loc.connection.connectionType == HttpType)
    assert(loc.connection.componentId.name == name)
    val httpLoc = loc.asInstanceOf[ResolvedHttpLocation]
    assert(httpLoc.uri.getPort == port)
    logger.info(s"$name passed")
    logger.info("Test3 done")
  }
}

