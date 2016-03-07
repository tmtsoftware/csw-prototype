package csw.services.cs.akka

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer
import csw.services.cs.core.ConfigManagerTestHelper
import csw.services.cs.core.git.GitConfigManager
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
 * Tests the Config Service Http server
 */
class ConfigServiceHttpServerTests extends TestKit(ActorSystem("mySystem"))
    with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {

  import system.dispatcher

  implicit val timeout: Timeout = 60.seconds

  test("Test the ConfigServiceClent, storing and retrieving some files") {
    val settings = ConfigServiceSettings(ConfigFactory.load("test.conf"))
    val settings2 = ConfigServiceSettings(ConfigFactory.load("test2.conf"))

    // Start the config service annex http server and wait for it to be ready for connections
    // (In normal operations, this server would already be running)
    val annexServer = ConfigServiceAnnexServer()

    val f = for {
      _ ← runTests(settings, oversize = false)
      _ ← runTests2(settings2, oversize = false)

      _ ← runTests(settings, oversize = true)
      _ ← runTests2(settings2, oversize = true)
    } yield ()
    Await.ready(f, 60.seconds)
    logger.info("Shutting down annex server")
    annexServer.shutdown()
  }

  // Runs the tests for the config service, using the given oversize option.
  def runTests(settings: ConfigServiceSettings, oversize: Boolean): Future[Unit] = {
    logger.info(s"--- Testing config service: oversize = $oversize ---")

    // create a test repository and use it to create the actor
    val manager = TestGitRepo.getConfigManager(settings)

    // Create the actor
    val csActor = system.actorOf(ConfigServiceActor.props(manager))
    val server = ConfigServiceHttpServer(csActor, settings)
    val csClient = ConfigServiceHttpClient(settings)

    for {
      _ ← ConfigManagerTestHelper.runTests(csClient, oversize)
    } yield {
      system.stop(csActor)
      server.shutdown()
    }
  }

  // Verify that a second config service can still see all the files that were checked in by the first
  def runTests2(settings: ConfigServiceSettings, oversize: Boolean): Future[Unit] = {
    logger.info(s"--- Verify config service: oversize = $oversize ---")

    // create a test repository and use it to create the actor
    GitConfigManager.deleteDirectoryRecursively(settings.gitLocalRepository)
    val manager = GitConfigManager(settings.gitLocalRepository, settings.gitMainRepository, settings.name)

    // Create the actor
    val csActor = system.actorOf(ConfigServiceActor.props(manager))
    val csClient = ConfigServiceClient(csActor)

    for {
      _ ← ConfigManagerTestHelper.runTests2(csClient, oversize)
    } yield {
      system.stop(csActor)
    }
  }
}
