package csw.services.pkg

import akka.actor.{ActorLogging, Actor, Props}
import csw.services.ccs.PeriodicHcdController
import csw.services.kvs.{DemandKvs, KvsSettings, KeyValueStore}
import csw.services.kvs.Implicits._
import csw.util.cfg.Configurations.SetupConfig

import scala.concurrent.duration._
import scala.util.{Failure, Success}

// A test HCD
object TestHcd {

  // Message sent to self to simulate work done
  case class WorkDone(config: SetupConfig)

}

case class TestHcd(name: String) extends Hcd with PeriodicHcdController with LifecycleHandler {

  override def process(): Unit = {
    nextConfig.foreach { config =>
      // Simulate work being done
      context.actorOf(TestWorker.props(config))
    }
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

  val settings = KvsSettings(context.system)
  val kvs = KeyValueStore[SetupConfig](settings)
  val demandKvs = DemandKvs(kvs)

  // Sets the demand state variable
  demandKvs.setDemand(demand)

  // Simulate doing work
  log.info(s"Start processing $demand")
  context.system.scheduler.scheduleOnce(2.seconds, self, WorkDone(demand))

  def receive: Receive = {
    case WorkDone(config) =>
      // Simulate getting the current value from the device and publishing it to the kvs
      log.info(s"Publishing $config")
      kvs.set(config.prefix, config).onComplete {
        case Success(()) =>
          log.debug(s"Set value for ${config.prefix}")
          context.stop(self)
        case Failure(ex) =>
          log.error(s"Failed to set value for ${config.prefix}", ex)
          context.stop(self)
      }
  }
}

