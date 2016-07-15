package csw.services.kvs

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor._
import csw.util.config.Configurations.SetupConfig
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, FunSuiteLike}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.util.config.DoubleKey

import scala.concurrent.duration._
import scala.language.postfixOps

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
//@DoNotDiscover
class PubSubTests extends TestKit(ActorSystem("Test"))
    with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll {

  import PubSubTests._

  // number of seconds to run
  val numSecs = 10
  val subscriber = system.actorOf(Props(classOf[TestSubscriber], "Subscriber-1"))
  val publisher = system.actorOf(Props(classOf[TestPublisher], self, numSecs))

  // Test runs for numSecs seconds, continuously publishing SetupConfig objects and
  // receiving them in the subscriber.
  test("Test subscriber") {
    within(numSecs + 2 seconds) {
      expectMsg("done")
      subscriber ! "done"
      val count = expectMsgType[Int]
      val msgPerSec = count / numSecs
      logger.info(s"Recieved $count configs in $numSecs seconds ($msgPerSec per second)")
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
  }
}

object PubSubTests {
  import Implicits._

  val exposureTime = DoubleKey("exposureTime")

  // A test class that publishes configs
  case class TestPublisher(caller: ActorRef, numSecs: Int) extends Actor with ActorLogging {
    val settings = KvsSettings(context.system)
    val kvs = KeyValueStore[SetupConfig](settings)
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
      kvs.set(prefix, nextConfig())
      Thread.`yield`() // don't want to hog the cpu here
    }

    def nextConfig(): SetupConfig = {
      nextId = nextId + 1
      SetupConfig(prefix).add(exposureTime.set(expTime)) // XXX change to be a Duration
    }

    override def receive: Receive = {
      case x => log.error(s"Unexpected message $x")
    }
  }

  // A test class that subscribes to configs
  case class TestSubscriber(name: String) extends Subscriber[SetupConfig] {
    var count = 0

    subscribe("tcs.mobie.red.dat.*")

    override def receive: Receive = {
      case config: SetupConfig =>
        // log.info(s"$name received $config")
        count = count + 1
        if (count % 10000 == 0)
          log.info(s"Received $count configs so far: $config")

      case "done" => sender() ! count
      case x      => log.error(s"Unexpected message $x")
    }
  }

}

