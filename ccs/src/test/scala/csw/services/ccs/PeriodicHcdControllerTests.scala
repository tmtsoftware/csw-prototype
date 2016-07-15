package csw.services.ccs

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.ccs.HcdController.Submit
import csw.services.ccs.PeriodicHcdControllerTests.TestPeriodicHcdController
import csw.services.kvs._
import csw.util.config.Configurations.SetupConfig
import csw.util.config.StringKey
import org.scalatest.FunSuiteLike

import scala.concurrent.duration._

object PeriodicHcdControllerTests {
  val system = ActorSystem("Test")

  val testPrefix1 = "wfos.blue.filter"
  val testPrefix2 = "wfos.red.filter"

  // -- Test implementation of a periodic HCD controller --
  object TestPeriodicHcdController {
    def props(): Props = Props(classOf[TestPeriodicHcdController])
  }

  class TestPeriodicHcdController extends Actor with ActorLogging with PeriodicHcdController {

    // Use single worker actor to do work in the background
    // (could also use a worker per job/message if needed)
    val worker = context.actorOf(TestWorker.props())

    def receive: Receive = controllerReceive

    override protected def process(): Unit = {
      // Note: There could be some logic here to decide when to take the next config,
      // if there is more than one in the queue. (nextConfig is an Option, so this
      // only takes one config from the queue, if there is one there).
      nextConfig.foreach { config =>
        worker ! config
      }
    }
  }

  // -- Test worker actor that simulates doing some work --
  object TestWorker {
    def props(): Props = Props(classOf[TestWorker])

    // Message sent to self to simulate work done
    case class WorkDone(config: SetupConfig)

  }

  class TestWorker extends Actor with ActorLogging {

    import TestWorker._
    import context.dispatcher

    val settings = KvsSettings(context.system)
    val svs = StateVariableStore(settings)
    val position = StringKey("position")

    // Simulate getting the initial state from the device and publishing to the kvs
    val initialState = SetupConfig(testPrefix1).add(position.set("None"))
    svs.set(initialState)

    def receive: Receive = {
      case config: SetupConfig =>
        // Update the demand state variable
        svs.setDemand(config)
        // Simulate doing work
        log.info(s"Start processing $config")
        context.system.scheduler.scheduleOnce(2.seconds, self, WorkDone(config))

      case WorkDone(config) =>
        log.info(s"Done processing $config")
        // Simulate getting the current value from the device and publishing it to the kvs
        log.info(s"Publishing $config")
        svs.set(config)

      case x => log.error(s"Unexpected message $x")
    }
  }
}

// Tests sending a DemandState to a test HCD, then starting a matcher actor to subscribe
// to the current state (a state variable updated by the HCD). When the current state matches
// the demand state, the matcher actor replies with a message (containing the current state).

// Test requires that Redis is running externally
//@DoNotDiscover
class PeriodicHcdControllerTests extends TestKit(PeriodicHcdControllerTests.system)
    with ImplicitSender with FunSuiteLike with LazyLogging {

  import HcdControllerTests._
  import PeriodicHcdController._

  test("Test periodic HCD controller") {
    val hcdController = system.actorOf(TestPeriodicHcdController.props())
    hcdController ! Process(1.second) // Normally sent by the container when parsing the config file

    // Send a setup config to the HCD
    val config = SetupConfig(testPrefix1).add(position.set("IR2"))
    hcdController ! Submit(config)
    system.actorOf(StateVariableMatcherActor.props(List(config), self))
    within(10.seconds) {
      val status = expectMsgType[CommandStatus.Completed]
      logger.info(s"Done (1). Received reply from matcher with current state: $status")
    }
  }
}
