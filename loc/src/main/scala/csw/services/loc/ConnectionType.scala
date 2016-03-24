package csw.services.loc

/**
 * Connection type: Indicate if it is an http server or an akka actor.
 */
sealed trait ConnectionType {
  def name: String

  override def toString = name
}

object ConnectionType {

  /**
   * Type of a REST/HTTP based service
   */
  case object HttpType extends ConnectionType {
    val name = "http"
  }

  /**
   * Type of an Akka actor based service
   */
  case object AkkaType extends ConnectionType {
    val name = "akka"
  }

  /**
   * Exception throws for an unknown connection type
   */
  case class UnknownConnectionTypeException(message: String) extends Exception(message)

  /**
   * Gets a ConnectionType from the string value ("akka" or "http") or throws an UnknownConnectionTypeException
   */
  def apply(name: String): ConnectionType = name match {
    case "http" ⇒ HttpType
    case "akka" ⇒ AkkaType
    case x      ⇒ throw UnknownConnectionTypeException(x)
  }

}

