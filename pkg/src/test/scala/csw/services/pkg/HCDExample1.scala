package csw.services.pkg

import akka.actor.{ActorRef, ActorLogging, Props}
import csw.services.ccs.PeriodicHcdController
import csw.services.ccs.PeriodicHcdController.Process
import csw.services.loc.{ServiceType, ServiceId}
import csw.services.ts.TimeService
import csw.services.ts.TimeService.TimeServiceScheduler
import scala.concurrent.duration._

/**
  * Test demonstrating working with the HCD APIs
  */
object HCDExample1 {

  object DaemonHCD {
    def props(): Props = Props(classOf[DaemonHCD])
  }

  class DaemonHCD extends PeriodicHcdController with ActorLogging with TimeServiceScheduler {
    import TimeService._

    override def additionalReceive: Receive = {
      case "end" =>
        log.info("Ending")
        // Need to unregister with the location service (Otherwise application won't exit)
        context.parent ! Supervisor.UnregisterWithLocationService
        context.system.terminate()
    }

    val killer = scheduleOnce(localTimeNow.plusSeconds(10), self, "end")

    override def process(): Unit = {
      nextConfig.foreach { config =>
        log.info(s"received: $config")

      }
    }
  }

}

/**
  * Starts Hcd2 as a standalone application.
  * Args: name, configPath
  */
object HCDExample1App extends App {
  // if (args.length != 2) {
  //    println("Expected two args: the HCD name and the config path")
  //    System.exit(1)
  //  }
  //val name = args(0)
  //val configPath = args(1)
  println("Starting!")
  val name = "example1"
  val prefix = "ex1"
  val props = HCDExample1.DaemonHCD.props()
  val serviceId = ServiceId(name, ServiceType.HCD)
  val httpUri = None
  //val regInfo = RegInfo(serviceId, Some(configPath), httpUri)
  val services = Nil
  val compInfo = Component.create(props, serviceId, prefix, services)
  val svisor:ActorRef = compInfo.supervisor
  svisor.tell(Process(1.second), null)
}





