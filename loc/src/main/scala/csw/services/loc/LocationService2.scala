package csw.services.loc

import java.net.URI

import akka.actor._
import csw.services.loc.Connection.{AkkaConnection, HttpConnection, TcpConnection}

import scala.concurrent.Future

/**
 * Location Service based on Multicast DNS (AppleTalk, Bonjour).
 *
 * The Location Service is based on Multicast DNS (AppleTalk, Bonjour) and can be used to register and lookup
 * akka and http based services in the local network.
 *
 * Every application using the location service should call the initInterface() method once at startup,
 * before creating any actors.
 *
 * Note: On a mac, you can use the command line tool dns-sd to browse the registered services.
 * On Linux use avahi-browse.
 */

abstract class LocationServiceProvider {

  import LocationServiceProvider._

  def initInterface()

  def registerAkkaConnection[A <: RegistrationResult](componentId: ComponentId, actorRef: ActorRef, prefix: String = "")(implicit system: ActorSystem): Future[RegistrationResult]

  def registerHttpConnection[A <: RegistrationResult](componentId: ComponentId, port: Int, path: String = "")(implicit system: ActorSystem): Future[RegistrationResult]

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

  def trackerProps(replyTo: Option[ActorRef]): Props

}

object LocationServiceProvider {

  /**
   * Represents a registered connection to a service
   */
  sealed trait Registration {
    def connection: Connection
  }

  /**
   * Represents a registered connection to an Akka service
   */
  final case class AkkaRegistration(connection: AkkaConnection, component: ActorRef, prefix: String = "") extends Registration

  /**
   * Represents a registered connection to a HTTP based service
   */
  final case class HttpRegistration(connection: HttpConnection, port: Int, path: String) extends Registration

  /**
   * Represents a registered connection to a TCP based service
   */
  final case class TcpRegistration(connection: TcpConnection, port: Int, path: String) extends Registration

  /**
   * Returned from register calls so that client can close the connection and deregister the service
   */
  trait RegistrationResult {
    /**
     * Unregisters the previously registered service.
     * Note that all services are automatically unregistered on shutdown.
     */
    def unregister(): Unit

    /**
     * Identifies the registered component
     */
    val componentId: ComponentId
  }

  case class ComponentRegistered(connection: Connection, result: RegistrationResult)

  case class TrackConnection(connection: Connection)

  case class UntrackConnection(connection: Connection)

  sealed trait Location {
    def connection: Connection

    val isResolved: Boolean = false
    val isTracked: Boolean = true
  }

  final case class UnTrackedLocation(connection: Connection) extends Location {
    override val isTracked = false
  }

  final case class Unresolved(connection: Connection) extends Location

  final case class ResolvedAkkaLocation(connection: AkkaConnection, uri: URI, prefix: String = "", actorRef: Option[ActorRef] = None) extends Location {
    override val isResolved = true
  }

  final case class ResolvedHttpLocation(connection: HttpConnection, uri: URI, path: String) extends Location {
    override val isResolved = true
  }

  final case class ResolvedTcpLocation(connection: TcpConnection, uri: URI, path: String) extends Location {
    override val isResolved = true
  }
}

