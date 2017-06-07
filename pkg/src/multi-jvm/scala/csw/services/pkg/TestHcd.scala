package csw.services.pkg

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.services.ccs.HcdController
import csw.services.pkg.Component.HcdInfo
import csw.services.pkg.Supervisor.{Initialized, Running}
import csw.util.param.Parameters.Setup
import csw.util.param.StateVariable.CurrentState

import scala.concurrent.duration._

// A test HCD
object TestHcd {

  // Message sent to self to simulate work done
  case class WorkDone(config: Setup)
}

/**
 * Test HCD
 */
case class TestHcd(info: HcdInfo, supervisor: ActorRef)
  extends Hcd with HcdController {

  supervisor ! Initialized

  log.info("Message from TestHcd")

  override def receive: Receive = controllerReceive orElse {
    case Running =>
    case x => log.error(s"Unexpected message: ${x.getClass}")
  }

  // Send the config to the worker for processing
  override protected def process(config: Setup): Unit = {
    context.actorOf(TestWorker.props(config, info.prefix))
  }

}

// -- Test worker actor that simulates doing some work --
object TestWorker {
  def props(demand: Setup, prefix: String): Props = Props(classOf[TestWorker], demand, prefix)

  // Message sent to self to simulate work done
  case class WorkDone(config: Setup)
}


//class TestWorker(demand: Setup) extends Actor with ActorLogging {
class TestWorker(demand: Setup, prefix: String) extends Actor with ActorLogging {

  import TestWorker._
  import context.dispatcher

  // Simulate doing work
  log.info(s"Start processing $demand")
  context.system.scheduler.scheduleOnce(2.seconds, self, WorkDone(demand))

  def receive: Receive = {
    case WorkDone(config) =>
      log.info(s"Publishing $config")
      context.parent ! CurrentState(config)
      context.stop(self)
  }
}

