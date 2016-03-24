package csw.services.loc

import csw.services.loc.ConnectionType.{AkkaType, HttpType}

///**
// * Describes a component and the way it is accessed (http, akka)
//  *
//  * @param componentId holds the component name and type (assembly, hcd, etc.)
// * @param connectionType indicates how the component is accessed (http, akka)
// */
//case class Connection(componentId: ComponentId, connectionType: ConnectionType) {
//  override def toString = s"$componentId-$connectionType"
//
//  override def equals(that: Any) = that match {
//    case (that: Connection) ⇒ this.toString == that.toString;
//    case _                  ⇒ false;
//  }
//}
//
//object Connection {
//  /**
//   * Gets a Connection from a string (as output by toString)
//   */
//  def apply(s: String): Connection = {
//    val (a, b) = s.splitAt(s.lastIndexOf('-'))
//    Connection(ComponentId(a), ConnectionType(b.drop(1)))
//  }
//}

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
  def apply(s: String): Connection = {
    val (id, typ) = s.splitAt(s.lastIndexOf('-')) // To strings
    ConnectionType(typ.drop(1)) match {
      case AkkaType ⇒ AkkaConnection(ComponentId(id))
      case HttpType ⇒ HttpConnection(ComponentId(id))
    }
  }
}
