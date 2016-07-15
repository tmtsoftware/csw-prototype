// XXX TODO: Implement test...

//package csw.services.ccs
//
//import akka.actor._
//import akka.testkit.{ImplicitSender, TestKit}
//import com.typesafe.scalalogging.slf4j.LazyLogging
//import csw.services.ccs.AssemblyController.{Submit, Valid, Validation}
//import csw.util.config.Configurations.{ConfigInfo, SetupConfig, SetupConfigArg}
//import csw.util.config.StateVariable.CurrentState
//import csw.util.config.{ObsId, StringKey}
//import org.scalatest.FunSuiteLike
//
//import scala.concurrent.duration._
//
//object AssemblyControllerTests {
//  val system = ActorSystem("Test")
//
//  val testPrefix1 = "wfos.blue.filter"
//  val testPrefix2 = "wfos.red.filter"
//
//  val position = StringKey("position")
//
//  object TestAssemblyController {
//    def props(): Props = Props(classOf[TestAssemblyController])
//  }
//
//  class TestAssemblyController extends AssemblyController with Actor with ActorLogging {
//
//    // Use single worker actor to do work in the background
//    // (could also use a worker per job/message if needed)
//    val worker = context.actorOf(TestWorker.props())
//
//    private def validate(configArg: SetupConfigArg): Validation = {
//      // TODO: Add code to check that the configs are all valid...
//      Valid
//    }
//
//    override def setup(locationsResolved: Boolean, configArg: SetupConfigArg, replyTo: Option[ActorRef]): Validation = {
//      val valid = validate(configArg)
//      configArg.configs.foreach { config =>
//        worker ! TestWorker.Work(config)}
//      valid
//    }
//
//    // Ask the worker actor to send us the current state (handled by parent trait)
//    override protected def requestCurrent(): Unit = {
//      worker ! TestWorker.RequestCurrentState
//    }
//
//    override def receive: Receive = controllerReceive
//  }
//
//  // -- Test worker actor that simulates doing some work --
//  object TestWorker {
//    def props(): Props = Props(classOf[TestWorker])
//
//    // Work to do
//    case class Work(config: SetupConfig)
//
//    // Message sent to self to simulate work done
//    case class WorkDone(config: SetupConfig)
//
//    // Message to request the current state values
//    case object RequestCurrentState
//  }
//
//  class TestWorker extends Actor with ActorLogging {
//
//    import TestWorker._
//    import context.dispatcher
//
//    // Simulate getting the initial state from the device
//    val initialState = CurrentState(testPrefix1).set(position, "None")
//
//    // Simulated current state
//    var currentState = initialState
//
//    def receive: Receive = {
//      case Work(config) =>
//        // Simulate doing work
//        log.info(s"Start processing $config")
//        context.system.scheduler.scheduleOnce(2.seconds, self, WorkDone(config))
//
//      case RequestCurrentState =>
//        log.info(s"Requested current state")
//        context.parent ! currentState
//
//      case WorkDone(config) =>
//        log.info(s"Done processing $config")
//        currentState = CurrentState(config.prefix, config.items)
//        context.parent ! currentState
//
//      case x => log.error(s"Unexpected message $x")
//    }
//  }
//
//}
//
//// Tests sending a SetupConfigArg to a test Assembly, then starting a matcher actor to subscribe
//// to the current state (a state variable updated by the Assembly). When the current state matches
//// the demand state, the matcher actor replies with a message (containing the current state).
//
//// Test requires that Redis is running externally
////@DoNotDiscover
//class AssemblyControllerTests extends TestKit(AssemblyControllerTests.system)
//  with ImplicitSender with FunSuiteLike with LazyLogging {
//
//  import AssemblyControllerTests._
//
//  test("Test Assembly controller") {
//    val assemblyController = system.actorOf(TestAssemblyController.props())
//
//    // Send a setup config to the Assembly
//    val config = SetupConfig(testPrefix2).set(position, "IR3")
//    val obsId = ObsId("obs0001")
//    val configArg = SetupConfigArg(ConfigInfo(obsId), config)
//    assemblyController ! Submit(configArg)
//    system.actorOf(AssemblyStatusMatcherActor.props(List(config), Set(assemblyController), self))
//    within(10.seconds) {
//      val status = expectMsgType[CommandStatus.Completed]
//      logger.info(s"Done (2). Received reply from matcher with current state: $status")
//    }
//  }
//}
