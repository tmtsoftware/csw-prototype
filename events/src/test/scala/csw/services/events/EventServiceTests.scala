package csw.services.events

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.util.config.Events.StatusEvent
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

  test("Test Set and Get") {
    val prefix1 = "tcs.test1"
    val event1 = StatusEvent(prefix1)
      .add(infoValue.set(1))
      .add(infoStr.set("info 1"))

    val prefix2 = "tcs.test2"
    val event2 = StatusEvent(prefix2)
      .add(infoValue.set(2))
      .add(infoStr.set("info 2"))

    val f = for {
      res1 <- eventService.publish(event1)
      val1 <- eventService.get(prefix1).mapTo[Option[StatusEvent]]
      res2 <- eventService.publish(event2)
      val2 <- eventService.get(prefix2).mapTo[Option[StatusEvent]]
      res3 <- eventService.delete(prefix1, prefix2)
      res4 <- eventService.get(prefix1)
      res5 <- eventService.get(prefix2)
      res6 <- eventService.delete(prefix1, prefix2)
    } yield {
      assert(val1.exists(_.prefix == prefix1))
      assert(val1.exists(_(infoValue).head == 1))
      assert(val1.exists(_(infoStr).head == "info 1"))
      assert(val2.exists(_(infoValue).head == 2))
      assert(val2.exists(_(infoStr).head == "info 2"))
      assert(res3 == 2)
      assert(res4.isEmpty)
      assert(res5.isEmpty)
      assert(res6 == 0)
    }
    Await.result(f, 5.seconds)
  }

  test("Test set, get and getHistory") {
    val prefix = "tcs.test2"
    val event = StatusEvent(prefix).add(exposureTime.set(2.0))
    val n = 3

    val f = for {
      _ <- eventService.publish(event.add(exposureTime.set(3.0)), n)
      _ <- eventService.publish(event.add(exposureTime.set(4.0)), n)
      _ <- eventService.publish(event.add(exposureTime.set(5.0)), n)
      _ <- eventService.publish(event.add(exposureTime.set(6.0)), n)
      _ <- eventService.publish(event.add(exposureTime.set(7.0)), n)
      v <- eventService.get(prefix).mapTo[Option[StatusEvent]]
      h <- eventService.getHistory(prefix, n + 1)
      _ <- eventService.delete(prefix)
    } yield {
      assert(v.isDefined)
      assert(v.get(exposureTime).head == 7.0)
      assert(h.size == n + 1)
      for (i <- 0 to n) {
        logger.debug(s"History: $i: ${h(i)}")
      }
    }
    Await.result(f, 5.seconds)
  }

  test("Test future usage") {
    val prefix = "tcs.test3"
    val event = StatusEvent(prefix)
      .add(infoValue.set(2))
      .add(infoStr.set("info 2"))
      .add(boolValue.set(true))

    eventService.publish(event).onSuccess {
      case _ =>
        eventService.get(prefix).onSuccess {
          case Some(statusEvent: StatusEvent) =>
            assert(statusEvent(infoValue).head == 2)
            assert(statusEvent(infoStr).head == "info 2")
            assert(statusEvent(boolValue).head)
            statusEvent
        }
    }
  }

  test("Test subscribing to events via subscribe method") {
    val prefix = "tcs.test4"
    val event = StatusEvent(prefix)
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
      eventService.publish(event)
      val e = expectMsgType[StatusEvent](5.seconds)
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
