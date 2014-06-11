package csw.services.cmd.akka

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorRef, ActorSystem}
import org.scalatest.{FunSuiteLike, BeforeAndAfterAll}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.cmd.spray.CommandServiceTestSettings
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import csw.util.{TestConfig, Configuration}

/**
 * Tests the Command Service Client actor
 */
class TestCommandServiceClient extends TestKit(ActorSystem("test")) with TestHelper
with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {

  import system.dispatcher

  // The Configuration used in the tests below
  val config = Configuration(TestConfig.testConfig)

  // Note: Adjust this value and the one used by TestConfigActor
  // to match the time needed for the tests and avoid timeouts
  val duration = CommandServiceTestSettings(system).timeout

  def getCommandServiceClientActor: ActorRef = {
    system.actorOf(CommandServiceClientActor.props(getCommandServiceActor(), duration),
      name = s"testCommandServiceClientActor")
  }

  def getCommandServiceClient: CommandServiceClient = {
    CommandServiceClient(getCommandServiceClientActor, duration)
  }

  // -- Tests --


  test("Tests the command service client class") {
    val cmdClient = getCommandServiceClient
    Await.result(for {
      runId1 <- cmdClient.queueSubmit(config)
      status1 <- cmdClient.pollCommandStatus(runId1)
      _ <- Future.successful(cmdClient.queuePause())
      runId2 <- cmdClient.queueSubmit(config)
      status2a <- cmdClient.getCommandStatus(runId2)
      _ <- Future.successful(cmdClient.queueStart())
      status2b <- cmdClient.pollCommandStatus(runId2)
    } yield {
      assert(status1 == CommandStatus.Completed(runId1))
      assert(status2a == CommandStatus.Queued(runId2))
      assert(status2b == CommandStatus.Completed(runId2))
    }, 5.seconds)
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}
