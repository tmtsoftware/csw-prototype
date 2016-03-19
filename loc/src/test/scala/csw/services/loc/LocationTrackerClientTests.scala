package csw.services.loc

import akka.actor._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.LocationService.LocationTrackerWorker.LocationsReady
import csw.services.loc.LocationService._
import csw.util.Components.{AkkaConnection, Assembly, ComponentId, HttpConnection}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.Await
import scala.concurrent.duration._

class TestActor extends Actor with ActorLogging {

  val tracker = context.system.actorOf(LocationTracker.props(Some(context.self)))

  val trackerClient = LocationTrackerClient(tracker)

  def receive = trackerClient.trackerClientReceive

}

class TestActor2 extends Actor with ActorLogging with LocationTrackerClient2 {

  //val tracker = context.system.actorOf(LocationTracker.props(Some(context.self)))

  //val trackerClient = LocationTrackerClient(tracker)

  def receive = trackerClientReceive

}


/**
  * TMT Source Code: 3/2/16.
  */
class LocationTrackerClientTests extends TestKit(ActorSystem("Test"))
  with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {

  override def afterAll = TestKit.shutdownActorSystem(system)

  test("Test Location Service Client") {

    val componentId = ComponentId("TestAss1", Assembly)
    val testPrefix = "test.prefix"
    val testPort = 1000

    val testProbe = TestProbe()

    LocationService.registerHttpConnection(componentId, testPort, testPrefix)

    val tester = TestActorRef[TestActor].underlyingActor

    val hc = HttpConnection(componentId)

    tester.trackerClient.trackConnection(hc)

    // Just to wait - possibly should have a way of being notified, but maybe component doesn't need to know
    testProbe.expectNoMsg(15.seconds)
    assert(tester.trackerClient.connections.size == 1)
    assert(tester.trackerClient.allResolved)
  }

  test("Test Locaction Service Client with two comps") {

    val componentId = ComponentId("TestAss1", Assembly)
    val testPrefix = "test.prefix"
    val testPort = 1000
    val testProbe = TestProbe()

    LocationService.registerHttpConnection(componentId, testPort, testPrefix)
    LocationService.registerAkkaConnection(componentId, testProbe.ref, testPrefix)

    val tester = TestActorRef[TestActor].underlyingActor

    val hc = HttpConnection(componentId)
    val ac = AkkaConnection(componentId)

    tester.trackerClient.trackConnection(hc)

    // These tests are potentially not safe with underlying actor
    testProbe.expectNoMsg(10.seconds)  // Just to wait
    assert(tester.trackerClient.connections.size == 1)
    assert(tester.trackerClient.allResolved)

    tester.trackerClient.trackConnection(ac)
    assert(tester.trackerClient.connections.size == 2)
    assert(!tester.trackerClient.allResolved)

    testProbe.expectNoMsg(10.seconds)  // Just to wait
    assert(tester.trackerClient.connections.size == 2)
    assert(tester.trackerClient.allResolved)

    // Now remove one - it should still be allResolved since we removed one
    tester.trackerClient.untrackConnection(ac)
    testProbe.expectNoMsg(10.seconds)  // Just to wait
    assert(tester.trackerClient.connections.size == 1)
    assert(tester.trackerClient.allResolved)
  }

  test("Test Location Service Client2") {

    val componentId = ComponentId("TestAss1", Assembly)
    val testPrefix = "test.prefix"
    val testPort = 1000

    val testProbe = TestProbe()

    LocationService.registerHttpConnection(componentId, testPort, testPrefix)

    val tester = TestActorRef[TestActor2].underlyingActor

    val hc = HttpConnection(componentId)

    tester.trackConnection(hc)

    // Just to wait - possibly should have a way of being notified, but maybe component doesn't need to know
    testProbe.expectNoMsg(15.seconds)
    assert(tester.connections.size == 1)
    assert(tester.allResolved)

    LocationService.unregisterConnection(hc)

    // Just to wait - possibly should have a way of being notified, but maybe component doesn't need to know
    testProbe.expectNoMsg(15.seconds)
    assert(tester.connections.size == 1)
    assert(!tester.allResolved)
  }

  test("Test Location TrackerWorker") {

    val componentId = ComponentId("TestAss1", Assembly)
    val testPrefix = "test.prefix"
    val testPort = 1000

    val testProbe = TestProbe()

    LocationService.registerHttpConnection(componentId, testPort, testPrefix)
    testProbe.expectNoMsg(15.seconds)

    val hc = HttpConnection(componentId)

    val tester = system.actorOf(LocationTrackerWorker.props(Some(testProbe.ref)), "test")

    tester ! LocationTrackerWorker.TrackConnections(Set(hc))

    val result = testProbe.expectMsgType[LocationsReady](30.seconds)
    logger.info("Result =:" + result)

    system.stop(tester)
  }

  test("Now test resolve in Location Service") {

    val componentId = ComponentId("TestAss1", Assembly)
    val testPrefix = "test.prefix"
    val testPort = 1000

    val testProbe = TestProbe()

    LocationService.registerAkkaConnection(componentId, testProbe.ref, testPrefix)
    LocationService.registerHttpConnection(componentId, testPort, testPrefix)
    testProbe.expectNoMsg(15.seconds)

    val ac = AkkaConnection(componentId)
    val hc = HttpConnection(componentId)

    implicit val timeout: Timeout = 40.seconds
    val future = LocationService.resolve(Set(hc, ac))
    val result = Await.result(future, timeout.duration)
    logger.info(s" Got it: ${result.locations}")
    assert(result.locations.size == 2)

    testProbe.expectNoMsg(10.seconds)
  }

  test("Try to do like Allan") {
    val componentId = ComponentId("TestAss1", Assembly)
    val testPrefix = "test.prefix"

    import system.dispatcher
    implicit val timeout: Timeout = 60.seconds

    val testProbe = TestProbe()

    LocationService.registerAkkaConnection(componentId, testProbe.ref, testPrefix)
    testProbe.expectNoMsg(15.seconds)

    val ac = AkkaConnection(componentId)

    LocationService.resolve(Set(ac)).map(lr => lr.locations.head).mapTo[ResolvedAkkaLocation] onSuccess {
      case ral =>  logger.info("Its: " + ral.actorRef)
    }

    //f1 onSuccess { case ResolvedAkkaLocation(_, _, _, actorRef) => logger.info("Its: " + actorRef) }


    //val result = Await.result(f1, 10.second)
    //logger.info("Result: " + result)
    testProbe.expectNoMsg(10.seconds)

  }

}

