package csw.services.kvs

import akka.testkit.{ ImplicitSender, TestKit }
import akka.actor._
import csw.util.cfg.Configurations.SetupConfig
import csw.util.cfg.StandardKeys.exposureTime
import org.scalatest.{ DoNotDiscover, BeforeAndAfterAll, FunSuiteLike }
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.duration._
import scala.language.postfixOps

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
@DoNotDiscover
class PubSubTests extends TestKit(ActorSystem("Test"))
    with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll {

  import PubSubTests._

  // number of seconds to run
  val numSecs = 10
  val subscriber = system.actorOf(Props(classOf[TestSubscriber], "Subscriber-1"))
  //  val subscriber2 = system.actorOf(Props(classOf[Subscriber], "Subscriber-2"))
  val publisher = system.actorOf(Props(classOf[TestPublisher], self, numSecs))

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

object PubSubTests extends Implicits {

  // A test class that publishes configs
  case class TestPublisher(caller: ActorRef, numSecs: Int) extends Publisher[SetupConfig] with Implicits {
    val root = "tmt.mobie.red.dat.exposureInfo"
    val expTime = 1
    var nextId = 0
    var done = false

    import context.dispatcher

    context.system.scheduler.scheduleOnce(numSecs seconds) {
      caller ! "done"
      done = true
    }

    while (!done) {
      publish(root, nextConfig())
      Thread.`yield`() // don't want to hog the cpu here
    }

    def nextConfig(): SetupConfig = {
      nextId = nextId + 1
      SetupConfig("test").set(exposureTime, expTime) // XXX change to be a Duration
    }

    override def receive: Receive = {
      case x ⇒ log.error(s"Unexpected message $x")
    }
  }

  // A test class that subscribes to configs
  case class TestSubscriber(name: String) extends Subscriber[SetupConfig] with Implicits {
    var count = 0

    subscribe("tmt.mobie.red.dat.*")

    override def receive: Receive = {
      case config: SetupConfig ⇒
        // log.info(s"$name received $config")
        count = count + 1
        if (count % 10000 == 0)
          log.info(s"Received $count configs so far: $config")

      case "done" ⇒ sender ! count
      case x      ⇒ log.error(s"Unexpected message $x")
    }
  }

}

