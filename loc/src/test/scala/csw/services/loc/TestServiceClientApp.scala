package csw.services.loc

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import csw.services.loc.LocationService.Location

// XXX allan: TODO: Update to new API

/**
 * A location service test client application that attempts to resolve one or more sets of
 * akka and http services.
 * If a command line arg is given, it should be the number of (akka, http) pairs of services to start (default: 1 of each).
 * The client and service applications can be run on the same or different hosts.
 */
object TestServiceClientApp extends App {
  val numServices = args.headOption.map(_.toInt).getOrElse(1)
  LocationService.initInterface()
  implicit lazy val system = ActorSystem("TestServiceClientApp")
  implicit val dispatcher = system.dispatcher
  sys.addShutdownHook(system.terminate())
  system.actorOf(TestServiceClient.props(numServices))
}

object TestServiceClient {
  def props(numServices: Int): Props = Props(classOf[TestServiceClient], numServices)
}

/**
 * A test client actor that uses the location service to resolve services
 */
class TestServiceClient(numServices: Int) extends Actor with ActorLogging with LocationTrackerClientActor {
  val connections = (1 to numServices).toList.flatMap(i ⇒ List(TestAkkaService.connection(i), TestHttpService.connection(i))).toSet
  connections.foreach(trackConnection)

  override def receive: Receive = trackerClientReceive orElse {
    case x ⇒
      log.error(s"Received unexpected message $x")
  }

  override protected def allResolved(locations: Set[Location]): Unit = {
    log.info(s"Test Passed: Received services: ${locations.map(_.connection.componentId.name).mkString(", ")}")
  }
}

