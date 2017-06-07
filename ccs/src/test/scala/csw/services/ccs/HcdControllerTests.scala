package csw.services.ccs

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.scalalogging.LazyLogging
import csw.services.ccs.HcdController.Submit
import csw.util.param.Parameters.{CommandInfo, Setup}
import csw.util.param.StateVariable.CurrentState
import csw.util.param.{ObsId, StringKey}
import org.scalatest.FunSuiteLike

import scala.concurrent.duration._

object HcdControllerTests {
  val system = ActorSystem("Test")

  val testPrefix = "wfos.blue.filter"
  val position = StringKey("position")

  object TestHcdController {
    def props(): Props = Props(new TestHcdController())
  }

  class TestHcdController extends HcdController with Actor with ActorLogging {
    // Use single worker actor to do work in the background
    // (could also use a worker per job/message if needed)
    private val worker = context.actorOf(TestWorker.props())

    // Send the command to the worker for processing
    override protected def process(command: Setup): Unit = {
      worker ! TestWorker.Work(command)
    }

    // Ask the worker actor to send us the current state (handled by parent trait)
    override protected def requestCurrent(): Unit = {
      worker ! TestWorker.RequestCurrentState
    }

    override def receive: Receive = controllerReceive
  }

  // -- Test worker actor that simulates doing some work --
  object TestWorker {
    def props(): Props = Props(new TestWorker())

    // Work to do
    case class Work(command: Setup)

    // Message sent to self to simulate work done
    case class WorkDone(command: Setup)

    // Message to request the current state values
    case object RequestCurrentState
  }

  class TestWorker extends Actor with ActorLogging {
    import TestWorker._
    import context.dispatcher

    // Simulate getting the initial state from the device
    private val initialState = CurrentState(testPrefix).add(position.set("None"))

    // Simulated current state
    private var currentState = initialState

    def receive: Receive = {
      case Work(command) =>
        // Simulate doing work
        log.debug(s"Start processing $command")
        context.system.scheduler.scheduleOnce(2.seconds, self, WorkDone(command))

      case RequestCurrentState =>
        log.debug(s"Requested current state")
        context.parent ! currentState

      case WorkDone(command) =>
        log.debug(s"Done processing $command")
        currentState = CurrentState(command.prefixStr, command.paramSet)
        context.parent ! currentState

      case x => log.error(s"Unexpected message $x")
    }
  }

}

// Tests sending a Setup to a test HCD, then starting a matcher actor to subscribe
// to the current state (a state variable updated by the HCD). When the current state matches
// the demand state, the matcher actor replies with a message (containing the current state).

// Test requires that Redis is running externally
//@DoNotDiscover
class HcdControllerTests extends TestKit(HcdControllerTests.system)
    with ImplicitSender with FunSuiteLike with LazyLogging {

  import HcdControllerTests._

  test("Test non-periodic HCD controller") {
    val hcdController = system.actorOf(TestHcdController.props())

    // Send a setup command to the HCD
    val info = CommandInfo(ObsId("001"))
    val command = Setup(info, testPrefix).add(position.set("IR3"))
    hcdController ! Submit(command)
    system.actorOf(HcdStatusMatcherActor.props(List(command), Set(hcdController), self))
    within(10.seconds) {
      val status = expectMsgType[CommandStatus.Completed.type]
      logger.debug(s"Done (2). Received reply from matcher with current state: $status")
    }
  }
}
