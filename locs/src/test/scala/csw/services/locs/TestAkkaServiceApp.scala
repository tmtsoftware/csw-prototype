package csw.services.locs

import akka.actor._
import csw.services.locs.AccessType.AkkaType


object TestAkkaServiceApp extends App {
  implicit lazy val system = ActorSystem("TestAkkaServiceApp")
  implicit val dispatcher = system.dispatcher
  sys.addShutdownHook(system.shutdown())
  system.actorOf(Props(classOf[TestAkkaService]))
}

object TestAkkaService {
  val serviceId = ServiceId("TestAkkaService", ServiceType.Assembly)
  val serviceRef = ServiceRef(serviceId, AkkaType)
}

/**
 * A dummy akka test service that registers with the location service
 */
class TestAkkaService extends Actor with ActorLogging {
  LocationService.registerAkkaService(TestAkkaService.serviceId, self, "test.akka.prefix")(context.system)
  override def receive: Receive = {
    case x =>
      log.error(s"Received unexpected message $x")
  }
}
