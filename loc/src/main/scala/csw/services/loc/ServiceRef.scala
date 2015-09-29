package csw.services.loc

/**
 * Describes a service and the way it is accessed (http, akka)
 * @param serviceId holds the service name and type (assembly, hcd, etc.)
 * @param accessType indicates how the service is accessed (http, akka)
 */
case class ServiceRef(serviceId: ServiceId, accessType: AccessType) {
  override def toString = s"$serviceId-$accessType"

  override def equals(that: Any) = that match {
    case (that: ServiceRef) ⇒ this.toString == that.toString;
    case _                  ⇒ false;
  }
}

object ServiceRef {
  /**
   * Gets a ServiceRef from a string as output by toString
   */
  def apply(s: String): ServiceRef = {
    val (sId, aTyp) = s.splitAt(s.lastIndexOf('-'))
    ServiceRef(ServiceId(sId), AccessType(aTyp.drop(1)))
  }
}

