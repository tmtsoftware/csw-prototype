package csw.services.event

import akka.actor._
import akka.testkit.{ ImplicitSender, TestKit }
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.event.EventPubSubTest._
import csw.util.cfg.Events.ObserveEvent
import csw.util.cfg.Key
import csw.util.cfg.StandardKeys._
import org.scalatest.{ DoNotDiscover, BeforeAndAfterAll, FunSuiteLike }
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * Defines some static items used in the tests
 */
//@DoNotDiscover
object EventPubSubTest {

  // --- configure this ---

  // delay between subscriber acknowledgement and the publishing of the next event
  // (Note that the timer resolution is not very accurate, so there is a big difference
  // between 0 and 1 nanosecond!)
  val delay = 0.nanoseconds

  // Sets the expiration time for a message (If this is too small, the subscriber can't keep up)
  val expire = 1.second

  // total number of events to publish
  val totalEventsToPublish = 100000

  // ---

  // Define a key for an event id
  val eventNum = Key.create[Int]("eventNum")

  // Define a key for image data
  val imageData = Key.create[Array[Short]]("imageData")

  // Dummy image data
  val testImageData = Array.ofDim[Short](10000)

  val prefix = "tcs.mobie.red.dat.exposureInfo"

  case object Publish

  case object Done

  case class PublisherInfo(actorRef: ActorRef)

  case object SubscriberAck

}

/**
 * Starts an embedded Hornetq server
 */
class EventPubSubTest extends TestKit(ActorSystem("Test")) with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll {
  val settings = EventServiceSettings(system)
  if (settings.useEmbeddedHornetq) {
    // Start an embedded HornetQ server, so no need to have it running externally!
    EventService.startEmbeddedHornetQ()
  }

  val subscriber = system.actorOf(Props(classOf[Subscriber]), "Subscriber")

  val publisher = system.actorOf(Props(classOf[Publisher], subscriber), "Publisher")
  publisher ! Publish

  test("Wait for end of test") {
    expectMsgType[Done.type](25.minutes)
    system.terminate()
    System.exit(1)
  }
}

// A test class that subscribes to events
class Subscriber extends Actor with ActorLogging with EventSubscriber {

  implicit val execContext: ExecutionContext = context.dispatcher
  implicit val actorSytem = context.system
  var count = 0
  var startTime = 0L
  var timer = context.system.scheduler.scheduleOnce(6.seconds, self, Timeout.zero)

  subscribe(prefix)

  override def receive: Receive = {
    case PublisherInfo(actorRef) ⇒
      log.info("Subscriber starting")
      context.become(working(actorRef))
  }

  def working(publisher: ActorRef): Receive = {
    case event: ObserveEvent ⇒
      timer.cancel()
      if (startTime == 0L) startTime = System.currentTimeMillis()
      val num = event.get(eventNum).get
      if (num != count) {
        log.error(s"Subscriber missed event: $num != $count")
        context.system.terminate()
        System.exit(1)
      } else {
        count = count + 1
        if (count % 100 == 0) {
          val t = (System.currentTimeMillis() - startTime) / 1000.0
          log.info(s"Received $count events in $t seconds (${count * 1.0 / t} per second)")
        }
        publisher ! SubscriberAck
        timer = context.system.scheduler.scheduleOnce(6.seconds, self, Timeout.zero)
      }

    case t: Timeout ⇒
      log.error("Publisher seems to be blocked!")
      context.system.terminate()
      System.exit(1)

    case x ⇒ log.warning(s"Unknown $x")

  }
}

class Publisher(subscriber: ActorRef) extends Actor with ActorLogging {

  import context.dispatcher

  val settings = EventServiceSettings(context.system)
  val eventService = EventService(prefix, settings)
  var count = 0
  var timer = context.system.scheduler.scheduleOnce(6.seconds, self, Timeout.zero)

  // Returns the next event to publish
  def nextEvent(num: Int): Event = {
    ObserveEvent(prefix)
      .set(eventNum, num)
      .set(exposureTime, 1.0)
      .set(imageData, testImageData)
  }

  def publish(): Unit = {
    timer.cancel()
    eventService.publish(nextEvent(count), expire)
    count += 1
    timer = context.system.scheduler.scheduleOnce(6.seconds, self, Timeout.zero)
  }

  subscriber ! PublisherInfo(self)

  def receive: Receive = {
    case Publish ⇒
      context.become(publishing(sender()))
      publish()

    case x ⇒ log.warning(s"Unknown $x")
  }

  def publishing(testActor: ActorRef): Receive = {
    case Publish ⇒
      publish()

    case SubscriberAck ⇒
      if (count < totalEventsToPublish) {
        //        context.system.scheduler.scheduleOnce(delay, self, Publish)
        Thread.sleep(0L, delay.toNanos.toInt)
        self ! Publish
      } else {
        eventService.close()
        testActor ! Done
      }

    case t: Timeout ⇒
      log.error("Subscriber did not reply!")
      context.system.terminate()
      System.exit(1)

    case x ⇒ log.warning(s"Unknown $x")
  }
}

