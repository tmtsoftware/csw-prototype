package csw.services.loc

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.LocationService.ServicesReady
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.duration._

object LocationServiceTests {
  LocationService.initInterface()

  val system = ActorSystem("Test")
}

class LocationServiceTests extends TestKit(LocationServiceTests.system)
    with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {

  test("Test location service") {
    val connections: Set[Connection] = Set(AkkaConnection(ComponentId("TestService", ComponentType.Assembly)))
    system.actorOf(LocationService.props(connections, Some(self)))

    // register
    LocationService.registerAkkaService(connections.head.componentId, testActor, "test.prefix")
    within(25.seconds) {
      val ready = expectMsgType[ServicesReady](20.seconds)
      logger.info(s"Services ready: $ready")
      assert(connections == ready.services.keys)
    }
  }

}

