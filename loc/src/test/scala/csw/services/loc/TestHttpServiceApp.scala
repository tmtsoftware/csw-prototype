package csw.services.loc

import akka.actor._
import csw.services.loc.Connection.HttpConnection

/**
 * Starts one or more (dummy) http services in order to test the location service.
 * If a command line arg is given, it should be the number of services to start (default: 1).
 * Each service will have a number appended to its name.
 * You should start the TestServiceClient with the same number, so that it
 * will try to find all the services.
 * The client and service applications can be run on the same or different hosts.
 */
object TestHttpServiceApp extends App {
  val numServices = args.headOption.map(_.toInt).getOrElse(1)
  LocationService.initInterface()
  implicit lazy val system = ActorSystem("TestHttpServiceApp")
  implicit val dispatcher = system.dispatcher
  sys.addShutdownHook(system.terminate())
  for (i ← 1 to numServices) {
    system.actorOf(TestHttpService.props(i))
  }
}

object TestHttpService {
  def props(i: Int): Props = Props(classOf[TestHttpService], i)
  def componentId(i: Int) = ComponentId(s"TestHttpService-$i", ComponentType.Assembly)
  def connection(i: Int): Connection = HttpConnection(componentId(i))
}

/**
 * A dummy akka test service that registers with the location service
 */
class TestHttpService(i: Int) extends Actor with ActorLogging {
  import context.system

  val port = 9000 + i // Dummy value for testing: Normally should be the actually port the HTTP server is running on...
  LocationService.registerHttpConnection(TestHttpService.componentId(i), port)
  override def receive: Receive = {
    case x ⇒
      log.error(s"Received unexpected message $x")
  }
}
