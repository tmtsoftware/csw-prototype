package csw.services.event_old

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.scalalogging.LazyLogging
import csw.services.event_old.EventPubSubTest._
import csw.util.config.Events.ObserveEvent
import csw.util.config._
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, FunSuiteLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * Defines some static items used in the tests
 */
object EventPubSubTest {

  // --- configure this ---

  // delay between subscriber acknowledgement and the publishing of the next event
  // (Note that the timer resolution is not very accurate, so there is a big difference
  // between 0 and 1 nanosecond!)
  val delay = 1000.nanoseconds

  // Sets the expiration time for a message (If this is too small, the subscriber can't keep up)
  val expire = 1.second

  // Receive timeout
  val timeout = 6.seconds

  // total number of events to publish
  //  val totalEventsToPublish = 100000
  val totalEventsToPublish = 100

  // ---

  // Define a key for an event id
  val eventNum = IntKey("eventNum")

  val exposureTime = DoubleKey("exposureTime")

  // Define a key for image data
  val imageData = IntArrayKey("imageData")

  // Dummy image data
  val testImageData = IntArray(Array.ofDim[Int](10000))

  val prefix = "tcs.mobie.red.dat.exposureInfo"

  case object Publish

  case object Done

  case object PublisherInfo

  case object SubscriberAck

}

/**
 * Starts an embedded Hornetq server
 */
@DoNotDiscover
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
    logger.debug("Waiting for Done message...")
    expectMsgType[Done.type](5.minutes)
    system.terminate()
  }
}

// A test class that subscribes to events
class Subscriber extends Actor with ActorLogging with EventSubscriber {

  implicit val execContext: ExecutionContext = context.dispatcher
  implicit val actorSytem = context.system
  var count = 0
  var startTime = 0L
  context.setReceiveTimeout(timeout)

  subscribe(prefix)

  override def receive: Receive = {
    case PublisherInfo =>
      log.debug("Subscriber starting")
      context.become(working(sender()))
  }

  def working(publisher: ActorRef): Receive = {
    case event: ObserveEvent =>
      if (startTime == 0L) startTime = System.currentTimeMillis()
      val num = event(eventNum).head
      if (num != count) {
        log.error(s"Subscriber missed event: $num != $count")
        context.system.terminate()
      } else {
        count = count + 1
        if (count % 100 == 0) {
          val t = (System.currentTimeMillis() - startTime) / 1000.0
          log.debug(s"Received $count events in $t seconds (${count * 1.0 / t} per second)")
        }
        publisher ! SubscriberAck
      }

    case t: ReceiveTimeout =>
      log.error("Publisher seems to be blocked!")
      context.system.terminate()

    case x => log.warning(s"Unknown $x")

  }
}

class Publisher(subscriber: ActorRef) extends Actor with ActorLogging {
  val settings = EventServiceSettings(context.system)
  val eventService = EventService(prefix, settings)
  var count = 0
  context.setReceiveTimeout(timeout)

  override def postStop(): Unit = {
    log.debug(s"Close connection to the event service")
    eventService.close()
  }

  // Returns the next event to publish
  def nextEvent(num: Int): Event = {
    import csw.util.config.ConfigDSL._
    oe(
      prefix,
      eventNum -> num,
      exposureTime -> 1.0,
      imageData -> testImageData
    )
  }

  def publish(): Unit = {
    eventService.publish(nextEvent(count), expire)
    count += 1
  }

  subscriber ! PublisherInfo

  def receive: Receive = {
    case Publish =>
      context.become(publishing(sender()))
      publish()

    case x => log.warning(s"Unknown $x")
  }

  def publishing(testActor: ActorRef): Receive = {
    case Publish =>
      publish()

    case SubscriberAck =>
      if (count < totalEventsToPublish) {
        //        context.system.scheduler.scheduleOnce(delay, self, Publish)
        Thread.sleep(0L, delay.toNanos.toInt)
        self ! Publish
      } else {
        testActor ! Done
      }

    case t: ReceiveTimeout =>
      log.error("Subscriber did not reply!")
      context.system.terminate()

    case x => log.warning(s"Unknown $x")
  }
}

