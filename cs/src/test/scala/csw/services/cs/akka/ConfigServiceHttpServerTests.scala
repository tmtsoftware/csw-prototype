package csw.services.cs.akka

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer
import csw.services.cs.core.ConfigManagerTestHelper
import csw.services.cs.core.git.GitConfigManager
import csw.services.loc.LocationService
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.duration._

object ConfigServiceHttpServerTests {
  LocationService.initInterface()

  val system = ActorSystem("ConfigServiceHttpServerTests")
}

/**
 * Tests the Config Service Http server
 */
class ConfigServiceHttpServerTests extends TestKit(ConfigServiceHttpServerTests.system)
    with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {

  implicit val timeout: Timeout = 60.seconds

  test("Test the ConfigServiceClent, storing and retrieving some files") {
    val settings = ConfigServiceSettings(ConfigFactory.load("test.conf"))
    val settings2 = ConfigServiceSettings(ConfigFactory.load("test2.conf"))

    // Start the config service annex http server and wait for it to be ready for connections
    // (In normal operations, this server would already be running)
    val annexServer = ConfigServiceAnnexServer()
    try {
      runTests(settings, oversize = false)
      runTests2(settings2, oversize = false)

      runTests(settings, oversize = true)
      runTests2(settings2, oversize = true)
    } finally {
      logger.debug("Shutting down annex server")
      annexServer.shutdown()
    }
  }

  // Runs the tests for the config service, using the given oversize option.
  def runTests(settings: ConfigServiceSettings, oversize: Boolean): Unit = {
    logger.debug(s"--- Testing config service: oversize = $oversize ---")

    // create a test repository and use it to create the actor
    val manager = TestRepo.getTestRepoConfigManager(settings)

    // Create the actor
    val csActor = system.actorOf(ConfigServiceActor.props(manager))
    val server = ConfigServiceHttpServer(csActor, settings)
    val csClient = ConfigServiceHttpClient(settings)

    ConfigManagerTestHelper.runTests(csClient, oversize)
    system.stop(csActor)
    server.shutdown()
  }

  // Verify that a second config service can still see all the files that were checked in by the first
  def runTests2(settings: ConfigServiceSettings, oversize: Boolean): Unit = {
    logger.debug(s"--- Verify config service: oversize = $oversize ---")

    // create a test repository and use it to create the actor
    if (!settings.useSvn)
      GitConfigManager.deleteDirectoryRecursively(settings.localRepository)
    val manager = settings.getConfigManager

    // Create the actor
    val csActor = system.actorOf(ConfigServiceActor.props(manager))
    val csClient = ConfigServiceClient(csActor, settings.name)

    ConfigManagerTestHelper.runTests2(csClient, oversize)
    system.stop(csActor)
  }
}
