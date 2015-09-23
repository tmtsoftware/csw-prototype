package csw.services.locs

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.locs.AccessType.AkkaType
import csw.services.locs.LocationService.{Disconnected, ServicesReady}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Failure}

class LocationServiceTests extends TestKit(ActorSystem("Test"))
with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {

  import system.dispatcher

  test("Test location service") {
    val serviceRefs = Set(ServiceRef(ServiceId("TestService", ServiceType.Assembly), AkkaType))
    system.actorOf(LocationService.props(serviceRefs, self))

    // register
    val f = LocationService.registerAkkaService(serviceRefs.head.serviceId, testActor, "test.prefix")
    f.onComplete {
      case Success(reg) =>
        logger.info("Wating for services...")
//        logger.info("Test passed, closing")
//        reg.close()
      case Failure(ex) =>
        logger.error("Test failed", ex)
    }
    Await.ready(f, 10.second)
    within(10.seconds) {
      val ready = expectMsgType[ServicesReady](10.seconds)
      logger.info(s"Services ready: $ready")
      assert(serviceRefs == ready.services.keys)
    }
  }

}

