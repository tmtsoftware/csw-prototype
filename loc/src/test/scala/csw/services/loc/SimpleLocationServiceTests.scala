package csw.services.loc

//import akka.actor.ActorSystem
//import akka.testkit.{ImplicitSender, TestKit}
//import com.typesafe.scalalogging.slf4j.LazyLogging
//import csw.services.loc.Connection.AkkaConnection
//import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}
//
//import scala.concurrent.duration._
//
//object SimpleLocationServiceTests {
//  LocationService.initInterface()
//
//  val system = ActorSystem("SimpleLocationServiceTests")
//}
//
// XXX allan TODO FIXME upgrade test to new API
//class SimpleLocationServiceTests extends TestKit(SimpleLocationServiceTests.system)
//    with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {
//
//  test("Test location service") {
//    val connections: Set[Connection] = Set(AkkaConnection(ComponentId("TestService", ComponentType.Assembly)))
//    system.actorOf(LocationService.props(connections, Some(self)))
//
//    // register
//    LocationService.registerAkkaConnection(connections.head.componentId, testActor, "test.prefix")
//    within(25.seconds) {
//      val ready = expectMsgType[ServicesReady](20.seconds)
//      logger.debug(s"Services ready: $ready")
//      assert(connections == ready.services.keys)
//    }
//  }
//
//}

