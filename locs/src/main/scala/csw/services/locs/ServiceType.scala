package csw.services.locs

/**
 * CSW Service types
 */
sealed trait ServiceType

object ServiceType {

  /**
   * Returns the named service type (default: Service)
   */
  def apply(name: String): ServiceType = name.toLowerCase match {
    case "container" ⇒ Container
    case "assembly"  ⇒ Assembly
    case "hcd"       ⇒ HCD
    case "service"   ⇒ Service
    case _           ⇒ Unknown
  }

  /**
   * A container for services, assemblies and HCDs
   */
  case object Container extends ServiceType

  /**
   * A service that controls a hardware device
   */
  case object HCD extends ServiceType

  /**
   * A service that controls one or more HCDs or assemblies
   */
  case object Assembly extends ServiceType

  /**
   * A general purpose service (actor and/or web service application)
   */
  case object Service extends ServiceType

  /**
   * An unknown service type
   */
  case object Unknown extends ServiceType
}
