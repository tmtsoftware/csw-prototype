package csw.services.loc

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.AccessType.AkkaType
import csw.services.loc.LocationService.{Disconnected, ServicesReady}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Failure}

object LocationServiceTests {
  LocationService.initInterface()

  val system = ActorSystem("Test")
}

class LocationServiceTests extends TestKit(LocationServiceTests.system)
    with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {

  import system.dispatcher

  test("Test location service") {
    val serviceRefs = Set(ServiceRef(ServiceId("TestService", ServiceType.Assembly), AkkaType))
    system.actorOf(LocationService.props(serviceRefs, Some(self)))

    // register
    LocationService.registerAkkaService(serviceRefs.head.serviceId, testActor, "test.prefix")
    within(25.seconds) {
      val ready = expectMsgType[ServicesReady](20.seconds)
      logger.info(s"Services ready: $ready")
      assert(serviceRefs == ready.services.keys)
    }
  }

}

