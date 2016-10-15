package csw.services.loc

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.Connection.{AkkaConnection, HttpConnection}
import csw.services.loc.LocationService._
import org.scalatest.{BeforeAndAfterAll, _}

import scala.concurrent.duration._


/**
  * This test
  */
object TrackerSubscriberTests {
  LocationService.initInterface()

  val system = ActorSystem("TrackerSubscriberTests")
}
class TrackerSubscriberTests extends TestKit(TrackerSubscriberTests.system) with ImplicitSender
  with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {

  override def afterAll = TestKit.shutdownActorSystem(TrackerSubscriberTests.system)


  val c1 = HttpConnection(ComponentId("Alarm Service", ComponentType.Service))

  val c2 = AkkaConnection(ComponentId("lgsTromboneHCD", ComponentType.HCD))

  // Test subscriber actor for telemetry
  object TestSubscriber {
    def props(): Props = Props(new TestSubscriber())

    case object GetResults

    case class Results(msgs: Vector[Location])

  }

  class TestSubscriber() extends Actor with TrackerSubscriberClient {

    import TestSubscriber._

    var msgs = Vector.empty[Location]

    subscribeToLocationUpdates()
    info(s"Test subscriber started")

    def receive: Receive = {
      case event: Location =>
        msgs = msgs :+ event
        log.info(s"Received my location: $event")
        event match {
          case l:ResolvedAkkaLocation =>
            log.info(s"Got actorRef: ${l.actorRef}")
          case h:ResolvedHttpLocation =>
            log.info(s"HTTP: ${h.connection}")
          case u:Unresolved =>
            log.info(s"Unresolved: ${u.connection}")
          case ut:UnTrackedLocation =>
            log.info(s"UnTracked: ${ut.connection}")
        }

      case GetResults => sender() ! Results(msgs)
    }
  }

  describe("Basic Tests") {
    import TrackerSubscriberActor._

    it("should allow creation") {
      val test = TestProbe()
      val ts = system.actorOf(TrackerSubscriberActor.props)

      test.expectNoMsg(500.milli)
    }


    it("should allow subscriptions") {
      import LocationService._

      val fakeAssembly = TestProbe()
      val ts = system.actorOf(TrackerSubscriberActor.props)

      fakeAssembly.send(ts, Subscribe)

      fakeAssembly.expectNoMsg(1.second)

      fakeAssembly.send(ts, LocationService.TrackConnection(c1))

      var msg = fakeAssembly.expectMsgClass(classOf[Location])
      msg shouldBe a [Unresolved]
      //info("Msg: " + msg)

      msg = fakeAssembly.expectMsgClass(classOf[Location])
      msg shouldBe a [ResolvedHttpLocation]
      //info("Msg: " + msg)

      // Now track the HCD
      fakeAssembly.send(ts, LocationService.TrackConnection(c2))

      msg = fakeAssembly.expectMsgClass(classOf[Location])
      msg shouldBe a [Unresolved]
      //info("Msg: " + msg)

      msg = fakeAssembly.expectMsgClass(classOf[ResolvedAkkaLocation])
      msg shouldBe a [ResolvedAkkaLocation]
      //info("Msg: " + msg)

      expectNoMsg(2.seconds)
      fakeAssembly.expectNoMsg(2.seconds)
    }

    /**
      * This test sets up a TrackerSubscriberActor and one Client to ensure that the client receives
      * updates.  Only the TrackerSubscriberActor runs a tracker.
      *
      * Note that this test is pretty lame and assumes an "Alarm Service" and "lgsTromboneHCD" is running.
      */
    it("should allow subscriptions with trait") {
      import TestSubscriber._

      val fakeAssembly = TestProbe()
      // Start the test subscriber running TrackerSubscriberClient
      val tsc = system.actorOf(TestSubscriber.props())

      // Start hte TrackerSubscriber
      val ts = system.actorOf(TrackerSubscriberActor.props)

      // TrackerSubscriber tracks two connections
      fakeAssembly.send(ts, LocationService.TrackConnection(c1))
      fakeAssembly.send(ts, LocationService.TrackConnection(c2))

      // Now wait a bit and see if the client has received updates
      expectNoMsg(2.seconds)

      tsc ! GetResults
      // Get the results
      val result = expectMsgClass(classOf[Results])
      // This is > 4 because on my machine service resolves twice
      result.msgs.size should be >= 4
      result.msgs.collect { case r:ResolvedAkkaLocation => r }.size should be >= 1
      result.msgs.collect { case h:ResolvedHttpLocation => h }.size should be >= 2
      result.msgs.collect { case un:Unresolved => un}.size should be >= 2

      expectNoMsg(2.seconds)
      fakeAssembly.expectNoMsg(2.seconds)
    }

  }


}
