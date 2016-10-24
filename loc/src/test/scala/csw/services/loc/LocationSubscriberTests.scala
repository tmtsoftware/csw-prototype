package csw.services.loc

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.Connection.TcpConnection
import csw.services.loc.LocationService._
import org.scalatest.{BeforeAndAfterAll, _}

import scala.concurrent.Await
import scala.concurrent.duration._


/**
  * This test
  */
object LocationSubscriberTests {
  LocationService.initInterface()

  val system = ActorSystem("TrackerSubscriberTests")
}
class LocationSubscriberTests extends TestKit(LocationSubscriberTests.system) with ImplicitSender
  with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {

  override def afterAll = TestKit.shutdownActorSystem(LocationSubscriberTests.system)


  val c1Name = "My Alarm Service"
  val c1Id = ComponentId(c1Name, ComponentType.Service)
  val c1 = TcpConnection(c1Id)

  // Test subscriber actor for telemetry
  object TestSubscriber {
    def props(): Props = Props(new TestSubscriber())

    case object GetResults

    case class Results(msgs: Vector[Location])

  }

  class TestSubscriber() extends Actor with LocationSubscriberClient {

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
          case t:ResolvedTcpLocation =>
            log.info(s"TCP Location: ${t.connection}")
          case u:Unresolved =>
            log.info(s"Unresolved: ${u.connection}")
          case ut:UnTrackedLocation =>
            log.info(s"UnTracked: ${ut.connection}")
        }

      case GetResults => sender() ! Results(msgs)
    }
  }

  /**
    * Test description, should allow creation and get no messages
    */
  describe("Basic Tests") {

    it("should allow creation") {
      val test = TestProbe()
      system.actorOf(LocationSubscriberActor.props)

      test.expectNoMsg(500.milli)
    }


    /**
      * Test Description: This test creates a TrackerSubscriberActor and creates a TcpConnection, after it
      * is registered, it gets one resolved location and then untracks it waiting for the Unresolved message
      * In this test, the track is after the register
      */
    it("should allow track subscription and untrack-after") {
      import LocationService._

      val fakeAssembly = TestProbe()
      val ts = system.actorOf(LocationSubscriberActor.props)

      // Start the service and wait for registration to be done
      Await.ready(LocationService.registerTcpConnection(c1Id, 1000), Duration.Inf)

      // Subscribe and then track the above service
      fakeAssembly.send(ts, LocationSubscriberActor.Subscribe)
      fakeAssembly.send(ts, LocationService.TrackConnection(c1))

      val msg1 = fakeAssembly.expectMsgAnyClassOf(5.seconds, classOf[Location])
      msg1 shouldBe a[ResolvedTcpLocation]

      // Wait for 1 second
      fakeAssembly.expectNoMsg(1.seconds)
      // Now unregister
      LocationService.unregisterConnection(c1)

      val msg2 = fakeAssembly.expectMsgAnyClassOf(10.seconds, classOf[Location])
      msg2 shouldBe a[Unresolved]
      // expect no more messages
      fakeAssembly.expectNoMsg(1.seconds)
    }

    /**
      * Test Description: This test creates a TrackerSubscriberActor and creates a TcpConnection, after it
      * is registered, it gets one resolved location and then untracks it waiting for the Unresolved message
      * In this test, the track is before the register
      */
    it("should allow track subscription and untrack-before") {
      import LocationService._

      val fakeAssembly = TestProbe()
      val ts = system.actorOf(LocationSubscriberActor.props)

      // Subscribe and then track the above service
      fakeAssembly.send(ts, LocationSubscriberActor.Subscribe)
      fakeAssembly.send(ts, LocationService.TrackConnection(c1))

      // Start the service and wait for registration to be done
      Await.ready(LocationService.registerTcpConnection(c1Id, 1000), Duration.Inf)

      val msg1 = fakeAssembly.expectMsgAnyClassOf(5.seconds, classOf[Location])
      msg1 shouldBe a[ResolvedTcpLocation]

      // Wait for 1 second
      fakeAssembly.expectNoMsg(1.seconds)
      // Now unregister
      LocationService.unregisterConnection(c1)

      val msg2 = fakeAssembly.expectMsgAnyClassOf(10.seconds, classOf[Location])
      msg2 shouldBe a[Unresolved]
      // expect no more messages
      fakeAssembly.expectNoMsg(1.seconds)
    }

    /**
      * This test sets up a TrackerSubscriberActor and one Client to ensure that the client receives
      * updates.  Only the TrackerSubscriberActor runs a tracker.
      *
      * Note that this test is pretty lame and assumes an "Alarm Service" and "lgsTromboneHCD" is running.
      */
    it("should allow subscriptions with trait") {
      import TestSubscriber._
      implicit val context = system.dispatcher

      val fakeAssembly = TestProbe()
      // Start the test subscriber running TrackerSubscriberClient
      val tsc = system.actorOf(TestSubscriber.props())

      // Start the TrackerSubscriber
      val ts = system.actorOf(LocationSubscriberActor.props)

      // TrackerSubscriber tracks one connection
      fakeAssembly.send(ts, LocationService.TrackConnection(c1))

      // Now register the service
      Await.ready(LocationService.registerTcpConnection(c1Id, 1000), Duration.Inf)

      // Now wait a bit and see if the client has received updates
      expectNoMsg(500.milli)

      // Test the results
      tsc ! GetResults
      // Get the results
      val result = expectMsgClass(classOf[Results])
      result.msgs.size shouldBe 1
      result.msgs.collect { case r:ResolvedAkkaLocation => r }.size shouldBe 0
      result.msgs.collect { case h:ResolvedTcpLocation => h }.size shouldBe 1
      result.msgs.collect { case un:Unresolved => un}.size shouldBe 0

      expectNoMsg(500.milli)
    }

    /**
      * This test sets up a TrackerSubscriberActor and one Client to ensure that the client receives
      * updates.  This tests that each trackconnection results in a resolved location
      */
    it("should allow multiple trackconnection and each gives a resolve") {
      import TestSubscriber._
      implicit val context = system.dispatcher

      val fakeAssembly = TestProbe()
      // Start the test subscriber running TrackerSubscriberClient
      val tsc = system.actorOf(TestSubscriber.props())

      // Start the TrackerSubscriber
      val ts = system.actorOf(LocationSubscriberActor.props)

      // TrackerSubscriber tracks one connection
      fakeAssembly.send(ts, LocationService.TrackConnection(c1))

      fakeAssembly.send(ts, LocationService.TrackConnection(c1))

      // Now register the service
      Await.ready(LocationService.registerTcpConnection(c1Id, 1000), Duration.Inf)

      // Now wait a bit and see if the client has received updates
      expectNoMsg(500.milli)

      // Test the results
      tsc ! GetResults
      // Get the results
      val result = expectMsgClass(classOf[Results])
      result.msgs.size shouldBe 2
      result.msgs.collect { case r:ResolvedAkkaLocation => r }.size shouldBe 0
      result.msgs.collect { case h:ResolvedTcpLocation => h }.size shouldBe 2
      result.msgs.collect { case un:Unresolved => un}.size shouldBe 0

      expectNoMsg(500.milli)
    }

    /**
      * Test Description: Start two separate trackersubscribers
      * Because they are in the same actor system, they both see two events event though each tracks once
      */
    it("should allow two trackers that each get events") {
      import TestSubscriber._
      implicit val context = system.dispatcher

      // Register the service first
      Await.ready(LocationService.registerTcpConnection(c1Id, 1000), Duration.Inf)

      val fakeAssembly = TestProbe()
      // Start the test subscriber running TrackerSubscriberClient
      val tsc1 = system.actorOf(TestSubscriber.props())

      // Start the first TrackerSubscriber
      val ts1 = system.actorOf(LocationSubscriberActor.props)

      // TrackerSubscriber tracks one connection
      fakeAssembly.send(ts1, LocationService.TrackConnection(c1))

      // Start the second test subscriber running TrackerSubscriberClient
      val tsc2 = system.actorOf(TestSubscriber.props())

      // Start the TrackerSubscriber
      val ts2 = system.actorOf(LocationSubscriberActor.props)

      // TrackerSubscriber tracks one connection
      fakeAssembly.send(ts2, LocationService.TrackConnection(c1))

      // Now wait a bit and see if the client has received updates
      expectNoMsg(500.millis)

      tsc1 ! GetResults
      // Get the results
      val result1 = expectMsgClass(classOf[Results])
      result1.msgs.size shouldBe 2
      result1.msgs.collect { case r:ResolvedAkkaLocation => r }.size shouldBe 0
      result1.msgs.collect { case h:ResolvedTcpLocation => h }.size shouldBe 2
      result1.msgs.collect { case un:Unresolved => un}.size shouldBe 0

      tsc2 ! GetResults
      // Get the results
      val result2 = expectMsgClass(classOf[Results])
      result2.msgs.size shouldBe 2
      result2.msgs.collect { case r:ResolvedAkkaLocation => r }.size shouldBe 0
      result2.msgs.collect { case h:ResolvedTcpLocation => h }.size shouldBe 2
      result2.msgs.collect { case un:Unresolved => un}.size shouldBe 0

      expectNoMsg(500.millis)
    }
  }

}
