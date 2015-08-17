package csw.services.cmd.spray

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Broadcast, FlowGraph, Sink }
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import csw.services.cmd.akka._
import csw.util.cfg.TestConfig
import org.scalatest.FunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Tests the Command Service HTTP/REST interface in an actor environment.
 */
class CommandServiceHttpServerTests extends TestKit(ActorSystem("test")) with CommandServiceHttpClient
    with TestHelper with FunSuiteLike {

  implicit val dispatcher = system.dispatcher

  implicit val materializer = ActorMaterializer()

  // The Configuration used in the tests below
  val config = TestConfig.testConfig

  val duration: FiniteDuration = 5.seconds

  // Settings
  val host = CommandServiceSettings(system).interface
  val port = CommandServiceSettings(system).port

  startCommandServiceHttpServer()

  // -- Tests --

  test("Test HTTP REST interface to Command Service") {

    Await.result(for {
      // test submitting a config to the command queue
      source1 ← queueSubmit(config)
      commandStatus1 ← source1.filter(_.done).runWith(Sink.head)

      // test requesting immediate execution of a config
      source2 ← queueBypassRequest(config)
      commandStatus2 ← source2.filter(_.done).runWith(Sink.head)

      // test pausing the queue, submitting a config and then restarting the queue
      res3a ← queuePause()
      source3 ← queueSubmit(config)
      commandStatus3 ← source3.runWith(Sink.head)
      res3b ← queueStart()

      //      // test submitting a config, pausing it and then canceling it (what is the status?)
      //      runId4 ← queueSubmit(config)
      //      res4a ← configPause(runId4)
      //      commandStatus4a ← getCommandStatus(runId4)
      //      res4b ← configCancel(runId4)
      //      commandStatus4b ← pollCommandStatus(runId4, 3)
      //
      //      // abort should fail, since command was already canceled
      //      res4c ← configAbort(runId4)
      //      commandStatus4c ← pollCommandStatus(runId4, 3)

    } yield {
      assert(commandStatus1.isInstanceOf[CommandStatus.Completed])
      assert(commandStatus2.isInstanceOf[CommandStatus.Completed])

      assert(res3a.status == StatusCodes.Accepted)
      assert(res3b.status == StatusCodes.Accepted)
      assert(commandStatus3.isInstanceOf[CommandStatus.Queued])
      // assert(commandStatus3b.isInstanceOf[CommandStatus.Completed])

      //      assert(res4a.status == StatusCodes.Accepted)
      //      checkReturnStatus("4a", commandStatus4a, runId4, CommandStatus.Busy(runId4))
      //      assert(res4b.status == StatusCodes.Accepted)
      //      checkReturnStatus("4b", commandStatus4b, runId4, CommandStatus.Canceled(runId4))
      //      assert(res4c.status == StatusCodes.Accepted)
      //      checkReturnStatus("4c", commandStatus4c, runId4, CommandStatus.Error(runId4, CommandServiceHttpServer.unknownRunIdMessage))

      system.shutdown()
    }, 10.seconds)
  }

  // -- Helper methods --

  //  // Checks the return status from a submit or request command
  //  def checkReturnStatus(name: String, commandStatus: CommandStatus, runId: RunId, expectedCommandStatus: CommandStatus): Unit = {
  //    logger.info(s"Received command$name status $commandStatus for submit command with runId $runId")
  //    if (commandStatus != expectedCommandStatus) {
  //      fail(s"Unexpected command status for test $name : $commandStatus")
  //    }
  //  }

  // Start the command service, passing it a command service actor, set up with two config actors that
  // will implement the commands.
  def startCommandServiceHttpServer(): Unit = {
    val numberOfSecondsToRun = 2 // Make this greater than CommandServiceTestSettings.timeout to test timeout handling
    val commandServiceActor = getCommandServiceActor(numberOfSecondsToRun)
    system.actorOf(CommandServiceHttpServer.props(commandServiceActor, host, port, 5.seconds), "commandService")
    Thread.sleep(1000) // XXX need a way to wait until the server is ready before proceeding
  }
}

