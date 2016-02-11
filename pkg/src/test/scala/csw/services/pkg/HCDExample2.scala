package csw.services.pkg

import akka.actor.{ Actor, ActorLogging, Props }
import csw.services.ccs.PeriodicHcdController
import csw.services.kvs._
import csw.services.loc.{ ServiceId, ServiceType }
import csw.services.pkg.Component.ComponentInfo
import csw.services.pkg.HCDExample2.HCDDaemon
import csw.services.ts.TimeService
import csw.services.ts.TimeService.TimeServiceScheduler
import csw.util.cfg.Events.StatusEvent
import csw.util.cfg.Key

import scala.concurrent.duration._
import scala.util.Random

/**
 * Test demonstrating working with the HCD APIs
 */
object HCDExample2 {

  object HCDDaemon {
    def props(name: String, prefix: String): Props = Props(classOf[HCDDaemon], name, prefix)
  }

  case class HCDDaemon(name: String, prefix: String) extends Hcd with PeriodicHcdController with TimeServiceScheduler {
    import PosGenerator._
    import TimeService._

    log.info(s"Freq: ${context.system.scheduler.maxFrequency}")
    log.info("My Rate: $rate")

    val posEventGenerator = context.actorOf(Props(classOf[PosGenerator], "Position Generator", prefix))

    val killer = scheduleOnce(localTimeNow.plusSeconds(60 * 60), self, "end")

    def process(): Unit = {
      nextConfig.foreach { config ⇒
        log.info(s"received: $config")
      }
    }

    def additionalReceive: Receive = {
      case "end" ⇒
        // Need to unregister with the location service (Otherwise application won't exit)
        posEventGenerator ! End
        log.info("Sleep")
        Thread.sleep(3000)
        log.info("Done sleeping")
        context.parent ! Supervisor.UnregisterWithLocationService
        context.system.terminate()

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
    import java.time._

    import PosGenerator._
    import TimeService._

    println("Prefix: " + prefix)

    // Create the Telemetry Service
    val settings = KvsSettings(context.system)
    val ts = TelemetryService(settings)
    val tss = for (n ← 0 to 10) yield {
      context.actorOf(Props(classOf[TelPosSubscriber], s"ev subscriber$n", prefix))
    }

    var count = 0
    //    val cancel = schedule(localTimeNow.plusSeconds(1), Duration.ofMillis(2), self, Tick)
    val cancel = schedule(localTimeNow.plusSeconds(1), Duration.ofMillis(1000), self, Tick)
    val rand = Random

    def receive: Receive = {
      case Tick ⇒
        count = count + 1
        val (az, el) = genPair(rand)
        val se = StatusEvent(prefix).set(azkey, az).set(elkey, el)
        ts.set(se)
        log.info(s"Coords: az: $az, el: $el")

      case End ⇒
        log.info(s"Ending Daemon")
        log.info(s"Published total of: $count")
        cancel.cancel
        tss.foreach(_ ! End)
        self ! Close

      case Close ⇒
        log.info(s"Closing")
    }

    def genPair(r: Random): (Int, Int) = {
      val az = r.nextInt(360)
      val el = r.nextInt(90)
      (az, el)
    }

  }

}

class TelPosSubscriber(name: String, prefix: String) extends TelemetrySubscriber {
  var count = 0
  import java.time._

  import HCDExample2.PosGenerator._

  val startTime = Instant.now
  subscribe(prefix)

  def receive: Receive = {
    case event: StatusEvent ⇒
      count = count + 1
      if (count % 5000 == 0) {
        val t = Duration.between(startTime, Instant.now).getSeconds
        log.info(s"Received $count from telemetry service in $t seconds (${count * 1.0 / t} per second)")
      }

    case End ⇒
      unsubscribe(prefix)
      log.info(s"Final Telemetry Subscriber Count: $count")

  }
}
/**
 * Starts Hcd2 as a standalone application.
 */
object HCDExample2App extends App {
  // For logging
  System.setProperty("application-name", "HCDExample2")

  println("Starting example1 HCD!")
  val name = "example1"
  val prefix = "tcs.fake.pos"
  val serviceId = ServiceId(name, ServiceType.HCD)
  val props = HCDDaemon.props(name, prefix)

  val compInfo: ComponentInfo = Component.create(props, serviceId, prefix, Nil)
  compInfo.supervisor ! PeriodicHcdController.Process(1.second)

}

