package csw.services.loc

import akka.actor._
import csw.util.Components._

object TestAkkaServiceApp extends App {
  implicit lazy val system = ActorSystem("TestAkkaServiceApp")
  implicit val dispatcher = system.dispatcher
  sys.addShutdownHook(system.terminate())
  system.actorOf(Props(classOf[TestAkkaService]))
}

object TestAkkaService {
  val componentId = ComponentId("TestAkkaService", HCD)
  val connecton = Connection(componentId, AkkaType)
}

/**
 * A dummy akka test service that registers with the location service
 */
class TestAkkaService extends Actor with ActorLogging {
  LocationService.registerAkkaConnection(TestAkkaService.componentId, self, "test.akka.prefix")(context.system)
  override def receive: Receive = {
    case x ⇒
      log.error(s"Received unexpected message $x")
  }
}
