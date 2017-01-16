package csw.examples

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import akka.util.Timeout
import csw.services.ccs.HcdController
import csw.services.events.TelemetryService
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.{ComponentId, ComponentType, LocationService}
import csw.services.pkg.Component.{HcdInfo, RegisterOnly}
import csw.services.pkg.{Hcd, Supervisor}
import csw.services.ts.TimeService
import csw.services.ts.TimeService.TimeServiceScheduler
import csw.util.config.Configurations.SetupConfig
import csw.util.config.Events.StatusEvent
import csw.util.config.IntKey

import scala.concurrent.Await
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
  val rateKey = IntKey("rate")

  // Generate position events
  protected object PosGenerator {

    // Periodic message sent by timer
    case object Tick

    // Message to change the rate at which positions are generated
    case class Rate(rate: Int)

    // Configuration keys for az and el
    val azKey = IntKey("az")
    val elKey = IntKey("el")

    // Used to create the actor
    def props(prefix: String): Props = Props(classOf[PosGenerator], "Position Generator", prefix)
  }

  // Position generator actor
  protected class PosGenerator(name: String, prefix: String) extends Actor with TimeService.TimeServiceScheduler {

    import java.time._
    implicit val system: ActorSystem = context.system

    import PosGenerator._
    import TimeService._

    private val rand = Random
    private implicit val timeout = Timeout(5.seconds)
    private val eventService = Await.result(TelemetryService(), timeout.duration)

    // Create a subscriber to positions (just for test)
    private val subscriberActor = context.actorOf(Props(classOf[EventPosSubscriber]))
    eventService.subscribe(subscriberActor, postLastEvents = true, prefix)

    private var timer = setTimer(1000)

    // Sets the delay in ms between ticks
    private def setTimer(delay: Int): Cancellable = {
      schedule(localTimeNow.plusSeconds(1), Duration.ofMillis(delay), self, Tick)
    }

    def receive: Receive = {
      case Rate(r) =>
        timer.cancel()
        timer = setTimer(1000 / r)

      case Tick =>
        val (az, el) = genPair(rand)
        val event = StatusEvent(prefix).add(azKey.set(az)).add(elKey.set(el))
        eventService.publish(event)
    }

    def genPair(r: Random): (Int, Int) = {
      val az = r.nextInt(360)
      val el = r.nextInt(90)
      (az, el)
    }
  }

  class EventPosSubscriber extends Actor with ActorLogging {

    import PosGenerator._

    var count = 0

    import java.time._

    private val startTime = Instant.now

    def receive: Receive = {
      case event: StatusEvent =>
        val az = event(azKey).head
        val el = event.get(elKey).get.head
        log.info(s"Coords: az: $az, el: $el")

        count = count + 1
        if (count % 1000 == 0) {
          val t = Duration.between(startTime, Instant.now).getSeconds
          log.info(s"Received $count events from event service in $t seconds (${count.toFloat / t} per second)")
        }
    }
  }
}

class HCDExample(override val info: HcdInfo, supervisor: ActorRef) extends Hcd with HcdController with TimeServiceScheduler {
  import HCDExample._
  import PosGenerator._
  import HCDExample._
  import Supervisor._

  log.info(s"Freq: ${context.system.scheduler.maxFrequency}")
  log.info(s"My Rate: ${info.rate}")
  supervisor ! Initialized
  supervisor ! Started

  // Create an actor to generate position events
  private val posEventGenerator = context.actorOf(PosGenerator.props(info.prefix))

  // Process a config message
  override def process(sc: SetupConfig): Unit = {
    for {
      rateItem <- sc.get(rateKey)
      rate <- rateItem.get(0)
    } {
      log.info(s"Set rate to $rate")
      posEventGenerator ! Rate(rate)
    }
  }

  // Receive actor methods
  def receive: Receive = controllerReceive orElse {
    case Running => log.info("Running")

    case x       => log.error(s"Unexpected message received: $x")
  }

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

