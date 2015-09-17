package csw.services.locs

import akka.actor.{ Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit }
import csw.services.locs.LocationService.{ AkkaType, ServiceType, ServiceId, ServiceRef }
import org.scalatest.{ BeforeAndAfterAll, FunSuiteLike }

class LocationServiceTests extends TestKit(ActorSystem("Test"))
    with ImplicitSender with FunSuiteLike with BeforeAndAfterAll {

  test("Test location service") {
    val serviceRefs = List(ServiceRef(ServiceId("TestService", ServiceType.Assembly), AkkaType))
    val ls = system.actorOf(LocationService.props(serviceRefs))

    // register
    val port = 9876
    val values = Map("Test" -> "Passed")
    LocationService.register(serviceRefs.head, port, values)
  }
}

