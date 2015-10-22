package csw.services.pkg

import akka.actor.{ActorLogging, Actor, Props}
import csw.services.ccs.PeriodicHcdController
import csw.services.kvs.KeyValueStore
import csw.services.kvs.Implicits._
import csw.util.cfg.Configurations.StateVariable.{CurrentState, DemandState}

import scala.concurrent.duration._

// A test HCD
object TestHcd {

  // Message sent to self to simulate work done
  case class WorkDone(config: DemandState)

}

case class TestHcd(name: String) extends Hcd with PeriodicHcdController with LifecycleHandler {

  override def rate: FiniteDuration = 1.second

  override def process(): Unit = {
    nextConfig.foreach { config =>
      // Simulate work being done
      context.actorOf(TestWorker.props(DemandState(config.prefix, config.data)))
    }
  }
}

// -- Test worker actor that simulates doing some work --
object TestWorker {
  def props(demand: DemandState): Props = Props(classOf[TestWorker], demand)

  // Message sent to self to simulate work done
  case class WorkDone(config: DemandState)

}

class TestWorker(demand: DemandState) extends Actor with ActorLogging {

  import TestWorker._
  import context.dispatcher

  implicit val sys = context.system
  val kvs = KeyValueStore[CurrentState]

  // Simulate doing work
  log.info(s"Start processing $demand")
  context.system.scheduler.scheduleOnce(2.seconds, self, WorkDone(demand))

  def receive: Receive = {
    case WorkDone(config) =>
      // Simulate getting the current value from the device and publishing it to the kvs
      val currentState = CurrentState(config.prefix, config.data)
      log.info(s"Publishing $currentState")
      kvs.set(currentState.extKey, currentState)
  }
}

