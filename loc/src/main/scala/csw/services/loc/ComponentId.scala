package csw.services.loc

/**
 * Used to identify a component
 *
 * @param name the service name
 * @param componentType HCD, Assembly, Service
 */
case class ComponentId(name: String, componentType: ComponentType) {
  override def toString = s"$name-$componentType"
}

object ComponentId {
  /**
   * Gets a ComponentId from a string, as output by ComponentId.toString
   */
  def apply(s: String): ComponentId = {
    val (name, typ) = s.splitAt(s.lastIndexOf('-'))
    ComponentId(name, ComponentType(typ.drop(1)))
  }
}

