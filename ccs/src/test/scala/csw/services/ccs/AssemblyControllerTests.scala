package csw.services.ccs

// XXX TODO: Implement test...

//package csw.services.ccs
//
import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.scalalogging.LazyLogging
import csw.services.ccs.AssemblyController.Submit
import csw.services.ccs.CommandStatus.CommandResponse
import csw.services.ccs.Validation.{Valid, Validation}
import csw.util.param.Parameters.{CommandInfo, Setup}
import csw.util.param.{ObsId, StringKey}
import org.scalatest.FunSuiteLike

import scala.concurrent.duration._

// XXX TODO: Test this (refactored after config change to item set)
object AssemblyControllerTests {
  val system = ActorSystem("Test")

  val testPrefix1 = "wfos.blue.filter"
  val testPrefix2 = "wfos.red.filter"

  val position = StringKey("position")

  object TestAssemblyController {
    def props(): Props = Props(new TestAssemblyController())
  }

  class TestAssemblyController extends AssemblyController with Actor with ActorLogging {

    // Use single worker actor to do work in the background
    // (could also use a worker per job/message if needed)
    private val worker = context.actorOf(TestWorker.props())

    private def validate(configArg: Setup): Validation = {
      // TODO: Add code to check that the configs are all valid...
      Valid
    }

    override def setup(template: Setup, replyTo: Option[ActorRef]): Validation = {
      val valid = validate(template)
      worker ! TestWorker.Work(template, replyTo)
      valid
    }

    override def receive: Receive = controllerReceive
  }

  // -- Test worker actor that simulates doing some work --
  object TestWorker {
    def props(): Props = Props(new TestWorker())

    // Work to do
    case class Work(config: Setup, replyTo: Option[ActorRef])

    // Message sent to self to simulate work done
    case class WorkDone(config: Setup, replyTo: Option[ActorRef])

    // Message to request the current state values
    case object RequestCurrentState
  }

  class TestWorker extends Actor with ActorLogging {

    import TestWorker._
    import context.dispatcher

    // Simulate getting the initial state from the device

    //    private val initialState = CurrentState(testPrefix1).add(position.set("None"))

    def receive: Receive = {
      case Work(config, replyTo) =>
        // Simulate doing work
        log.debug(s"Start processing $config")
        context.system.scheduler.scheduleOnce(2.seconds, self, WorkDone(config, replyTo))

      case WorkDone(config, replyTo) =>
        log.debug(s"Done processing $config")
        replyTo.foreach(_ ! CommandStatus.Completed)

      case x => log.error(s"Unexpected message $x")
    }
  }

}

// Tests sending a SetupConfigArg to a test Assembly, then starting a matcher actor to subscribe
// to the current state (a state variable updated by the Assembly). When the current state matches
// the demand state, the matcher actor replies with a message (containing the current state).

// Test requires that Redis is running externally
//@DoNotDiscover
class AssemblyControllerTests extends TestKit(AssemblyControllerTests.system)
    with ImplicitSender with FunSuiteLike with LazyLogging {

  import AssemblyControllerTests._

  test("Test Assembly controller") {
    val assemblyController = system.actorOf(TestAssemblyController.props())

    val obsId = ObsId("obs0001")

    // Send a setup config to the Assembly
    val setup = Setup(CommandInfo(obsId), testPrefix2).add(position.set("IR3"))

    assemblyController ! Submit(setup)
    //  system.actorOf(AssemblyStatusMatcherActor.props(List(config), Set(assemblyController), self))
    within(10.seconds) {
      val accept = expectMsgType[CommandResponse]
      logger.debug(s"Accepted with (2). Received accepted from worker with current state: $accept")
      val status = expectMsgType[CommandResponse]
      logger.debug(s"Done (2). Received completed reply from worker with current state: $status")
    }
  }
}
