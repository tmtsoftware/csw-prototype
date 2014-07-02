package csw.services.event

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor._
import csw.util.cfg.ConfigValues
import ConfigValues.ValueData._
import csw.util.cfg.Events.TelemetryEvent
import org.scalatest.{DoNotDiscover, BeforeAndAfterAll, FunSuiteLike}
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

// Added annotation below, since test depends on Hornetq server running (Remove to include in tests)
@DoNotDiscover
class EventPubSubTests extends TestKit(ActorSystem("Test"))
with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll {

  val numSecs = 20
  // number of seconds to run
  val subscriber = system.actorOf(Props(classOf[Subscriber], "Subscriber-1"))
  val publisher = system.actorOf(Props(classOf[Publisher], self, numSecs))

  test("Test subscriber") {
    within((numSecs + 2).seconds) {
      expectMsg("done")
      subscriber ! "done"
      val count = expectMsgType[Int]
      val msgPerSec = count * 1.0 / numSecs
      logger.info(s"Recieved $count events in $numSecs seconds ($msgPerSec per second)")
      assert(count > 0)
    }
    Thread.sleep(1000) // wait for any messages still in the queue for the subscriber
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}

// A test class that publishes events
private case class Publisher(caller: ActorRef, numSecs: Int) extends Actor with ActorLogging with EventPublisher {
  val channel = "tmt.mobie.red.dat.exposureInfo"
  val expTime = 1
  var nextId = 0
  var done = false

  //Use the system's dispatcher as ExecutionContext

  import context.dispatcher

  context.system.scheduler.maxFrequency
  context.system.scheduler.scheduleOnce(numSecs.seconds) {
    caller ! "done"
    done = true
  }

  while (!done) {
    val event = nextEvent()
    publish(channel, event)
    Thread.`yield`() // don't want to hog the cpu here
    if (nextId % 10000 == 0)
      log.info(s"Published $nextId events so far: $event")
  }

  // Returns the next event to publish
  def nextEvent(): Event = {
    val time = System.currentTimeMillis()
    nextId = nextId + 1
    TelemetryEvent(
      source = "test",
      s"$channel",
      "eventId" -> nextId,
      "exposureTime" -> expTime.ms, // XXX deal with duration implicit defs
      "startTime" -> (time - expTime),
      "endTime" -> time
    )
  }

  override def receive: Receive = {
    case x => log.error(s"Unexpected message $x")
  }
}

// A test class that subscribes to events
private case class Subscriber(name: String) extends Actor with ActorLogging with EventSubscriber {
  implicit val execContext: ExecutionContext = context.dispatcher
  implicit val actorSytem = context.system
  var count = 0

  //  val channel = "tmt.mobie.red.dat.exposureInfo"
  val channel = "tmt.mobie.red.dat.*" // using wildcard
  subscribe(channel)

  override def receive: Receive = {
    case event: Event =>
      count = count + 1
      if (count % 10000 == 0)
        log.info(s"Received $count events so far: $event")

    case "done" =>
      sender() ! count
      unsubscribe(channel)

    case x => log.error(s"Unexpected message $x")
  }
}
