package csw.services.events

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor._
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.util.config.DoubleKey
import csw.util.config.Events.SystemEvent

import scala.concurrent.duration._
import scala.language.postfixOps

class PubSubTests extends TestKit(ActorSystem("Test"))
    with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll {

  import PubSubTests._

  // number of seconds to run
  val numSecs = 10
  val subscriber = system.actorOf(Props(classOf[TestSubscriber], "Subscriber-1"))
  val publisher = system.actorOf(Props(classOf[TestPublisher], self, numSecs))

  // Test runs for numSecs seconds, continuously publishing SystemEvent objects and
  // receiving them in the subscriber.
  test("Test subscriber") {
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

  override def afterAll(): Unit = {
    system.terminate()
  }
}

object PubSubTests {

  val exposureTime = DoubleKey("exposureTime")

  // A test class that publishes events
  case class TestPublisher(caller: ActorRef, numSecs: Int) extends Actor with ActorLogging {
    val settings = EventServiceSettings(context.system)
    val eventService = EventService(settings)
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

  // A test class that subscribes to events
  case class TestSubscriber(name: String) extends EventSubscriber {
    var count = 0

    subscribe("tcs.mobie.red.dat.*")

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

