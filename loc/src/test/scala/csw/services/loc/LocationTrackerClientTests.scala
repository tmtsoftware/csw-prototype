package csw.services.loc

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.ComponentType._
import csw.services.loc.Connection._
import csw.services.loc.LocationService.{Location, LocationTracker, LocationTrackerWorker, ResolvedAkkaLocation}
import csw.services.loc.LocationService.LocationTrackerWorker.LocationsReady
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._


object LocationTrackerClientTests {
  // This needs to be called first!
  LocationService.initInterface()

  // This is here only to make sure the above call is made first!
  val mySystem = ActorSystem("Test")

  // Local test actor to test the tracker client
  object TestActor {
    sealed trait TestActorMessage
    case class AllResolved(locations: Set[Location]) extends TestActorMessage
    case class NotAllResolved(locations: Set[Location]) extends TestActorMessage
    case object QueryResolved extends TestActorMessage
    case class TrackConnection(connection: Connection) extends TestActorMessage
    case class UntrackConnection(connection: Connection) extends TestActorMessage

    def props(replyTo: ActorRef): Props = Props(classOf[TestActor], replyTo)
  }

  class TestActor(replyTo: ActorRef) extends Actor with ActorLogging {
    import TestActor._
    private val tracker = context.actorOf(LocationTracker.props(Some(self)))
    private val trackerClient = LocationTrackerClient(tracker)

    override def receive = recv()

    // query is set to true once after a QueryResolved message was received
    def recv(query: Boolean = false): Receive = {
      case loc: Location ⇒
        log.info(s"Received location: $loc")
        trackerClient.trackerClientReceive(loc)
        if (trackerClient.allResolved)
          replyTo ! AllResolved(trackerClient.getLocations)
        else if (loc.isResolved || query)
          replyTo ! NotAllResolved(trackerClient.getLocations)
        context.become(recv(false))

      case TrackConnection(connection) =>
        trackerClient.trackConnection(connection)

      case UntrackConnection(connection) =>
        trackerClient.untrackConnection(connection)

      case QueryResolved =>
        context.become(recv(true))

      case x             ⇒
        log.error(s"Unexpected message: $x")
    }
  }

  class TestActor2 extends Actor with ActorLogging with LocationTrackerClient2 {
    def receive = trackerClientReceive
  }
}

// Tests: Note that LocationTrackerClientTests.mySystem is used to ensure that the
// LocationService.initInterface() is called first.
class LocationTrackerClientTests extends TestKit(LocationTrackerClientTests.mySystem)
    with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {

  import system.dispatcher
  import LocationTrackerClientTests.TestActor
  import LocationTrackerClientTests.TestActor._

  override def afterAll = TestKit.shutdownActorSystem(system)
  val t: FiniteDuration = 20.seconds

  test("Test Location Service Client") {

    val componentId = ComponentId("TestAss1", Assembly)
    val testPrefix = "test.prefix"
    val testPort = 1000
    val f = LocationService.registerHttpConnection(componentId, testPort)
    val testProbe = TestProbe("probe1")
    val tester = system.actorOf(TestActor.props(testProbe.ref))
    val hc = HttpConnection(componentId)

    tester ! TrackConnection(hc)

    val locations = testProbe.expectMsgType[AllResolved](t).locations
    assert(locations.size == 1)

    val result = Await.result(f, t)
    result.unregister()
    system.stop(tester)
  }

  test("Test Location Service Client with two comps") {
    val componentId = ComponentId("TestAss2", Assembly)
    val testPrefix = "test.prefix"
    val testPort = 1000
    val testProbe = TestProbe("probe2")

    val tester = system.actorOf(TestActor.props(testProbe.ref))

    val f = Future.sequence(List(LocationService.registerHttpConnection(componentId, testPort),
      LocationService.registerAkkaConnection(componentId, testProbe.ref, testPrefix)))


    val hc = HttpConnection(componentId)
    val ac = AkkaConnection(componentId)

    tester ! TrackConnection(hc)

    val locations = testProbe.expectMsgType[AllResolved](t).locations
    assert(locations.size == 1)

    tester ! TrackConnection(ac)

    val locations2 = testProbe.expectMsgType[AllResolved](t).locations
    assert(locations2.size == 2)

    // Now remove one - it should still be allResolved since we removed one
    tester ! UntrackConnection(ac)
    tester ! QueryResolved
    testProbe.expectMsgType[AllResolved](t).locations

    val resultList = Await.result(f, t)
    resultList.foreach(_.unregister())
    system.stop(tester)
  }

  test("Test Location Service Client2") {
    val componentId = ComponentId("TestAss3", Assembly)
    val testPrefix = "test.prefix"
    val testPort = 1000
    val testProbe = TestProbe("probe3")

    val f = LocationService.registerHttpConnection(componentId, testPort)
    Await.result(f, t)
    val tester = system.actorOf(TestActor.props(testProbe.ref))
    val hc = HttpConnection(componentId)

    tester ! TrackConnection(hc)

    val locations = testProbe.expectMsgType[AllResolved](t).locations
    assert(locations.size == 1)

    LocationService.unregisterConnection(hc)
    tester ! QueryResolved
    val locations2 = testProbe.expectMsgType[NotAllResolved](t).locations
    assert(locations2.size == 1)

    system.stop(tester)
  }

  test("Test Location TrackerWorker") {
    val componentId = ComponentId("TestAss4", Assembly)
    val testPrefix = "test.prefix"
    val testPort = 1000
    val f = LocationService.registerHttpConnection(componentId, testPort)
    val hc = HttpConnection(componentId)
    val testProbe = TestProbe("probe4")
    val tester = system.actorOf(LocationTrackerWorker.props(Some(testProbe.ref)))

    tester ! LocationTrackerWorker.TrackConnections(Set(hc))

    val result = testProbe.expectMsgType[LocationsReady](30.seconds)
    logger.info("Result =:" + result)

    val r = Await.result(f, t)
    r.unregister()
  }

  test("Now test resolve in Location Service") {
    val componentId = ComponentId("TestAss5", Assembly)
    val testPrefix = "test.prefix"
    val testPort = 1000
    val testProbe = TestProbe("probe5")

    val f = Future.sequence(
      List(LocationService.registerAkkaConnection(componentId, testProbe.ref, testPrefix),
        LocationService.registerHttpConnection(componentId, testPort)))

    val ac = AkkaConnection(componentId)
    val hc = HttpConnection(componentId)

    implicit val timeout: Timeout = 40.seconds
    val future = LocationService.resolve(Set(hc, ac))
    val result = Await.result(future, timeout.duration)
    logger.info(s" Got it: ${result.locations}")
    assert(result.locations.size == 2)

    val resultList = Await.result(f, t)
    resultList.foreach(_.unregister())
  }

  test("Try to do like Allan") {
    val componentId = ComponentId("TestAss6", Assembly)
    val testPrefix = "test.prefix"
    val testProbe = TestProbe("probe6")

    import system.dispatcher
    implicit val timeout: Timeout = 60.seconds

    val f = LocationService.registerAkkaConnection(componentId, testProbe.ref, testPrefix)
    val ac = AkkaConnection(componentId)

    LocationService.resolve(Set(ac)).map(_.locations.head).mapTo[ResolvedAkkaLocation] onSuccess {
      case ral ⇒ logger.info("Its: " + ral.actorRef)
    }

    val r = Await.result(f, t)
    r.unregister()
  }

}

