package csw.services.cs.core

import akka.actor.ActorSystem
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer
import csw.services.cs.akka.{TestRepo, TestSvnRepo, ConfigServiceSettings, TestGitRepo}
import org.scalatest.FunSuite

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Tests the ConfigManager class
 */
class ConfigManagerTests extends FunSuite with LazyLogging {
  implicit val system = ActorSystem()

  test("Test creating a ConfigManager, storing and retrieving some files") {
    runTests(None, oversize = false)

    // Start the config service annex http server and wait for it to be ready for connections
    // (In normal operations, this server would already be running)
    val server = ConfigServiceAnnexServer()
    try {
      runTests(Some(server), oversize = true)
    } finally {
      server.shutdown()
    }
  }

  def runTests(annexServer: Option[ConfigServiceAnnexServer], oversize: Boolean): Unit = {
    logger.debug(s"\n\n--- Testing config service: oversize = $oversize ---\n")

    // create a test git or svn repository and use it to create the manager
    val settings = ConfigServiceSettings(ActorSystem())
    val manager = TestRepo.getTestRepoConfigManager(settings)

    val result = ConfigManagerTestHelper.runTests(manager, oversize)

    Await.result(result, 30.seconds)
    if (annexServer.isDefined) {
      logger.debug("Shutting down annex server")
      annexServer.get.shutdown()
    }
  }
}
