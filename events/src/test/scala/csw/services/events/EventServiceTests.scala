package csw.services.events

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import csw.services.loc.LocationService
import csw.util.itemSet.Events.SystemEvent
import csw.util.itemSet._
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object EventServiceTests {
  LocationService.initInterface()
  val system = ActorSystem("EventServiceTests")

  // Define keys for testing
  val infoValue = IntKey("infoValue")

  val infoStr = StringKey("infoStr")

  val boolValue = BooleanKey("boolValue")

  val exposureTime = DoubleKey("exposureTime")
}

// Added annotation below, since test depends on Redis server running (Remove to include in tests)
//@DoNotDiscover
class EventServiceTests
    extends TestKit(EventServiceTests.system)
    with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll {

  import EventServiceTests._
  import system.dispatcher

  implicit val timeout = Timeout(20.seconds)

  // Used to start and stop the event service Redis instance used for the test
  //  var eventAdmin: EventServiceAdmin = _

  // Get the event service by looking up the name with the location service.
  val eventService = Await.result(EventService(), timeout.duration)

  override protected def beforeAll(): Unit = {
    // Note: This is only for testing: Normally Redis would already be running and registered with the location service.
    // Start redis and register it with the location service on a random free port.
    // The following is the equivalent of running this from the command line:
    //   tracklocation --name "Event Service" --command "redis-server --port %port"
    //    EventServiceAdmin.startEventService()

    // Get the event service by looking it up the name with the location service.
    //    eventService = Await.result(EventService(), timeout.duration)

    // This is only used to stop the Redis instance that was started for this test
    //    eventAdmin = EventServiceAdmin(eventService)
  }

  override protected def afterAll(): Unit = {
    // Shutdown Redis (Only do this in tests that also started the server)
    //    Try(if (eventAdmin != null) Await.ready(eventAdmin.shutdown(), timeout.duration))
    TestKit.shutdownActorSystem(system)
  }

  test("Test subscribing to events via subscribe method") {
    val prefix = "tcs.test5"
    val event = SystemEvent(prefix)
      .add(infoValue.set(5))
      .add(infoStr.set("info 5"))
      .add(boolValue.set(true))
    var eventReceived: Option[Event] = None

    def listener(ev: Event): Unit = {
      eventReceived = Some(ev)
      logger.info(s"Listener received event: $ev")
    }

    Await.ready(eventService.publish(event), 2.seconds)
    val probe = TestProbe(prefix)
    val monitor1 = eventService.subscribe(probe.ref, postLastEvents = true, prefix)
    val monitor2 = eventService.subscribe(listener _, postLastEvents = true, prefix)
    try {
      Thread.sleep(500)
      // wait for actor to start
      val e = probe.expectMsgType[SystemEvent](2.seconds)
      logger.info(s"Actor received event: $e")
      assert(e == event)
      Thread.sleep(500) // wait redis to react?
      assert(eventReceived.isDefined)
      assert(e == eventReceived.get)
    } finally {
      monitor1.stop()
      monitor2.stop()
    }
  }
}
