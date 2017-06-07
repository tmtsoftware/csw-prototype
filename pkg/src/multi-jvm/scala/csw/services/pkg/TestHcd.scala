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
  case class WorkDone(command: Setup)
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

  // Send the command to the worker for processing
  override protected def process(command: Setup): Unit = {
    context.actorOf(TestWorker.props(command, info.prefix))
  }

}

// -- Test worker actor that simulates doing some work --
object TestWorker {
  def props(demand: Setup, prefix: String): Props = Props(new TestWorker(demand, prefix))

  // Message sent to self to simulate work done
  case class WorkDone(command: Setup)
}


//class TestWorker(demand: Setup) extends Actor with ActorLogging {
class TestWorker(demand: Setup, prefix: String) extends Actor with ActorLogging {

  import TestWorker._
  import context.dispatcher

  // Simulate doing work
  log.info(s"Start processing $demand")
  context.system.scheduler.scheduleOnce(2.seconds, self, WorkDone(demand))

  def receive: Receive = {
    case WorkDone(command) =>
      log.info(s"Publishing $command")
      context.parent ! CurrentState(command)
      context.stop(self)
  }
}

