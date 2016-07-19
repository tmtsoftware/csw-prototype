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
import scala.util.{Failure, Success}

/**
 * Tests the Config Service actor
 */
class ConfigServiceClientTests extends TestKit(ActorSystem("mySystem"))
    with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {

  import system.dispatcher

  implicit val timeout: Timeout = 60.seconds

  test("Test the ConfigServiceClent, storing and retrieving some files") {
    val settings = ConfigServiceSettings(ConfigFactory.load("test.conf"))
    val settings2 = ConfigServiceSettings(ConfigFactory.load("test2.conf"))

    // Start the config service annex http server and wait for it to be ready for connections
    // (In normal operations, this server would already be running)
    val annexServer = ConfigServiceAnnexServer()

    try {
      val f = for {
        _ <- runTests(settings, oversize = false)
        _ <- runTests2(settings2, oversize = false)
        //        _ <- runTests3(List(settings, settings2), oversize = false)

        _ <- runTests(settings, oversize = true)
        _ <- runTests2(settings2, oversize = true)
        //        _ <- runTests3(List(settings, settings2), oversize = true)
      } yield ()

      f.onComplete {
        case Success(_)  => logger.info("Success")
        case Failure(ex) => logger.error("Failure", ex)
      }

      Await.ready(f, 600.seconds)

    } finally {
      logger.info("Shutting down annex server")
      annexServer.shutdown()
    }
  }

  // Runs the tests for the config service, using the given oversize option.
  def runTests(settings: ConfigServiceSettings, oversize: Boolean): Future[Unit] = {
    logger.info(s"--- Testing config service: oversize = $oversize ---")

    // create a test repository and use it to create the actor
    val manager = TestRepo.getTestRepoConfigManager(settings)

    // Create the actor
    val csActor = system.actorOf(ConfigServiceActor.props(manager), name = "configService")
    val csClient = ConfigServiceClient(csActor, settings.name)

    val result = ConfigManagerTestHelper.runTests(csClient, oversize)
    result.onComplete {
      case _ => system.stop(csActor)
    }
    result
  }

  // Verify that a second config service can still see all the files that were checked in by the first
  def runTests2(settings: ConfigServiceSettings, oversize: Boolean): Future[Unit] = {
    logger.info(s"--- Verify config service: oversize = $oversize ---")

    // create a test repository and use it to create the actor
    GitConfigManager.deleteDirectoryRecursively(settings.localRepository)
    val manager = settings.getConfigManager

    // Create the actor
    val csActor = system.actorOf(ConfigServiceActor.props(manager), name = "configService2")
    val csClient = ConfigServiceClient(csActor, settings.name)

    val result = ConfigManagerTestHelper.runTests2(csClient, oversize)
    result.onComplete {
      case _ => system.stop(csActor)
    }
    result
  }

  //  // Test concurrent access
  //  // (XXX: Doesn't make sense for svn case: There should only be one cs actor per repo)
  //  def runTests3(settings: List[ConfigServiceSettings], oversize: Boolean): Future[Unit] = {
  //    logger.info(s"--- Test concurrent access: oversize = $oversize ---")
  //
  //    val managers = settings.map(_.getConfigManager)
  //
  //    // Create the actor
  //    val csActors = managers.map(manager => system.actorOf(ConfigServiceActor.props(manager)))
  //    val csClients = csActors.zip(settings).map(p => ConfigServiceClient(p._1, p._2.name))
  //    val result = ConfigManagerTestHelper.concurrentTest(csClients, oversize)
  //    result.onComplete {
  //      case _ => csActors.foreach(csActor => system.stop(csActor))
  //    }
  //    result
  //  }

  override def afterAll(): Unit = {
    //    system.terminate()
  }
}
