package csw.services.locs

import akka.actor._
import csw.services.locs.AccessType.HttpType

object TestHttpServiceApp extends App {
  implicit lazy val system = ActorSystem("TestHttpServiceApp")
  implicit val dispatcher = system.dispatcher
  sys.addShutdownHook(system.shutdown())
  system.actorOf(Props(classOf[TestHttpService]))
}

object TestHttpService {
  val serviceId = ServiceId("TestHttpService", ServiceType.Assembly)
  val serviceRef = ServiceRef(serviceId, HttpType)
}

/**
 * A dummy akka test service that registers with the location service
 */
class TestHttpService extends Actor with ActorLogging {
  import context.dispatcher

  val port = 12345 // This should the actually port the HTTP server is running on...
  LocationService.registerHttpService(TestHttpService.serviceId, port, "test.http.prefix")
  override def receive: Receive = {
    case x =>
      log.error(s"Received unexpected message $x")
  }
}
