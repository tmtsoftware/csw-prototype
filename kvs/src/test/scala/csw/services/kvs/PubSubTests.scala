package csw.services.kvs

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor._
import org.scalatest.{DoNotDiscover, BeforeAndAfterAll, FunSuiteLike}
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import csw.util.Configuration

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
@DoNotDiscover
class PubSubTests extends TestKit(ActorSystem("Test"))
with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll {

  val numSecs = 10
  // number of seconds to run
  val subscriber = system.actorOf(Props(classOf[Subscriber], "Subscriber-1"))
  //  val subscriber2 = system.actorOf(Props(classOf[Subscriber], "Subscriber-2"))
  val publisher = system.actorOf(Props(classOf[Publisher], self, numSecs))

  test("Test subscriber") {
    within(numSecs + 2 seconds) {
      expectMsg("done")
      subscriber ! "done"
      val count = expectMsgType[Int]
      val msgPerSec = count / numSecs
      logger.info(s"Recieved $count events in $numSecs seconds ($msgPerSec per second)")
      assert(count > numSecs * 1000)
    }
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}

// A test class that publishes events
private case class Publisher(caller: ActorRef, numSecs: Int) extends Actor with ActorLogging with EventPublisher {
  val root = "tmt.mobie.red.dat.exposureInfo"
  val expTime = 1
  // ms
  var nextId = 0
  var done = false

  //Use the system's dispatcher as ExecutionContext

  import context.dispatcher

  context.system.scheduler.scheduleOnce(numSecs seconds) {
    caller ! "done"
    done = true
  }

  while (!done) {
    publish(root, nextEvent())
    Thread.`yield`() // don't want to hog the cpu here
    //    Thread.sleep(1000)
  }

  def nextEvent(): Event = {
    val time = System.currentTimeMillis()
    nextId = nextId + 1
    Configuration()
      .withValue(s"$root.eventId", nextId)
      .withValue(s"$root.exposureTime.value", expTime)
      .withValue(s"$root.exposureTime.units", "milliseconds")
      .withValue(s"$root.startTime", time - expTime)
      .withValue(s"$root.endTime", time)
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
  val kvs: KeyValueStore = RedisKeyValueStore()
  val root = "tmt.mobie.red.dat.exposureInfo"
  val idKey = s"$root.eventId"

  subscribe("tmt.mobie.red.dat.*")

  override def receive: Receive = {
    case event: Event =>
      //      log.info(s"$name received $event")
      count = count + 1
      if (count % 10000 == 0)
        log.info(s"Received $count events so far: $event")

    case "done" => sender ! count
    case x => log.error(s"Unexpected message $x")
  }
}

