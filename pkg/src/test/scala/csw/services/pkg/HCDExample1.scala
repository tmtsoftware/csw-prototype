package csw.services.pkg

import java.time.{Instant, Duration}

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import csw.services.ccs.PeriodicHcdController2
import csw.services.event.{EventSubscriber, EventService}
import csw.services.kvs._
import csw.services.loc.{ServiceId, ServiceType}
import csw.services.pkg.Component2.{HcdInfo, RegisterOnly}
import csw.services.ts.TimeService
import csw.services.ts.TimeService.TimeServiceScheduler
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
    import PosGenerator._

    log.info(s"Freq: $context.system.scheduler.maxFrequency")
    log.info("My Rate: $info.rate")

    val posEventGenerator = context.actorOf(Props(classOf[PosGenerator], "Position Generator", info.prefix))

    val killer = scheduleOnce(localTimeNow.plusSeconds(30), self, "end")

    startProcessing(info.rate)

    def process(): Unit = {
      nextConfig.foreach { config ⇒
        log.info(s"received: $config")
      }
    }

    def additionalReceive: Receive = {
      case "end" ⇒
        // Need to unregister with the location service (Otherwise application won't exit)
        posEventGenerator ! End
        endProcessing(supervisor)
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

    // Create the Event Service
    val eventService = EventService(prefix)
    // Create the Telemetry Service
    val settings = KvsSettings(context.system)
    val ts = TelemetryService(settings)
    implicit val timeout = Timeout(5.seconds)
    val bts = BlockingTelemetryService(ts)

    val evs = List.tabulate(10)(n => {
      println(s"N: $n")
      context.actorOf(Props(classOf[EventPosSubscriber], s"ev subscriber${n}", prefix))
    })
    //val eventSubscriber = context.actorOf(Props(classOf[EventPosSubscriber], "event subscriber", prefix))
    //val telemSubscriber = context.actorOf(Props(classOf[TelPosSubscriber], "telem subscriber", prefix))

    var count = 0
    val cancel = schedule(localTimeNow.plusSeconds(1), Duration.ofMillis(10), self, Tick)
    val rand = Random

    def receive: Receive = {
      case Tick =>
        count = count + 1
        val (az, el) = genPair(rand)
        val ev = SystemEvent(prefix).set(azkey, az).set(elkey, el)
        eventService.publish(ev)
        val se = StatusEvent(prefix).set(azkey, az).set(elkey, el)
        bts.set(se)
        //log.info(s"Tick: $az/$el")

      case End =>
        log.info(s"Ending Daemon")
        log.info(s"Published total of: $count")
        evs.foreach(_ ! End)
       // eventSubscriber ! End
      //  telemSubscriber ! End
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

class EventPosSubscriber(name: String, prefix: String) extends EventSubscriber {
  log.info(s"prefix: $prefix")
  var count = 0
  import HCDExample1.PosGenerator._
  import java.time._

  val startTime = Instant.now
  subscribe(prefix)

  def receive: Receive = {
    case event:SystemEvent =>
      count = count + 1
      //log.info(s"Sub event: $event")
      if (count % 500 == 0) {
        val t = Duration.between(startTime, Instant.now).getSeconds
        log.info(s"Received $count on event service in $t seconds (${count.toFloat / t} per second)")
      }

    case End =>
      unsubscribe(prefix)
      log.info(s"Final Event Subscriber Count: $count")

  }

}

class TelPosSubscriber(name: String, prefix: String) extends TelemetrySubscriber {
  var count = 0
  import HCDExample1.PosGenerator._
  import java.time._

  val startTime = Instant.now
  subscribe(prefix)

  def receive: Receive = {
    case event:StatusEvent =>
      count = count + 1
      //log.info(s"TSub: $event")
      if (count % 1000 == 0) {
        val t = Duration.between(startTime, Instant.now).getSeconds
        log.info(s"Received $count on telemetry service in $t seconds (${count * 1.0 / t} per second)")
      }

    case End =>
      unsubscribe(prefix)
      log.info(s"Final Telemetry Subscriber Count: $count")

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
