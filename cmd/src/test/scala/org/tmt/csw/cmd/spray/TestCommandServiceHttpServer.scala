package org.tmt.csw.cmd.spray

import akka.testkit.TestKit
import akka.actor.ActorSystem
import org.scalatest.FunSuiteLike
import scala.concurrent.duration._
import org.tmt.csw.cmd.akka._
import spray.http.StatusCodes
import org.tmt.csw.util.{TestConfig, Configuration}
import scala.concurrent.Await

/**
 * Tests the Command Service HTTP/REST interface in an actor environment.
 */
class TestCommandServiceHttpServer extends TestKit(ActorSystem("test")) with CommandServiceHttpClient
  with TestHelper with FunSuiteLike {

  implicit val dispatcher = system.dispatcher

  // The Configuration used in the tests below
  val config = Configuration(TestConfig.testConfig)

  val duration: FiniteDuration = 5.seconds

  // Settings
  val interface = CommandServiceTestSettings(system).interface
  val port = CommandServiceTestSettings(system).port

  startCommandServiceHttpServer()


  // -- Tests --

  test("Test HTTP REST interface to Command Service") {

    Await.result(for {
      // test submitting a config to the command queue
      runId1 <- queueSubmit(config)
      commandStatus1 <- pollCommandStatus(runId1, 3)

      // test requesting immediate execution of a config
      runId2 <- queueBypassRequest(config)
      commandStatus2 <- pollCommandStatus(runId2, 3)

      // test pausing the queue, submitting a config and then restarting the queue
      res3a <- queuePause()
      runId3 <- queueSubmit(config)
      commandStatus3a <- getCommandStatus(runId3)
      res3b <- queueStart()
      commandStatus3b <- pollCommandStatus(runId3, 3)

      // Attempting to get the status of an old or unknown command runId should an error
      commandStatus3c <- pollCommandStatus(runId3, 3)

      // test submitting a config, pausing it and then canceling it (what is the status?)
      runId4 <- queueSubmit(config)
      res4a <- configPause(runId4)
      commandStatus4a <- getCommandStatus(runId4)
      res4b <- configCancel(runId4)
      commandStatus4b <- pollCommandStatus(runId4, 3)

      // abort should fail, since command was already canceled
      res4c <- configAbort(runId4)
      commandStatus4c <- pollCommandStatus(runId4, 3)

    } yield {
      // At this point all of the above futures have completed: check the results
      checkReturnStatus("1", commandStatus1, runId1, CommandStatus.Completed(runId1))

      checkReturnStatus("2", commandStatus2, runId2, CommandStatus.Completed(runId2))

      assert(res3a.status == StatusCodes.Accepted)
      checkReturnStatus("3a", commandStatus3a, runId3, CommandStatus.Queued(runId3))
      assert(res3b.status == StatusCodes.Accepted)
      checkReturnStatus("3b", commandStatus3b, runId3, CommandStatus.Completed(runId3))
      checkReturnStatus("3c", commandStatus3c, runId3, CommandStatus.Error(runId3, CommandServiceHttpServer.unknownRunIdMessage))

      assert(res4a.status == StatusCodes.Accepted)
      checkReturnStatus("4a", commandStatus4a, runId4, CommandStatus.Busy(runId4))
      assert(res4b.status == StatusCodes.Accepted)
      checkReturnStatus("4b", commandStatus4b, runId4, CommandStatus.Canceled(runId4))
      assert(res4c.status == StatusCodes.Accepted)
      checkReturnStatus("4c", commandStatus4c, runId4, CommandStatus.Error(runId4, CommandServiceHttpServer.unknownRunIdMessage))
    }, 10.seconds)
  }

  // -- Helper methods --

  // Checks the return status from a submit or request command
  def checkReturnStatus(name: String, commandStatus: CommandStatus, runId: RunId, expectedCommandStatus: CommandStatus): Unit = {
    logger.info(s"Received command$name status $commandStatus for submit command with runId $runId")
    if (commandStatus != expectedCommandStatus) {
      fail(s"Unexpected command status for test $name : $commandStatus")
    }
  }

  // Start the command service, passing it a command service actor, set up with two config actors that
  // will implement the commands.
  def startCommandServiceHttpServer(): Unit = {
    // val numberOfSecondsToRun = 12 // Make this greater than CommandServiceTestSettings.timeout to test timeout handling
    val numberOfSecondsToRun = 2 // Make this greater than CommandServiceTestSettings.timeout to test timeout handling
    val commandServiceActor = getCommandServiceActor(numberOfSecondsToRun)
    system.actorOf(CommandServiceHttpServer.props(commandServiceActor, interface, port, 5.seconds), "commandService")
    Thread.sleep(1000) // XXX need a way to wait until the server is ready before proceeding
  }
}

