package csw.services.event

import akka.testkit.{ ImplicitSender, TestKit }
import akka.actor._
import csw.util.cfg.Events.ObserveEvent
import csw.util.cfg.Key
import csw.util.cfg.StandardKeys._
import org.scalatest.{ BeforeAndAfterAll, FunSuiteLike }
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

/**
 * Defines some static items used in the tests
 */
object EventPubSubTests {
  // Define a key for an event id
  val eventId = Key.create[Int]("eventId")

  // Define a key for image data
  val imageData = Key.create[Array[Short]]("imageData")

  // Dummy image data
  val testImageData = Array.ofDim[Short](10000)

  val prefix = "tcs.mobie.red.dat.exposureInfo"

  val staticEvent = ObserveEvent(prefix)
    .set(eventId, 0)
    .set(exposureTime, 1.0)
    .set(imageData, testImageData)

  // Message sent to publisher to publish next message
  case object Publish

  // Message to end test
  case object Done

}

/**
 * Does a performance test of the event service
 */
class EventPubSubTests extends TestKit(ActorSystem("Test")) with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll {

  import EventPubSubTests._
  import system.dispatcher

  val settings = EventServiceSettings(system)
  if (settings.useEmbeddedHornetq) {
    // Start an embedded HornetQ server, so no need to have it running externally!
    EventService.startEmbeddedHornetQ()
  }

  // number of seconds to run
  val numSecs = 30

  val subscriber = system.actorOf(Props(classOf[Subscriber], numSecs))
  val publisher = system.actorOf(Props(classOf[Publisher], numSecs))

  // Set a timer to end the test
  system.scheduler.scheduleOnce(numSecs.seconds, publisher, Done)
  system.scheduler.scheduleOnce(numSecs.seconds, subscriber, Done)
  system.scheduler.scheduleOnce(numSecs.seconds, self, Done)

  // Set a timer to publish messages
  val timer = system.scheduler.schedule(Duration.Zero, 1.millisecond, publisher, Publish)

  test("Test subscriber") {
    within((numSecs + 5).seconds) {
      expectMsg(Done)
    }
    Thread.sleep(1000) // wait for any messages still in the queue for the subscriber
  }

  override def afterAll(): Unit = {
    system.terminate()
  }
}

// A test class that publishes events
private case class Publisher(numSecs: Int) extends Actor with ActorLogging {
  val expTime = 1
  var count = 0
  var done = false

  import EventPubSubTests._

  val settings = EventServiceSettings(context.system)
  val eventService = EventService(settings)

  // Returns the next event to publish
  def nextEvent(): Event = {
    count = count + 1
    ObserveEvent(prefix)
      .set(eventId, count)
      .set(exposureTime, 1.0)
      .set(imageData, testImageData)
    //    staticEvent
  }

  override def receive: Receive = {
    case Publish ⇒
      (0 until 100).foreach { _ ⇒
        eventService.publish(nextEvent())
      }

    case Done ⇒
      log.info(s"Published $count events in $numSecs seconds (${count * 1.0 / numSecs} per second)")

    case x ⇒ log.error(s"Unexpected message $x")
  }
}

// A test class that subscribes to events
private case class Subscriber(numSecs: Int) extends Actor with ActorLogging with EventSubscriber {

  import EventPubSubTests._

  implicit val execContext: ExecutionContext = context.dispatcher
  implicit val actorSytem = context.system
  var count = 0

  subscribe(prefix)

  override def receive: Receive = {
    case event: ObserveEvent ⇒
      count = count + 1
      if (count % 10000 == 0)
        log.info(s"Received $count events so far: $event")

    case Done ⇒
      log.info(s"Received $count events in $numSecs seconds (${count * 1.0 / numSecs} per second)")
      unsubscribe(prefix)

    case x ⇒ log.error(s"Unexpected message $x")
  }
}
