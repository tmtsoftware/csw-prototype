package csw.services.locs

/**
 * Used to identify a service
 * @param name the service name
 * @param serviceType HCD, Assembly, Service
 */
case class ServiceId(name: String, serviceType: ServiceType) {
  override def toString = s"$name-$serviceType"
}

object ServiceId {
  /**
   * Gets a ServiceId from a string, as output by toString
   */
  def apply(s: String): ServiceId = {
    val (name, typ) = s.splitAt(s.lastIndexOf('-'))
    ServiceId(name, ServiceType(typ.drop(1)))
  }
}

