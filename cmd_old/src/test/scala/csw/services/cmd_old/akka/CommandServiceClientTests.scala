package csw.services.cmd_old.akka

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.testkit.{ ImplicitSender, TestKit }
import akka.actor.{ ActorRef, ActorSystem }
import csw.services.cmd_old.spray.CommandServiceSettings
import csw.shared.cmd.CommandStatus
import csw.util.cfg_old.TestConfig
import org.scalatest.{ FunSuiteLike, BeforeAndAfterAll }
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Tests the Command Service Client actor
 */
class CommandServiceClientTests extends TestKit(ActorSystem("test")) with TestHelper
    with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {

  import system.dispatcher

  // The Configuration used in the tests below
  val config = TestConfig.testConfig

  // Note: Adjust this value and the one used by TestConfigActor
  // to match the time needed for the tests and avoid timeouts
  val duration = CommandServiceSettings(system).timeout

  implicit val materializer = ActorMaterializer()

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
      source1 ← cmdClient.queueSubmit(config)
      status1 ← source1.filter(_.done).runWith(Sink.head)
      _ ← Future.successful(cmdClient.queuePause())
      source2 ← cmdClient.queueSubmit(config)
      status2a ← source2.runWith(Sink.head)
      _ ← Future.successful(cmdClient.queueStart())
      //      status2b ← source2.runWith(Sink.head)
    } yield {
      assert(status1.isInstanceOf[CommandStatus.Completed])
      assert(status2a.isInstanceOf[CommandStatus.Queued])
      //      assert(status2b.isInstanceOf[CommandStatus.Completed])
      //      assert(status2a.runId == status2b.runId)
    }, 5.seconds)
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}
