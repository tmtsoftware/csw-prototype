package csw.services.cs.core.svn

import akka.actor.ActorSystem
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexServer
import csw.services.cs.akka.TestSvnRepo
import csw.services.cs.core.ConfigManagerTestHelper
import org.scalatest.FunSuite

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Tests the SvnConfigManager class
  */
class SvnConfigManagerTests extends FunSuite with LazyLogging {
  implicit val system = ActorSystem()

  test("Test creating a SvnConfigManager, storing and retrieving some files") {
    runTests(None, oversize = false)

    // Start the config service annex http server and wait for it to be ready for connections
    // (In normal operations, this server would already be running)
    val server = ConfigServiceAnnexServer()
    runTests(Some(server), oversize = true)
    server.shutdown()
  }

  def runTests(annexServer: Option[ConfigServiceAnnexServer], oversize: Boolean): Unit = {
    logger.info(s"\n\n--- Testing config service: oversize = $oversize ---\n")

    // create a test repository and use it to create the manager
    val manager = TestSvnRepo.getConfigManager()

    val result = ConfigManagerTestHelper.runTests(manager, oversize)

    Await.result(result, 30.seconds)
    if (annexServer.isDefined) {
      logger.info("Shutting down annex server")
      annexServer.get.shutdown()
    }
  }
}
