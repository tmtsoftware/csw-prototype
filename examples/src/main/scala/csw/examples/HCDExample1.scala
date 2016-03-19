package csw.examples

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import csw.services.ccs.PeriodicHcdController
import csw.services.event.EventSubscriber
import csw.services.kvs._
import csw.services.pkg.Component.{HcdInfo, RegisterOnly}
import csw.services.pkg.{Hcd, LifecycleHandler, Supervisor}
import csw.services.ts.TimeService
import csw.services.ts.TimeService.TimeServiceScheduler
import csw.util.cfg.Events.{StatusEvent, SystemEvent}
import csw.util.cfg.Key
import csw.util.Components._

import scala.concurrent.duration._
import scala.util.Random

/**
 * Test demonstrating working with the HCD APIs
 */
object HCDExample1 {

  object Tick
  object End
  object Close

  case class HCDDaemon(info: HcdInfo) extends Hcd with PeriodicHcdController with TimeServiceScheduler with LifecycleHandler {
    import TimeService._
    import Supervisor._

    log.info(s"Freq: ${context.system.scheduler.maxFrequency}")
    log.info(s"My Rate: ${info.rate}")

    log.info("Startup called")
    lifecycle(supervisor)

    //val posEventGenerator = context.actorOf(Props(classOf[PosGenerator], "Position Generator", info.prefix))

    val killer = scheduleOnce(localTimeNow.plusSeconds(10), self, End)

    processAt(info.rate)
    var count = 0
    def process(): Unit = {
      count = count + 1
      log.info("Process: " + count)
      if (count == 3) {
        log.info("starting processing to .5")
        processAt(500.milli)
      }
      nextConfig.foreach { config ⇒
        log.info(s"received: $config")
      }
    }

    def componentReceive: Receive = {
      case End ⇒
        // Need to unregister with the location service (Otherwise application won't exit)
        //posEventGenerator ! End
        haltComponent(supervisor)

    }

    def receive = componentReceive orElse controllerReceive orElse lifecycleHandlerReceive

  }

  object PosGenerator {
    val azkey = Key.create[Int]("az")
    val elkey = Key.create[Int]("el")
  }

  class PosGenerator(name: String, prefix: String) extends Actor with ActorLogging with TimeService.TimeServiceScheduler with Implicits {
    import PosGenerator._

    println("Prefix: " + prefix)

    // Create the Event Service
    //val eventService = EventService(prefix)
    // Create the Telemetry Service
    val settings = KvsSettings(context.system)
    val ts = TelemetryService(settings)
    implicit val timeout = Timeout(5.seconds)
    val bts = BlockingTelemetryService(ts)

    val evs = List.tabulate(1)(n => {
      println(s"N: $n")
      context.actorOf(Props(classOf[TelPosSubscriber], s"ev subscriber${n}", prefix))
    })
    //val eventSubscriber = context.actorOf(Props(classOf[EventPosSubscriber], "event subscriber", prefix))
    //val telemSubscriber = context.actorOf(Props(classOf[TelPosSubscriber], "telem subscriber", prefix))

    var count = 0
  //  val cancel = schedule(localTimeNow.plusSeconds(1), Duration.ofMillis(500), self, Tick)
    val rand = Random

    def receive: Receive = {
      case Tick =>
        count = count + 1
        val (az, el) = genPair(rand)
        //val ev = SystemEvent(prefix).set(azkey, az).set(elkey, el)
        //eventService.publish(ev)
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
        //eventService.close
        //cancel.cancel
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
  import java.time._

  import HCDExample1._

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
  import java.time._

  import HCDExample1._

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
 * Starts Hcd as a standalone application.
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
  val className = "csw.examples.HCDExample1$HCDDaemon"

  val hcdInfo = HcdInfo(name, prefix, className, RegisterOnly, Set(AkkaType), 1.second)

  val supervisor = Supervisor(hcdInfo)
  //supervisor ! Startup

}
