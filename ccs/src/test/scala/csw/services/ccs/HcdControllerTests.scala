package csw.services.ccs

import akka.actor._
import akka.testkit.{ ImplicitSender, TestKit }
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.kvs.{ KeyValueStore, Implicits }
import csw.shared.cmd.CommandStatus
import csw.util.cfg.Configurations.SetupConfig
import csw.util.cfg.Configurations.StateVariable.{ CurrentState, DemandState }
import csw.util.cfg.StandardKeys.position
import org.scalatest.{ DoNotDiscover, FunSuiteLike }

import scala.concurrent.duration._

object HcdControllerTests extends Implicits {

  val testPrefix1 = "wfos.blue.filter"
  val testPrefix2 = "wfos.red.filter"

  // -- Test implementation of a periodic HCD controller --
  object TestPeriodicHcdController {
    def props(): Props = Props(classOf[TestPeriodicHcdController])
  }

  class TestPeriodicHcdController extends PeriodicHcdController {

    // Use single worker actor to do work in the background
    // (could also use a worker per job/message if needed)
    val worker = context.actorOf(TestWorker.props())

    override def rate: FiniteDuration = 1.second

    override def additionalReceive: Receive = Actor.emptyBehavior

    override protected def process(): Unit = {
      // Note: There could be some logic here to decide when to take the next config,
      // if there is more than one in the queue. (nextConfig is an Option, so this
      // only takes one config from the queue, if there is one there).
      nextConfig.foreach { config ⇒
        worker ! DemandState(config.prefix, config.data)
      }
    }
  }

  // -- Test implementation of a non-periodic HCD controller --
  object TestHcdController {
    def props(): Props = Props(classOf[TestHcdController])
  }

  class TestHcdController extends HcdController {

    // Use single worker actor to do work in the background
    // (could also use a worker per job/message if needed)
    val worker = context.actorOf(TestWorker.props())

    override protected def process(config: SetupConfig): Unit = {
      worker ! DemandState(config.prefix, config.data)
    }
  }

  // -- Test worker actor that simulates doing some work --
  object TestWorker {
    def props(): Props = Props(classOf[TestWorker])

    // Message sent to self to simulate work done
    case class WorkDone(config: DemandState)

  }

  class TestWorker extends Actor with ActorLogging {

    import TestWorker._
    import context.dispatcher

    implicit val sys = context.system
    val kvs = KeyValueStore[CurrentState]

    // Simulate getting the initial state from the device and publishing to the kvs
    val initialState = CurrentState(testPrefix1).set(position, "None")
    kvs.publish(initialState.extKey, initialState)

    def receive: Receive = {
      case config: DemandState ⇒
        // Simulate doing work
        log.info(s"Start processing $config")
        context.system.scheduler.scheduleOnce(2.seconds, self, WorkDone(config))

      case WorkDone(config) ⇒
        log.info(s"Done processing $config")
        // Simulate getting the current value from the device and publishing it to the kvs
        val currentState = CurrentState(config.prefix, config.data)
        log.info(s"Publishing $currentState")
        kvs.publish(currentState.extKey, currentState)

      case x ⇒ log.error(s"Unexpected message $x")
    }
  }

}

// Tests sending a DemandState to a test HCD, then starting a matcher actor to subscribe
// to the current state (a state variable updated by the HCD). When the current state matches
// the demand state, the matcher actor replies with a message (containing the current state).

// Test requires that Redis is running externally
@DoNotDiscover
class HcdControllerTests extends TestKit(ActorSystem("test"))
    with ImplicitSender with FunSuiteLike with LazyLogging {

  import HcdControllerTests._

  test("Test periodic HCD controller") {
    val hcdController = system.actorOf(TestPeriodicHcdController.props())

    // Send a setup config to the HCD
    val config = SetupConfig(testPrefix1).set(position, "IR2")
    hcdController ! config
    val demand = DemandState(config.prefix, config.data)
    system.actorOf(StateMatcherActor.props(List(demand), self))
    within(10.seconds) {
      val status = expectMsgType[CommandStatus.Completed]
      logger.info(s"Done (1). Received reply from matcher with current state: $status")
    }
  }

  test("Test non-periodic HCD controller") {
    val hcdController = system.actorOf(TestHcdController.props())

    // Send a setup config to the HCD
    val config = SetupConfig(testPrefix2).set(position, "IR3")
    hcdController ! config
    val demand = DemandState(config.prefix, config.data)
    system.actorOf(StateMatcherActor.props(List(demand), self))
    within(10.seconds) {
      val status = expectMsgType[CommandStatus.Completed]
      logger.info(s"Done (2). Received reply from matcher with current state: $status")
    }
  }
}
