package csw.services.event

import akka.actor._
import csw.services.event.EventPubSubTest._
import csw.util.cfg.Events.ObserveEvent
import csw.util.cfg.Key
import csw.util.cfg.StandardKeys._
import scala.concurrent.ExecutionContext

/**
  * Starts an embedded Hornetq server
  */
object EventPubSubTestApp extends App {
  val system = ActorSystem("EventPubSubTest")
  val settings = EventServiceSettings(system)
  if (settings.useEmbeddedHornetq) {
    // Start an embedded HornetQ server, so no need to have it running externally!
    EventService.startEmbeddedHornetQ()
  }

  val subscriber = system.actorOf(Props(classOf[Subscriber]), "Subscriber")

  // optional command line arg is number of events to publish
  val eventsToPublish = if (args.length == 1) args(0).toInt else 100

  system.actorOf(Props(classOf[Publisher], eventsToPublish, subscriber), "Publisher")
}

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

  case class PublisherInfo(actorRef: ActorRef)
  case object SubscriberAck
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
      if (count % 1000 == 0 || num == 0) {
        val t = (System.currentTimeMillis() - startTime)/1000.0
        log.info(s"Received $count events in $t seconds (${count * 1.0 / t} per second)")
      }
      if (num == 0) {
        publisher ! SubscriberAck
      }
  }
}

class Publisher(eventsToPublish: Int, subscriber: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher
  val settings = EventServiceSettings(context.system)
  val eventService = EventService(prefix, settings)

  // Returns the next event to publish
  def nextEvent(num: Int): Event = {
    ObserveEvent(prefix)
      .set(eventNum, num)
      .set(exposureTime, 1.0)
      .set(imageData, testImageData)
  }

  def publish(): Unit = {
    log.info(s"Publishing $eventsToPublish events")
    for (num <- eventsToPublish-1 to 0 by -1) yield {
      eventService.publish(nextEvent(num))
          Thread.sleep(1) // sleep 1 ms
//      Thread.`yield`()
    }
  }

  subscriber ! PublisherInfo(self)
  publish()

  def receive: Receive = {
    case SubscriberAck => publish()
  }
}
