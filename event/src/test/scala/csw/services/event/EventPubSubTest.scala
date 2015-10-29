package csw.services.event

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.event.EventPubSubTest._
import csw.util.cfg.Events.ObserveEvent
import csw.util.cfg.Key
import csw.util.cfg.StandardKeys._
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Defines some static items used in the tests
  */
object EventPubSubTest {
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

  val eventsToPublish = 1000
  val totalEventsToPublish = 100000
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
    expectMsgType[Done.type](10.minutes)
    system.terminate()
  }
}

// A test class that subscribes to events
class Subscriber extends Actor with ActorLogging with EventSubscriber {

  import context.dispatcher

  implicit val execContext: ExecutionContext = context.dispatcher
  implicit val actorSytem = context.system
  var count = 0
  var startTime = 0L

  subscribe(prefix)

  override def receive: Receive = {
    case PublisherInfo(actorRef) => context.become(working(actorRef))
  }

  def working(publisher: ActorRef): Receive = {
    case event: ObserveEvent ⇒
      if (startTime == 0L) startTime = System.currentTimeMillis()
      count = count + 1
      val num = event.get(eventNum).get
      if (count % eventsToPublish == 0) {
        val t = (System.currentTimeMillis() - startTime) / 1000.0
        log.info(s"Received $count events in $t seconds (${count * 1.0 / t} per second)")
        count = 0
        startTime = 0L
      }
      if (num == 0) {
        publisher ! SubscriberAck
      }
  }
}

class Publisher(subscriber: ActorRef) extends Actor with ActorLogging {

  import context.dispatcher

  val settings = EventServiceSettings(context.system)
  val eventService = EventService(prefix, settings)
  var count = 0

  // Returns the next event to publish
  def nextEvent(num: Int): Event = {
    ObserveEvent(prefix)
      .set(eventNum, num)
      .set(exposureTime, 1.0)
      .set(imageData, testImageData)
  }

  def publish(): Unit = {
    for (num <- eventsToPublish - 1 to 0 by -1) yield {
      eventService.publish(nextEvent(num))
    }
    count += eventsToPublish
  }

  subscriber ! PublisherInfo(self)

  def receive: Receive = {
    case Publish =>
      context.become(publishing(sender()))
      publish()
  }

  def publishing(testActor: ActorRef): Receive = {
    case Publish =>
      publish()

    case SubscriberAck =>
      if (count < totalEventsToPublish) {
        // Wait for messages to expire, to avoid running out of memory
        context.system.scheduler.scheduleOnce(1.second, self, Publish)
      } else {
        eventService.close()
        testActor ! Done
      }
  }
}


