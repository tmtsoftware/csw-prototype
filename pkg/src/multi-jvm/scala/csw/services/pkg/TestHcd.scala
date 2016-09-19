package csw.services.pkg

import akka.actor.{Actor, ActorRef, Props}
import csw.services.ccs.HcdController
import csw.services.log.PrefixedActorLogging
import csw.services.pkg.Component.HcdInfo
import csw.services.pkg.Supervisor3.{Initialized, Started}
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
case class TestHcd(info: HcdInfo, supervisor: ActorRef)
  extends Hcd with HcdController with LifecycleHandler {

  supervisor ! Initialized
  supervisor ! Started

  log.info("Message from TestHcd")

  override def receive: Receive = controllerReceive orElse lifecycleHandlerReceive orElse {
    case x => log.error(s"Unexpected message: $x")
  }

  // Send the config to the worker for processing
  override protected def process(config: SetupConfig): Unit = {
    context.actorOf(TestWorker.props(config, info.prefix))
  }

}

// -- Test worker actor that simulates doing some work --
object TestWorker {
  def props(demand: SetupConfig, prefix: String): Props = Props(classOf[TestWorker], demand, prefix)

  // Message sent to self to simulate work done
  case class WorkDone(config: SetupConfig)
}


//class TestWorker(demand: SetupConfig) extends Actor with ActorLogging {
class TestWorker(demand: SetupConfig, override val prefix: String) extends Actor with PrefixedActorLogging {

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

