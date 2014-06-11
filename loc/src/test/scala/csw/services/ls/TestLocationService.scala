package csw.services.ls

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor._
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.scalatest.{FunSuiteLike, BeforeAndAfterAll}
import LocationServiceActor._

/**
 * Simple standalone test of local location service (normally it should be run as a remote actor)
 */
class TestLocationService extends TestKit(ActorSystem("Test"))
with ImplicitSender with FunSuiteLike with LazyLogging with BeforeAndAfterAll {

  test("Test location service") {
    val ls = system.actorOf(Props[LocationServiceActor], LocationServiceActor.locationServiceName)

    // register
    val serviceId = ServiceId("TestActor", ServiceType.HCD)
    ls ! Register(serviceId, None, None)

    // resolve
    ls ! Resolve(serviceId)
    val info = expectMsgType[LocationServiceInfo]
    assert(info.serviceId == serviceId)
    assert(info.actorRefOpt == Some(self))
    assert(info.configPathOpt == None)
    assert(info.endpoints.size == 1)

    // browse
    ls ! Browse(None, None)
    ls ! Browse(Some("TestActor"), None)
    ls ! Browse(None, Some(ServiceType.HCD))
    ls ! Browse(Some("TestActor"), Some(ServiceType.HCD))
    val r1 = expectMsgType[BrowseResults]
    val r2 = expectMsgType[BrowseResults]
    val r3 = expectMsgType[BrowseResults]
    val r4 = expectMsgType[BrowseResults]
    assert(r1.results.size == 1)
    assert(r1.results(0) == info)
    assert(r2 == r1)
    assert(r3 == r1)
    assert(r4 == r1)
    ls ! Browse(Some("TestActor"), Some(ServiceType.Assembly))
    ls ! Browse(Some("XXX"), None)
    ls ! Browse(Some("XXX"), Some(ServiceType.HCD))
    val r5 = expectMsgType[BrowseResults]
    val r6 = expectMsgType[BrowseResults]
    val r7 = expectMsgType[BrowseResults]
    assert(r5.results.size == 0)
    assert(r6.results.size == 0)
    assert(r7.results.size == 0)

    // request services
    ls ! RequestServices(List(serviceId))
    val sr = expectMsgType[ServicesReady]
    assert(sr.services.size == 1)
    assert(sr.services(0) == info)
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}


