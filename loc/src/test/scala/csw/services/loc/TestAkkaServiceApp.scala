package csw.services.loc

import akka.actor._
import csw.services.loc.Connection.AkkaConnection

/**
 * Starts one or more akka services in order to test the location service.
 * If a command line arg is given, it should be the number of services to start (default: 1).
 * Each service will have a number appended to its name.
 * You should start the TestServiceClient with the same number, so that it
 * will try to find all the services.
 * The client and service applications can be run on the same or different hosts.
 */
object TestAkkaServiceApp extends App {
  val numServices = args.headOption.map(_.toInt).getOrElse(1)
  LocationService.initInterface()
  implicit lazy val system = ActorSystem("TestAkkaServiceApp")
  implicit val dispatcher = system.dispatcher
  sys.addShutdownHook(system.terminate())
  for (i ← 1 to numServices) {
    system.actorOf(TestAkkaService.props(i))
  }
}

object TestAkkaService {
  def props(i: Int): Props = Props(classOf[TestAkkaService], i)
  def componentId(i: Int) = ComponentId(s"TestAkkaService-$i", ComponentType.Assembly)
  def connection(i: Int): Connection = AkkaConnection(componentId(i))
}

/**
 * A dummy akka test service that registers with the location service
 */
class TestAkkaService(i: Int) extends Actor with ActorLogging {
  LocationService.registerAkkaService(TestAkkaService.componentId(i), self, "test.akka.prefix")(context.system)
  override def receive: Receive = {
    case x ⇒
      log.error(s"Received unexpected message $x")
  }
}
