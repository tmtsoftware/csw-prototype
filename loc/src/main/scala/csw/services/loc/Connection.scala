package csw.services.loc

import csw.services.loc.ConnectionType.{AkkaType, HttpType, TcpType}

import scala.util.{Failure, Success, Try}

/**
 * Describes a component and the way it is accessed (http, akka)
 */
sealed trait Connection {
  /**
   * Holds the component name and type (assembly, hcd, etc.)
   */
  def componentId: ComponentId

  /**
   * Returns a connection's name
   */
  def name: String = componentId.name

  /**
   * Indicates how the component is accessed (http, akka)
   */
  def connectionType: ConnectionType

  override def toString = {
    if (Connection.servicePrefix.nonEmpty)
      s"${Connection.servicePrefix}-$componentId-$connectionType"
    else
      s"$componentId-$connectionType"
  }

  override def equals(that: Any): Boolean = that match {
    case (that: Connection) => this.toString == that.toString
    case _                  => false
  }
}

object Connection {

  /**
   * Holds a string that can be used to make the services unique (for example, during development).
   * If the system property CSW_SERVICE_PREFIX is set, use it, otherwise if an environment variable of the
   * same name is used, use it. The value, if set, is prepended to the name used to register with mDNS.
   * The name should not contain a '-' (If it does, it will be replaced with a _).
   */
  val servicePrefix: String = {
    Option(System.getProperty("CSW_SERVICE_PREFIX")).getOrElse(
      Option(System.getenv("CSW_SERVICE_PREFIX")).getOrElse("")
    ).replace('-', '_')
  }

  /**
   * A connection to a remote akka actor based component
   */
  final case class AkkaConnection(componentId: ComponentId) extends Connection {
    val connectionType = AkkaType
  }

  /**
   * A connection to a remote http based component
   */
  final case class HttpConnection(componentId: ComponentId) extends Connection {
    val connectionType = HttpType
  }

  /**
   * A connection to a remote tcp based component
   */
  final case class TcpConnection(componentId: ComponentId) extends Connection {
    val connectionType = TcpType
  }

  /**
   * Gets a Connection from a string as output by toString
   */
  def apply(s: String): Try[Connection] = {
    // s is in a format like: $user-$name-Assembly-akka
    if (servicePrefix.isEmpty || s.startsWith(s"$servicePrefix-")) {
      val ss = if (servicePrefix.isEmpty) s else s.splitAt(s.indexOf('-') + 1)._2
      val (id, typ) = ss.splitAt(ss.lastIndexOf('-'))
      ConnectionType(typ.drop(1)) match {
        case Success(AkkaType) => ComponentId(id).map(AkkaConnection)
        case Success(HttpType) => ComponentId(id).map(HttpConnection)
        case Success(TcpType)  => ComponentId(id).map(TcpConnection)
        case Failure(ex)       => Failure(ex)
      }
    } else {
      Failure(new RuntimeException("Wrong csw service prefix"))
    }
  }

  /**
   * Gets a Connection based on the component id and connection type
   */
  def apply(componentId: ComponentId, connectionType: ConnectionType): Connection = {
    connectionType match {
      case AkkaType => AkkaConnection(componentId)
      case HttpType => HttpConnection(componentId)
      case TcpType  => TcpConnection(componentId)
    }
  }
}
