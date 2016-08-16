package csw.services.loc

import java.net.URI

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.scalalogging.slf4j.{LazyLogging, Logger}
import csw.services.loc.Connection.{AkkaConnection, HttpConnection}
import org.slf4j.LoggerFactory

import scala.concurrent.Future

/**
  * TMT Source Code: 8/4/16.
  */
object TestLocationService extends LocationServiceProvider {
  private val logger = Logger(LoggerFactory.getLogger(TestLocationService.getClass))

  import LocationServiceProvider._

  // Private loc is for testing
  private[loc] var connections = Map.empty[Connection, Location]

  def initInterface(): Unit = {
    logger.info("Initializing TestLocationService")
    connections = Map.empty[Connection, Location]
  }

  // Gets the full URI for the actor
  def getActorUri(actorRef: ActorRef, system: ActorSystem): URI = new URI(actorRef.path.address.toString)

  private case class TestRegisterResult(componentId: ComponentId) extends RegistrationResult {
    override def unregister(): Unit = {}
  }

  def doAkkaResolve(connection: AkkaConnection, actorRef: ActorRef, system: ActorSystem, prefix: String): Location = {
    val uri = getActorUri(actorRef, system)
    ResolvedAkkaLocation(connection, uri, prefix, Some(actorRef))
  }

  def registerAkkaConnection[A <: RegistrationResult](componentId: ComponentId, actorRef: ActorRef, prefix: String = "")(implicit system: ActorSystem): Future[RegistrationResult] = {
    import system.dispatcher

    val connection = AkkaConnection(componentId)
    val location = doAkkaResolve(connection, actorRef, system, prefix)
    connections = connections + (connection -> location)
    notifySubscribers(TrackConnection(connection))
    Future(TestRegisterResult(componentId))
  }

  def registerHttpConnection[A <: RegistrationResult](componentId: ComponentId, port: Int, path: String = "")(implicit system: ActorSystem): Future[RegistrationResult] = {
    import system.dispatcher

    val connection = HttpConnection(componentId)
    // This is not correct, but good for testing until I need it
    val location = ResolvedHttpLocation(connection, new URI(path), path)
    connections = connections + (connection -> location)
    Future(TestRegisterResult(componentId))
  }

  /*  Suggest removing this as only used in RegistrationTracker, which should also be removed
  /**
    * Registers a component connection with the location sevice.
    * The component will automatically be unregistered when the vm exists or when
    * unregister() is called on the result of this method.
    *
    * @param reg    component registration information
    * @param system akka system
    * @return a future result that completes when the registration has completed and can be used to unregister later
    */
  def register(reg: Registration)(implicit system: ActorSystem): Future[RegistrationResult] = {
    reg match {
      case AkkaRegistration(connection, component, prefix) => registerAkkaConnection(connection.componentId, component, prefix)

      case HttpRegistration(connection, port, path) => registerHttpConnection(connection.componentId, port, path)
    }
  }
  */

  def unregisterConnection(connection: Connection): Unit = {
    def rm(loc: Location): Unit = {
      if (loc.isResolved) {
        val unc = Unresolved(loc.connection)
        connections += (loc.connection -> unc)
        //sendLocationUpdate(unc)
      }
    }
    connections.get(connection).foreach(rm)
  }

  case object subscribe

  case object unsubscribe

  // List of actors that subscribe to this publisher
  private var subscribers = Set[ActorRef]()

  // Subscribes the given actorRef
  def subscribe(actorRef: ActorRef): Unit = {
    if (!subscribers.contains(actorRef)) {
      subscribers += actorRef
    }
  }

  // Unsubscribes the given actorRef
  def unsubscribe(actorRef: ActorRef): Unit = {
    subscribers -= actorRef
  }

  /**
    * Notifies all subscribers with the given value
    */
  private def notifySubscribers(a: Any): Unit = {
    subscribers.foreach(_ ! a)
  }

  def trackerProps(replyTo: Option[ActorRef]): Props = TestLocationTracker.props(connections, replyTo)

  //override def getTracker(replyTo: Option[ActorRef]): LocationTrackerProvider =

}

