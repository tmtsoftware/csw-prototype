package csw.services.locs

/**
 * Access type: Indicate if it is an http server or an akka actor.
 */
sealed trait AccessType {
  def name: String
  override def toString = name
}

object AccessType {
  /**
   * Gets an AccessType from the string value
   */
  def apply(name: String): AccessType = name match {
    case "http" ⇒ HttpType
    case "akka" ⇒ AkkaType
    case _      ⇒ UnknownType
  }

  /**
   * Type of a REST/HTTP based service
   */
  case object HttpType extends AccessType { val name = "http" }

  /**
   * Type of an Akka actor based service
   */
  case object AkkaType extends AccessType { val name = "akka" }

  /**
   * Unknown type of service
   */
  case object UnknownType extends AccessType { val name = "unknown" }
}

