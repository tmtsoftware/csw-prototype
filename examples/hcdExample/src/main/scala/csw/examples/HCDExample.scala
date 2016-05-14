package csw.examples

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import csw.services.ccs.HcdController
import csw.services.event.{EventService, EventServiceSettings, EventSubscriber}
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.{ComponentId, ComponentType, LocationService}
import csw.services.pkg.{Hcd, LifecycleHandler, Supervisor}
import csw.services.pkg.Component.{HcdInfo, RegisterOnly}
import csw.services.ts.TimeService
import csw.services.ts.TimeService.TimeServiceScheduler
import csw.util.cfg.Configurations.SetupConfig
import csw.util.cfg.Events.SystemEvent
import csw.util.cfg.Key

import scala.concurrent.duration._
import scala.util.Random

/**
 * Test demonstrating working with the HCD APIs
 */
object HCDExample {
  val prefix = "tcs.pos.gen"
  val hcdName = "hcdExample"
  val className = "csw.examples.HCDExample"

  /**
   * Config key for setting the rate
   */
  val rateKey = Key.create[Int]("rate")

  // Generate position events
  protected object PosGenerator {

    // Periodic message sent by timer
    case object Tick

    // Message to change the rate at which positions are generated
    case class Rate(rate: Int)

    // Configuration keys for az and el
    val azKey = Key.create[Int]("az")
    val elKey = Key.create[Int]("el")

    // Used to create the actor
    def props(prefix: String): Props = Props(classOf[PosGenerator], "Position Generator", prefix)
  }

  // Position generator actor
  protected class PosGenerator(name: String, prefix: String) extends Actor with ActorLogging with TimeService.TimeServiceScheduler {

    import java.time._
    import PosGenerator._
    import TimeService._

    val rand = Random
    val eventService = EventService(prefix, EventServiceSettings(context.system))

    // Create a subscriber to positions (just for test)
    context.actorOf(Props(classOf[EventPosSubscriber], "ev subscriber", prefix))

    var timer = setTimer(1000)

    // Sets the delay in ms between ticks
    private def setTimer(delay: Int): Cancellable = {
      schedule(localTimeNow.plusSeconds(1), Duration.ofMillis(delay), self, Tick)
    }

    def receive: Receive = {
      case Rate(r) ⇒
        timer.cancel()
        timer = setTimer(1000 / r)

      case Tick ⇒
        val (az, el) = genPair(rand)
        val event = SystemEvent(prefix).set(azKey, az).set(elKey, el)
        eventService.publish(event)
    }

    def genPair(r: Random): (Int, Int) = {
      val az = r.nextInt(360)
      val el = r.nextInt(90)
      (az, el)
    }
  }

  class EventPosSubscriber(name: String, prefix: String) extends EventSubscriber {

    import PosGenerator._

    var count = 0

    import java.time._

    val startTime = Instant.now
    subscribe(prefix)

    def receive: Receive = {
      case event: SystemEvent ⇒
        val az = event.get(azKey).get
        val el = event.get(elKey).get
        log.info(s"Coords: az: $az, el: $el")

        count = count + 1
        if (count % 1000 == 0) {
          val t = Duration.between(startTime, Instant.now).getSeconds
          log.info(s"Received $count events from event service in $t seconds (${count.toFloat / t} per second)")
        }
    }
  }
}

class HCDExample(info: HcdInfo) extends Hcd with HcdController with TimeServiceScheduler with LifecycleHandler {
  import HCDExample._
  import Supervisor._
  import PosGenerator._
  import HCDExample._

  log.info(s"Freq: ${context.system.scheduler.maxFrequency}")
  log.info(s"My Rate: ${info.rate}")
  lifecycle(supervisor)

  // Create an actor to generate position events
  val posEventGenerator = context.actorOf(PosGenerator.props(prefix))

  // Process a config message
  override def process(sc: SetupConfig): Unit = {
    sc.get(rateKey).foreach {
      rate ⇒
        log.info(s"Set rate to $rate")
        posEventGenerator ! Rate(rate)
    }
  }

  // Receive actor methods
  def receive = controllerReceive orElse lifecycleHandlerReceive

}

/**
 * Starts the HCD as a standalone application.
 */
object HCDExampleApp extends App {
  LocationService.initInterface()
  import HCDExample._
  println("Starting!")
  val componentId = ComponentId(HCDExample.hcdName, ComponentType.HCD)
  val hcdInfo = HcdInfo(hcdName, prefix, className, RegisterOnly, Set(AkkaType), 1.second)
  val supervisor = Supervisor(hcdInfo)
}

