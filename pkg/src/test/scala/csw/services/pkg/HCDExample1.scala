package csw.services.pkg

//import akka.actor.{ActorRef, ActorLogging, Props}
//import csw.services.ccs.PeriodicHcdController
//import csw.services.ccs.PeriodicHcdController.Process
//import csw.services.loc.{ComponentType, ComponentId}
//import csw.services.ts.TimeService
//import csw.services.ts.TimeService.TimeServiceScheduler
//import scala.concurrent.duration._

/**
 * Test demonstrating working with the HCD APIs
 */
object HCDExample1 {

//  object DaemonHCD {
//    def props(): Props = Props(classOf[DaemonHCD])
//  }
//
//  class DaemonHCD extends PeriodicHcdController with ActorLogging with TimeServiceScheduler {
//    import TimeService._
//
//    override def additionalReceive: Receive = {
//      case "end" ⇒
//        log.info("Ending")
//        // Need to unregister with the location service (Otherwise application won't exit)
//        context.parent ! Supervisor.UnregisterWithLocationService
//        context.system.terminate()
//    }
//
//    val killer = scheduleOnce(localTimeNow.plusSeconds(10), self, "end")
//
//    override def process(): Unit = {
//      nextConfig.foreach { config ⇒
//        log.info(s"received: $config")
//
//      }
//    }
//  }

}

/**
 * Starts Hcd2 as a standalone application.
 */
object HCDExample1App extends App {
//  println("Starting!")
//  val name = "example1"
//  val prefix = "ex1"
//  val props = HCDExample1.DaemonHCD.props()
//  val componentId = ComponentId(name, ComponentType.HCD)
//  val httpUri = None
//  val services = Nil
//  val compInfo = Component.create(props, componentId, prefix, services)
//  val svisor: ActorRef = compInfo.supervisor
//  svisor.tell(Process(1.second), null)
}

