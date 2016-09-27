package csw.services.events

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.util.config.Events.SystemEvent
import csw.util.config._
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.Await
import scala.concurrent.duration._

object EventServiceTests {

  // Define keys for testing
  val infoValue = IntKey("infoValue")

  val infoStr = StringKey("infoStr")

  val boolValue = BooleanKey("boolValue")

  val exposureTime = DoubleKey("exposureTime")
}

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
//@DoNotDiscover
class EventServiceTests
    extends TestKit(ActorSystem("Test"))
    with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll {

  import EventServiceTests._
  implicit val execContext = system.dispatcher

  val settings = EventServiceSettings(system)
  val eventService = EventService(settings)

  test("Test subscribing to events via subscribe method") {
    val prefix = "tcs.test4"
    val event = SystemEvent(prefix)
      .add(infoValue.set(4))
      .add(infoStr.set("info 4"))
      .add(boolValue.set(true))
    var eventReceived: Option[Event] = None
    def listener(ev: Event): Unit = {
      eventReceived = Some(ev)
      logger.info(s"Listener received event: $ev")
    }
    val monitor = eventService.subscribe(Some(self), Some(listener), prefix)
    try {
      Thread.sleep(500) // wait for actor to start
      Await.ready(eventService.publish(event), 2.seconds)
      val e = expectMsgType[SystemEvent](2.seconds)
      logger.info(s"Actor received event: $e")
      assert(e == event)
      Thread.sleep(500) // wait redis to react?
      assert(eventReceived.isDefined)
      assert(e == eventReceived.get)
    } finally {
      monitor.stop()
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
  }
}
