package examples

import akka.actor.{ Cancellable, Actor, ActorLogging, Props }
import csw.services.ccs.PeriodicHcdController
import csw.services.event.{ EventSubscriber, EventService, EventServiceSettings }

import csw.services.loc.{ ServiceId, ServiceType }
import csw.services.pkg.{ LifecycleHandler, Hcd, Component }
import csw.services.pkg.Component.ComponentInfo

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
  val hcdName = "example1"

  /**
   * Config key for setting the rate
   */
  val rateKey = Key.create[Int]("rate")

  /**
   * Used to create the HCD actor
   */
  def props(): Props = Props(classOf[HCDExample])
}

class HCDExample extends Hcd with PeriodicHcdController with TimeServiceScheduler with LifecycleHandler {

  import PosGenerator._
  import HCDExample._

  override val name = hcdName

  log.info(s"Freq: ${context.system.scheduler.maxFrequency}")
  log.info("My Rate: $rate")

  // Create an actor to generate position events
  val posEventGenerator = context.actorOf(PosGenerator.props(prefix))

  // Process a config message
  private def doProcess(sc: SetupConfig): Unit = {
    sc.get(rateKey).foreach {
      rate ⇒
        log.info(s"Set rate to $rate")
        posEventGenerator ! Rate(rate)
    }
  }

  override def process(): Unit = {
    nextConfig.foreach { config ⇒ doProcess(config) }
  }
}

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

/**
 * Starts the HCD as a standalone application.
 */
object HCDExample2App extends App {
  println("Starting!")
  val serviceId = ServiceId(HCDExample.hcdName, ServiceType.HCD)

  val compInfo: ComponentInfo = Component.create(HCDExample.props(), serviceId, HCDExample.prefix, Nil)
  compInfo.supervisor ! PeriodicHcdController.Process(1.second)
}

