package csw.services.loc

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.ComponentType._
import csw.services.loc.Connection._
import csw.services.loc.LocationService.{AkkaRegistration, ComponentRegistered, HttpRegistration, RegistrationTracker}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuiteLike}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object LocationServiceTests {
  LocationService.initInterface()

  val system = ActorSystem("Test")
}

class LocationServiceTests extends TestKit(LocationServiceTests.system)
    with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with BeforeAndAfterEach with LazyLogging {

  import system.dispatcher

  override def afterAll = TestKit.shutdownActorSystem(system)

  test("Test location service register with only") {

    val componentId = ComponentId("TestAss1", Assembly)

    val trackerResponseProbe = TestProbe()

    val actorTestProbe = TestProbe()

    val akkaConnection = AkkaConnection(componentId)
    val akkaRegister = AkkaRegistration(akkaConnection, actorTestProbe.ref, "test.prefix1")

    val tracker = system.actorOf(RegistrationTracker.props(Set(akkaRegister), Some(trackerResponseProbe.ref)))

    val m1 = Seq(trackerResponseProbe.expectMsgType[ComponentRegistered](10.second))
    assert(m1.size == 1)
    assert(m1.exists(_.connection == akkaConnection))
    m1.head.result.unregister()
    system.stop(tracker)
  }

  test("Test location service register with both akka and http as sequence") {

    val componentId = ComponentId("TestAss2", Assembly)

    val trackerResponseProbe = TestProbe()

    val actorTestProbe = TestProbe()

    val akkaConnection = AkkaConnection(componentId)
    val httpConnection = HttpConnection(componentId)

    val akkaRegister = AkkaRegistration(akkaConnection, actorTestProbe.ref, "test.prefix2")
    val httpRegister = HttpRegistration(httpConnection, 1000, "test.prefix2")

    val tracker = system.actorOf(RegistrationTracker.props(Set(akkaRegister, httpRegister), Some(trackerResponseProbe.ref)))

    val m1 = Seq(
      trackerResponseProbe.expectMsgType[ComponentRegistered](10.second),
      trackerResponseProbe.expectMsgType[ComponentRegistered](10.second))

    assert(m1.size == 2)
    assert(m1.exists(_.connection == akkaConnection))
    assert(m1.exists(_.connection == httpConnection))
    m1.foreach(_.result.unregister())
    system.stop(tracker)
  }

  test("Test tracker with one Akka component") {
    import LocationService._

    val componentId = ComponentId("TestAss3", Assembly)
    val testPrefix = "test.prefix3"
    val testProbe = TestProbe()
    val actorTestProbe = TestProbe()

    val f = LocationService.registerAkkaConnection(componentId, actorTestProbe.ref, testPrefix)

    val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)), "LocationTracker!")

    val ac = AkkaConnection(componentId)

    tracker ! TrackConnection(ac)

    testProbe.expectMsg(15.seconds, Unresolved(ac))

    val ready = testProbe.expectMsgClass(10.seconds, classOf[ResolvedAkkaLocation])
    assert(ready.connection == ac)
//    expectNoMsg(5.seconds)

    val result = Await.result(f, 1.second)
    result.unregister()
    system.stop(tracker)
  }

  test("Test tracker with one Akka component - try to add twice") {
    import LocationService._

    val componentId = ComponentId("TestAss4", Assembly)
    val testPrefix = "test.prefix4"

    val testProbe = TestProbe()
    val actorTestProbe = TestProbe()

    val f = LocationService.registerAkkaConnection(componentId, actorTestProbe.ref, testPrefix)

    val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)))

    val ac = AkkaConnection(componentId)

    tracker ! TrackConnection(ac)

    testProbe.expectMsg(15.seconds, Unresolved(ac))

    val ready = testProbe.expectMsgClass(10.seconds, classOf[ResolvedAkkaLocation])
    assert(ready.connection == ac)

//    expectNoMsg(5.seconds)

    tracker ! TrackConnection(ac)
//    expectNoMsg(5.seconds)
    val result = Await.result(f, 1.second)
    result.unregister()
    system.stop(tracker)
  }

  test("Test tracker with one HTTP component") {
    import LocationService._

    val componentId = ComponentId("TestAss5", Assembly)
    val testPrefix = "test.prefix5"
    val testPort = 1000

    val testProbe = TestProbe()

    val f = LocationService.registerHttpConnection(componentId, testPort)

    val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)))

    val hc = HttpConnection(componentId)

    tracker ! TrackConnection(hc)

    testProbe.expectMsg(15.seconds, Unresolved(hc))

    val ready = testProbe.expectMsgClass(10.seconds, classOf[ResolvedHttpLocation])
    assert(ready.connection == hc)

//    expectNoMsg(5.seconds)
    val result = Await.result(f, 1.second)
    result.unregister()
    system.stop(tracker)
  }

  test("Test tracker with two components registered before tracker") {
    import LocationService._

    val componentId = ComponentId("TestAss6", Assembly)
    val testPrefix = "test.prefix6"
    val testPort = 1000

    val testProbe = TestProbe()

    val actorTestProbe = TestProbe()

    val fList = List(
      LocationService.registerHttpConnection(componentId, testPort),
      LocationService.registerAkkaConnection(componentId, actorTestProbe.ref, testPrefix))

    val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)))

    val ac = AkkaConnection(componentId)
    val hc = HttpConnection(componentId)

    tracker ! TrackConnection(ac)
    testProbe.expectMsg(15.seconds, Unresolved(ac))
    val r1 = testProbe.expectMsgClass(10.seconds, classOf[ResolvedAkkaLocation])
    assert(r1.connection == ac)

//    expectNoMsg(5.seconds) // Give time for all to be registered

    tracker ! TrackConnection(hc)
    testProbe.expectMsg(15.seconds, Unresolved(hc))
    val r2 = testProbe.expectMsgClass(15.seconds, classOf[ResolvedHttpLocation])
    assert(r2.connection == hc)

    // Assure no messages coming for no tracking
//    testProbe.expectNoMsg(5.seconds)

    val f = Future.sequence(fList)
    val resultList = Await.result(f, 1.second)
    resultList.foreach(_.unregister())
    system.stop(tracker)
  }

  test("Test tracker to ensure no messages without a registered comp") {
    import LocationService._

    val testProbe = TestProbe()

    val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)))

    // Assure no messages coming for no tracking
    testProbe.expectNoMsg(5.seconds)
    system.stop(tracker)
  }

  test("Test tracker with two components register later") {
    import LocationService._

    val componentId = ComponentId("TestAss7", Assembly)
    val testPrefix = "test.prefix7"
    val testPort = 1000

    val testProbe = TestProbe()

    val actorTestProbe = TestProbe()

    val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)))

    val ac = AkkaConnection(componentId)
    val hc = HttpConnection(componentId)

    tracker ! TrackConnection(ac)
    tracker ! TrackConnection(hc)
    testProbe.expectMsg(15.seconds, Unresolved(ac))
    testProbe.expectMsg(15.seconds, Unresolved(hc))

    // Assure no messages coming for no tracking
//    testProbe.expectNoMsg(5.seconds)

    val f1 = LocationService.registerAkkaConnection(componentId, actorTestProbe.ref, testPrefix)

    val r1 = testProbe.expectMsgClass(10.seconds, classOf[ResolvedAkkaLocation])
    assert(r1.connection == ac)

    val f2 = LocationService.registerHttpConnection(componentId, testPort)
    val r2 = testProbe.expectMsgClass(15.seconds, classOf[ResolvedHttpLocation])
    assert(r2.connection == hc)
    // Assure no messages coming for no tracking
//    testProbe.expectNoMsg(5.seconds)

    val f = Future.sequence(List(f1, f2))
    val resultList = Await.result(f, 1.second)
    resultList.foreach(_.unregister())
    system.stop(tracker)
  }

  test("Test tracker with two components then remove one") {
    import LocationService._

    val componentId = ComponentId("TestAss8", Assembly)
    val testPrefix = "test.prefix8"
    val testPort = 1000

    val testProbe = TestProbe()

    val actorTestProbe = TestProbe()

    val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)))

    val ac = AkkaConnection(componentId)
    val hc = HttpConnection(componentId)

    tracker ! TrackConnection(ac)
    tracker ! TrackConnection(hc)
    testProbe.expectMsg(15.seconds, Unresolved(ac))
    testProbe.expectMsg(15.seconds, Unresolved(hc))

    // Assure no messages coming for no tracking
//    testProbe.expectNoMsg(5.seconds)

    val f1 = LocationService.registerAkkaConnection(componentId, actorTestProbe.ref, testPrefix)

    val r1 = testProbe.expectMsgClass(10.seconds, classOf[ResolvedAkkaLocation])
    assert(r1.connection == ac)

    val f2 = LocationService.registerHttpConnection(componentId, testPort)
    val r2 = testProbe.expectMsgClass(15.seconds, classOf[ResolvedHttpLocation])
    assert(r2.connection == hc)

    tracker ! UnTrackConnection(hc)

    val r3 = testProbe.expectMsgClass(15.seconds, classOf[UnTrackedLocation])
    assert(r3.connection == hc)

    // Re-add it again
    tracker ! TrackConnection(hc)
    testProbe.expectMsg(15.seconds, Unresolved(hc))
    val r4 = testProbe.expectMsgClass(15.seconds, classOf[ResolvedHttpLocation])
    assert(r4.connection == hc)
    // Assure no messages coming for no tracking
//    testProbe.expectNoMsg(5.seconds)

    val f = Future.sequence(List(f1, f2))
    val resultList = Await.result(f, 1.second)
    resultList.foreach(_.unregister())
    system.stop(tracker)
  }
}
