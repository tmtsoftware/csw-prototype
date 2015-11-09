package csw.services.pkg

import java.time.Duration

import akka.actor.{ActorLogging, Actor, Props}
import csw.services.ccs.PeriodicHcdController2
import csw.services.loc.{ServiceId, ServiceType}
import csw.services.pkg.Component2.{HcdInfo, RegisterOnly}
import csw.services.pkg.HCDExample1.HCDDaemon
import csw.services.ts.TimeService
import csw.services.ts.TimeService.TimeServiceScheduler

import scala.concurrent.duration._

/**
 * Test demonstrating working with the HCD APIs
 */
object HCDExample1 {

  case class HCDDaemon(name: String, info: HcdInfo) extends Hcd2 with PeriodicHcdController2  with TimeServiceScheduler {
    import Supervisor2._
    import TimeService._

    def additionalReceive: Receive = {
      case "end" ⇒
        log.info(s"Ending: $count")
        // Need to unregister with the location service (Otherwise application won't exit)
        tester ! "end"
        endProcessing(supervisor)
    }

    log.info(s"Freq: $context.system.scheduler.maxFrequency")
    log.info("My Rate: $info.rate")

    val tester = context.actorOf(TestDaemon.props())

    val killer = scheduleOnce(localTimeNow.plusSeconds(121), self, "end")

    startProcessing(info.rate)

    var count = 0;
    def process(): Unit = {
      log.info("process")
      count = count + 1
      nextConfig.foreach { config ⇒
        log.info(s"received: $config")
      }
    }

  }




  object TestDaemon {
    def props(): Props = Props(classOf[PosGenerator], "Position Generator")
  }

  class PosGenerator(name: String) extends Actor with ActorLogging with TimeService.TimeServiceScheduler {
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.language.postfixOps
    import TimeService._

    var count = 0;
    val cancel = schedule(localTimeNow.plusSeconds(1), Duration.ofMillis(2), self, "tick")

    def receive:Receive = {
      case "tick" => {
        count = count + 1
        //log.debug(s"Tick: $name")
      }
      case "end"  => {
        log.info(s"Ending Daemon")
        log.info(s"Count: $count")
        cancel.cancel()
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
  val serviceId = ServiceId(name, ServiceType.HCD)

  //def props(name: String): Props = Props(classOf[HCDDaemon], name)
  //val cname = classOf[HCDDaemon].getName
  //println("Name: " + cname)

  val className = "csw.services.pkg.HCDExample1$HCDDaemon"

  val result = Hcd2.create(className, serviceId, prefix, RegisterOnly, 1.second)

}

