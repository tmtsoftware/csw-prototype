package org.tmt.csw.cmd.spray

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import scala.concurrent.duration._
import org.tmt.csw.cmd.core.{TestConfig, Configuration}
import org.tmt.csw.cmd.akka._
import spray.http.StatusCodes

/**
 * Tests the Command Service HTTP/REST interface in an actor environment.
 */
class TestCommandService extends TestKit(ActorSystem("test")) with CommandServiceClient
with ImplicitSender with FunSuite with BeforeAndAfterAll {

  // The Configuration used in the tests below
  val config = Configuration(TestConfig.testConfig)

  val duration: FiniteDuration = 5.seconds
  implicit val dispatcher = system.dispatcher

  // Settings
  val interface = CommandServiceTestSettings(system).interface
  val port = CommandServiceTestSettings(system).port
  implicit val timeout = CommandServiceTestSettings(system).timeout

  // Need to save any exception that occurs in another thread, so we can fail the test in this thread
  var savedException: Option[Exception] = None

  startCommandService()


  // -- Tests --

  test("Test HTTP REST interface to Command Service") {
    for {
    // test submitting a config to the command queue
      runId1 <- queueSubmit(config)
      commandStatus1 <- pollCommandStatus(runId1)

      // test requesting immediate execution of a config
      runId2 <- request(config)
      commandStatus2 <- pollCommandStatus(runId2)

      // test pausing the queue, submitting a config and then restarting the queue
      res3a <- queuePause()
      runId3 <- queueSubmit(config)
      commandStatus3a <- getCommandStatus(runId3)
      res3b <- queueStart()
      commandStatus3b <- pollCommandStatus(runId3)

      // Attempting to get the status of an old or unknown command runId should an error
      commandStatus3c <- pollCommandStatus(runId3)

      // test submitting a config, pausing it and then canceling it (what is the status?)
      runId4 <- queueSubmit(config)
      res4a <- configPause(runId4)
      commandStatus4a <- getCommandStatus(runId4)
      res4b <- configCancel(runId4)
      commandStatus4b <- pollCommandStatus(runId4)

      // abort should fail, since command was already canceled
      res4c <- configAbort(runId4)
      commandStatus4c <- pollCommandStatus(runId4)

    } try {
      // At this point all of the above futures have completed: check the results
      checkReturnStatus("1", commandStatus1, runId1, CommandStatus.Complete(runId1))

      checkReturnStatus("2", commandStatus2, runId2, CommandStatus.Complete(runId2))

      assert(res3a.status == StatusCodes.Accepted)
      checkReturnStatus("3a", commandStatus3a, runId3, CommandStatus.Queued(runId3))
      assert(res3b.status == StatusCodes.Accepted)
      checkReturnStatus("3b", commandStatus3b, runId3, CommandStatus.Complete(runId3))
      checkReturnStatus("3c", commandStatus3c, runId3, CommandStatus.Error(runId3, CommandService.unknownRunIdMessage))

      assert(res4a.status == StatusCodes.Accepted)
      checkReturnStatus("4a", commandStatus4a, runId4, CommandStatus.Busy(runId4))
      assert(res4b.status == StatusCodes.Accepted)
      checkReturnStatus("4b", commandStatus4b, runId4, CommandStatus.Canceled(runId4))
      assert(res4c.status == StatusCodes.Accepted)
      checkReturnStatus("4c", commandStatus4c, runId4, CommandStatus.Error(runId4, CommandService.unknownRunIdMessage))
    } catch {
      case e: Exception => savedException = Some(e)
    } finally {
      // If we don't call this, the call to system.awaitTermination() below will hang
      system.shutdown()
    }

    savedException match {
      case None => // OK
      case Some(e) => fail(e)
    }

    // Wait for above to complete!
    system.awaitTermination()
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
  def startCommandService(): Unit = {
    // Create a config service actor
    val commandServiceActor = system.actorOf(Props[CommandServiceActor], name = s"testCommandServiceActor")

    // Create 2 config actors, tell them to register with the command service actor and wait, before starting the test
    // (If we start sending commands before the registration is complete, they won't get executed).
    // Each config actor is responsible for a different part of the configs (the path passed as an argument).
    val configActor1 = system.actorOf(TestConfigActor.props("config.tmt.tel.base.pos"), name = s"TestConfigActorA")
    val configActor2 = system.actorOf(TestConfigActor.props("config.tmt.tel.ao.pos.one"), name = s"TestConfigActorB")
    within(duration) {
      // Note: this tells configActor1 to register with the command service. It could do this on its own,
      // (by using a known path to find the commandServiceActor) but doing it this way lets us know when
      // the registration is complete, so we can start sending commands
      configActor1 ! ConfigActor.Register(commandServiceActor)
      configActor2 ! ConfigActor.Register(commandServiceActor)
      expectMsgType[ConfigActor.Registered.type]
      expectMsgType[ConfigActor.Registered.type]
    }

    system.actorOf(CommandService.props(commandServiceActor, interface, port, timeout), "commandService")
    Thread.sleep(1000) // XXX
  }
}

