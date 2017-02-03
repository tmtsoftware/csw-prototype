package csw.services.events

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor._
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.LocationService
import csw.util.config.DoubleKey
import csw.util.config.Events.SystemEvent

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class PubSubTests extends TestKit(PubSubTests.system)
    with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll {

  import PubSubTests._
  import system.dispatcher

  implicit val timeout = Timeout(10.seconds)

  // Get the event service by looking up the name with the location service.
  private val eventService = Await.result(EventService(), timeout.duration)

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  // Test runs for numSecs seconds, continuously publishing SystemEvent objects and
  // receiving them in the subscriber.
  test("Test subscriber") {
    // number of seconds to run
    val numSecs = 10
    val subscriber = system.actorOf(TestSubscriber.props())
    eventService.subscribe(subscriber, postLastEvents = true, "tcs.mobie.red.dat.*")
    val publisher = system.actorOf(TestPublisher.props(eventService, self, numSecs))

    within(numSecs + 2 seconds) {
      expectMsg("done")
      subscriber ! "done"
      val count = expectMsgType[Int]
      val msgPerSec = count / numSecs
      logger.debug(s"Recieved $count events in $numSecs seconds ($msgPerSec per second)")
      system.stop(subscriber)
      system.stop(publisher)
    }
  }
}

object PubSubTests {
  LocationService.initInterface()
  val system = ActorSystem("PubSubTests")

  val exposureTime = DoubleKey("exposureTime")

  object TestPublisher {
    def props(eventService: EventService, caller: ActorRef, numSecs: Int): Props =
      Props(classOf[TestPublisher], eventService, caller, numSecs)
  }

  // A test class that publishes events
  class TestPublisher(eventService: EventService, caller: ActorRef, numSecs: Int) extends Actor with ActorLogging {
    val prefix = "tcs.mobie.red.dat.exposureInfo"
    val expTime = 1.0
    var nextId = 0
    var done = false

    import context.dispatcher

    context.system.scheduler.scheduleOnce(numSecs seconds) {
      caller ! "done"
      done = true
    }

    while (!done) {
      eventService.publish(nextEvent())
      Thread.`yield`() // don't want to hog the cpu here
    }

    def nextEvent(): SystemEvent = {
      nextId = nextId + 1
      SystemEvent(prefix).add(exposureTime.set(expTime)) // XXX change to be a Duration
    }

    override def receive: Receive = {
      case x => log.error(s"Unexpected message $x")
    }
  }

  object TestSubscriber {
    def props(): Props = Props(classOf[TestSubscriber])
  }

  // A test class that subscribes to events
  class TestSubscriber extends Actor with ActorLogging {
    var count = 0

    override def receive: Receive = {
      case event: SystemEvent =>
        count = count + 1
        if (count % 10000 == 0)
          log.debug(s"Received $count events so far: $event")

      case "done" => sender() ! count
      case x      => log.error(s"Unexpected message $x")
    }
  }

}

