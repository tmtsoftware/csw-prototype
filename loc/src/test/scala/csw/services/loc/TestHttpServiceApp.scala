package csw.services.loc

import akka.actor._
import csw.util.Components._

object TestHttpServiceApp extends App {
  implicit lazy val system = ActorSystem("TestHttpServiceApp")
  implicit val dispatcher = system.dispatcher
  sys.addShutdownHook(system.terminate())
  system.actorOf(Props(classOf[TestHttpService]))
}

object TestHttpService {
  val componentId = ComponentId("TestHttpService", Assembly)
  val connection = Connection(componentId, HttpType)
}

/**
 * A dummy akka test service that registers with the location service
 */
class TestHttpService extends Actor with ActorLogging {
  import context.dispatcher

  val port = 12345 // This should the actually port the HTTP server is running on...
  LocationService.registerHttpConnection(TestHttpService.componentId, port, "test.http.prefix")(context.system)
  override def receive: Receive = {
    case x ⇒
      log.error(s"Received unexpected message $x")
  }
}
