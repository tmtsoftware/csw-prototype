package csw.services.loc

import csw.services.loc.ConnectionType.{AkkaType, HttpType}

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
   * Indicates how the component is accessed (http, akka)
   */
  def connectionType: ConnectionType

  override def toString = s"$componentId-$connectionType"

  override def equals(that: Any) = that match {
    case (that: Connection) ⇒ this.toString == that.toString
    case _                  ⇒ false
  }
}

object Connection {

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
   * Gets a LocationRef from a string as output by toString
   */
  def apply(s: String): Try[Connection] = {
    val (id, typ) = s.splitAt(s.lastIndexOf('-')) // To strings
    ConnectionType(typ.drop(1)) match {
      case Success(AkkaType) ⇒ ComponentId(id).map(AkkaConnection)
      case Success(HttpType) ⇒ ComponentId(id).map(HttpConnection)
      case Failure(ex) => Failure(ex)
    }
  }

  def apply(componentId: ComponentId, connectionType: ConnectionType): Connection = {
    connectionType match {
      case AkkaType ⇒ AkkaConnection(componentId)
      case HttpType ⇒ HttpConnection(componentId)
    }
  }
}
