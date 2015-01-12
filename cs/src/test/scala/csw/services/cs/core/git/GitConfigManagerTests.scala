package csw.services.cs.core.git

import akka.actor.ActorSystem
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer
import csw.services.cs.akka.TestRepo
import csw.services.cs.core.ConfigManagerTestHelper
import org.scalatest.FunSuite

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Tests the GitConfigManager class
 */
class GitConfigManagerTests extends FunSuite with LazyLogging {
  implicit val system = ActorSystem()

  test("Test creating a GitConfigManager, storing and retrieving some files") {
    runTests(None, oversize = false)

    // Start the config service annex http server and wait for it to be ready for connections
    // (In normal operations, this server would already be running)
    val server = ConfigServiceAnnexServer()
    runTests(Some(server), oversize = true)
  }

  def runTests(annexServer: Option[ConfigServiceAnnexServer], oversize: Boolean): Unit = {
    logger.info(s"\n\n--- Testing config service: oversize = $oversize ---\n")

    // create a test repository and use it to create the manager
    val manager = TestRepo.getConfigManager()

    val result = ConfigManagerTestHelper.runTests(manager, oversize)

    Await.result(result, 30.seconds)
    if (annexServer.isDefined) {
      logger.info("Shutting down annex server")
      annexServer.get.shutdown()
    }
  }
}
