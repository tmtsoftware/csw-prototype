package csw.services.loc

/*
import akka.actor.{ Props, ActorSystem, ActorLogging, Actor }
import csw.services.loc.LocationService.{ Disconnected, ServicesReady }

object TestServiceClientApp extends App {
  implicit lazy val system = ActorSystem("TestServiceClientApp")
  implicit val dispatcher = system.dispatcher
  sys.addShutdownHook(system.terminate())
  system.actorOf(Props(classOf[TestServiceClient]))
}

/**
 * A test client actor that uses the location service to resolve services
 */
class TestServiceClient extends Actor with ActorLogging {
  val serviceRefs = Set(TestAkkaService.serviceRef, TestHttpService.serviceRef)
  context.actorOf(LocationService.props(serviceRefs))

  override def receive: Receive = {
    case ServicesReady(services) ⇒
      log.info(s"Received services: ${services.values.map(_.serviceRef.serviceId.name).mkString(", ")}")
    case Disconnected(serviceRef) ⇒
      log.info(s"Disconnected service: ${serviceRef.serviceId.name}")
    case x ⇒
      log.error(s"Received unexpected message $x")
  }
}
*/
