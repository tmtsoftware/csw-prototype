package csw.services.pkg

import akka.actor.{Actor, ActorLogging, Props}
import csw.services.ccs.HcdController
import csw.services.pkg.Component.HcdInfo
import csw.util.config.Configurations.SetupConfig
import csw.util.config.StateVariable.CurrentState

import scala.concurrent.duration._

// A test HCD
object TestHcd {

  // Message sent to self to simulate work done
  case class WorkDone(config: SetupConfig)

}

/**
 * Test HCD
 */
case class TestHcd(info: HcdInfo)
  extends Hcd with HcdController with LifecycleHandler {

  import Supervisor._
  lifecycle(supervisor)

  override def receive: Receive = controllerReceive orElse lifecycleHandlerReceive orElse {
    case x => log.error(s"Unexpected message: $x")
  }

  // Send the config to the worker for processing
  override protected def process(config: SetupConfig): Unit = {
    context.actorOf(TestWorker.props(config))
  }

}

// -- Test worker actor that simulates doing some work --
object TestWorker {
  def props(demand: SetupConfig): Props = Props(classOf[TestWorker], demand)

  // Message sent to self to simulate work done
  case class WorkDone(config: SetupConfig)
}

class TestWorker(demand: SetupConfig) extends Actor with ActorLogging {

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

