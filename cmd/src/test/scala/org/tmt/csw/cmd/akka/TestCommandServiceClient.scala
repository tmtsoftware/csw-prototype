package org.tmt.csw.cmd.akka

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorRef, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import com.typesafe.scalalogging.slf4j.Logging
import org.tmt.csw.cmd.core.{TestConfig, Configuration}
import akka.util.Timeout
import org.tmt.csw.cmd.spray.CommandServiceTestSettings
import scala.concurrent.Future

/**
 * Tests the Command Service actor
 */
class TestCommandServiceClient extends TestKit(ActorSystem("test")) with TestHelper
  with ImplicitSender with FunSuite with BeforeAndAfterAll with Logging {

  // The Configuration used in the tests below
  val config = Configuration(TestConfig.testConfig)

  // Note: Adjust this value and the one used by TestConfigActor
  // to match the time needed for the tests and avoid timeouts
  val duration = CommandServiceTestSettings(system).timeout

  implicit val timeout = Timeout(duration)
  implicit val dispatcher = system.dispatcher

  def getCommandServiceClientActor(n: Int = 1): ActorRef = {
    system.actorOf(CommandServiceClientActor.props(getCommandServiceActor(n), duration), name = s"testCommandServiceClientActor$n")
  }

  def getCommandServiceClient(n: Int = 1): CommandServiceClient = {
    CommandServiceClient(getCommandServiceClientActor(n), duration)
  }

  // -- Tests --


  test("Tests the command service client class") {
    val cmdClient = getCommandServiceClient()
    // Need to save any exception that occurs in another thread, so we can fail the test in this thread
    var savedException: Option[Exception] = None
    for {
      runId1 <- cmdClient.queueSubmit(config)
      status1 <- cmdClient.pollCommandStatus(runId1)

      _ <- Future.successful(cmdClient.queuePause())
      runId2 <- cmdClient.queueSubmit(config)
      status2a <- cmdClient.getCommandStatus(runId2)
      _ <- Future.successful(cmdClient.queueStart())
      status2b <- cmdClient.pollCommandStatus(runId2)
    } {
      try {
        assert(status1 == CommandStatus.Complete(runId1))
        assert(status2a == CommandStatus.Queued(runId2))
        assert(status2b == CommandStatus.Complete(runId2))
      } catch {
        case e: Exception => savedException = Some(e)
      } finally {
        // If we don't call this, the call to system.awaitTermination() below will hang
        system.shutdown()
      }
    }

    // Wait for above to complete!
    system.awaitTermination()

    savedException match {
      case None => // OK
      case Some(e) => fail(e)
    }
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}
