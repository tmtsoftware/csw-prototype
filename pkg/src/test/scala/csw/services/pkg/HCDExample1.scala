package csw.services.pkg

import akka.actor.{Actor, ActorLogging, Props}
import csw.services.ccs.PeriodicHcdController2
import csw.services.event.{EventSubscriber, EventService}
import csw.services.kvs.{Implicits, KvsSettings, BlockingKeyValueStore}
import csw.services.loc.{ServiceId, ServiceType}
import csw.services.pkg.Component2.{HcdInfo, RegisterOnly}
import csw.services.ts.TimeService
import csw.services.ts.TimeService.TimeServiceScheduler
import csw.util.cfg.Configurations.SetupConfig
import csw.util.cfg.Events.{StatusEvent, SystemEvent}
import csw.util.cfg.Key

import scala.concurrent.duration._
import scala.util.Random

/**
 * Test demonstrating working with the HCD APIs
 */
object HCDExample1 {

  case class HCDDaemon(name: String, info: HcdInfo) extends Hcd2 with PeriodicHcdController2 with TimeServiceScheduler {

    import Supervisor2._
    import TimeService._

    def additionalReceive: Receive = {
      case "end" ⇒
        // Need to unregister with the location service (Otherwise application won't exit)
        tester ! "end"
        endProcessing(supervisor)
    }

    log.info(s"Freq: $context.system.scheduler.maxFrequency")
    log.info("My Rate: $info.rate")

    val tester = context.actorOf(Props(classOf[PosGenerator], "Position Generator", info.prefix))

    val killer = scheduleOnce(localTimeNow.plusSeconds(11), self, "end")

    startProcessing(info.rate)

    def process(): Unit = {
      nextConfig.foreach { config ⇒
        log.info(s"received: $config")
      }
    }
  }

  object PosGenerator {
    object Tick
    object End
    object Close

    val azkey = Key.create[Int]("az")
    val elkey = Key.create[Int]("el")
  }

  class PosGenerator(name: String, prefix: String) extends Actor with ActorLogging with TimeService.TimeServiceScheduler with Implicits {
    import scala.concurrent.ExecutionContext.Implicits.global
    import java.time._

    import PosGenerator._
    import TimeService._

    println("Prefix: " + prefix)
    val subscriber = context.actorOf(Props(classOf[PosSubscriber], "subscriber", prefix))

    var count = 0
    val cancel = schedule(localTimeNow.plusSeconds(1), Duration.ofMillis(500), self, Tick)
    val rand = Random

    val eventService = EventService(prefix)

    val settings = KvsSettings(context.system)
    val kvs = BlockingKeyValueStore[SetupConfig](5.seconds, settings)

    def receive: Receive = {
      case Tick =>
        count = count + 1
        val (az, el) = genPair(rand)
        val ev = SystemEvent(prefix).set(azkey, az).set(elkey, el)
        eventService.publish(ev)
        val se = StatusEvent(prefix).set(azkey, az).set(elkey, el)
        //log.info(s"Tick: $az/$el")
        log.info(s"Pub")

      case End =>
        log.info(s"Ending Daemon")
        log.info(s"Count: $count")
        self ! Close

      case Close =>
        log.info(s"Closing")
        eventService.close
        cancel.cancel
    }

    def genPair(r: Random): (Int, Int) = {
      val az = r.nextInt(360)
      val el = r.nextInt(90)
      (az, el)
    }

  }

}

class PosSubscriber(name: String, prefix: String) extends Actor with ActorLogging with EventSubscriber {
  var count = 0
  import csw.services.pkg.HCDExample1.PosGenerator._

  subscribe(prefix)

  def receive: Receive = {
    case event:SystemEvent =>
      count = count + 1
      //log.info(s"Tick: $az/$el")
      log.info(s"Sub: $event")

    case End =>
      log.info(s"Ending Daemon")
      log.info(s"Count: $count")
      unsubscribe(prefix)

  }

}

class TelPosSubscriber(name: String, prefix: String) extends Actor with ActorLogging with TelemetrySubscriber {
  var count = 0
  import csw.services.pkg.HCDExample1.PosGenerator._

  subscribe(prefix)

  def receive: Receive = {
    case event:SystemEvent =>
      count = count + 1
      //log.info(s"Tick: $az/$el")
      log.info(s"Sub: $event")

    case End =>
      log.info(s"Ending Daemon")
      log.info(s"Count: $count")
      unsubscribe(prefix)

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
  val prefix = "tcs.fake.pos"
  val serviceId = ServiceId(name, ServiceType.HCD)

  //def props(name: String): Props = Props(classOf[HCDDaemon], name)
  //val cname = classOf[HCDDaemon].getName
  //println("Name: " + cname)

  val className = "csw.services.pkg.HCDExample1$HCDDaemon"

  val result = Hcd2.create(className, serviceId, prefix, RegisterOnly, 1.second)

}
