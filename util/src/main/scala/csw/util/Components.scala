package csw.util

/**
  * TMT Source Code: 3/15/16.
  */
object Components {

  /**
    * TMT Source Code: 3/11/16.
    */
  /**
    * Used to identify a service or component
    *
    * @param componentName the component name
    * @param componentType HCD, Assembly, Service
    */
  case class ComponentId(componentName: String, componentType: ComponentType) {
    override def toString = s"$componentName-$componentType"
  }

  object ComponentId {
    /**
      * Gets a ComponentId from a string, as output by toString
      */
    def apply(s: String): ComponentId = {
      val (componentName, typ) = s.splitAt(s.lastIndexOf('-'))
      ComponentId(componentName, ComponentType(typ.drop(1)))
    }
  }

  /**
    * CSW Component types
    */
  sealed trait ComponentType

  /**
    * A container for services, assemblies and HCDs
    */
  case object Container extends ComponentType

  /**
    * A service that controls a hardware device
    */
  case object HCD extends ComponentType

  /**
    * A service that controls one or more HCDs or assemblies
    */
  case object Assembly extends ComponentType

  /**
    * A general purpose service (actor and/or web service application)
    */
  case object Service extends ComponentType

  // Exception thrown when a string can not be parsed to a component type
  case class UnknownComponentTypeException(message: String) extends Exception(message)

  object ComponentType {
    /**
      * Returns the named service type (default: Service)
      */
    def apply(name: String): ComponentType = name.toLowerCase match {
      case "container" ⇒ Container
      case "assembly" ⇒ Assembly
      case "hcd" ⇒ HCD
      case "service" ⇒ Service
      case x ⇒ throw UnknownComponentTypeException(x)
    }
  }

  sealed trait ConnectionType {
    def name: String

    override def toString = name
  }

  /**
    * Type of a REST/HTTP based component connection
    */
  final case object HttpType extends ConnectionType {
    val name = "http"
  }

  /**
    * Type of an Akka actor based component connection
    */
  final case object AkkaType extends ConnectionType {
    val name = "akka"
  }

  case class UnknownConnectionTypeException(message: String) extends Exception(message)

  object ConnectionType {
    /**
      * Gets an ConnectionType from the string value
      */

    def apply(name: String): ConnectionType = name match {
      case "http" ⇒ HttpType
      case "akka" ⇒ AkkaType
      case x ⇒ throw UnknownConnectionTypeException(x)
    }
  }


  sealed trait Connection {
    def componentId: ComponentId

    def connectionType: ConnectionType

    override def toString = s"$componentId-$connectionType"

    override def equals(that: Any) = that match {
      case (that: Connection) ⇒ this.toString == that.toString
      case _ ⇒ false
    }
  }

  final case class AkkaConnection(componentId: ComponentId) extends Connection {
    val connectionType = AkkaType
  }

  final case class HttpConnection(componentId: ComponentId) extends Connection {
    val connectionType = HttpType
  }

  object Connection {

    def apply(componentId: ComponentId, connectionType: ConnectionType): Connection = {
      connectionType match {
        case AkkaType ⇒ AkkaConnection(componentId)
        case HttpType ⇒ HttpConnection(componentId)
      }
    }

    /**
      * Gets a LocationRef from a string as output by toString
      */
    def apply(s: String): Option[Connection] = {
      val (sId, aTyp) = s.splitAt(s.lastIndexOf('-')) // To strings
      ConnectionType(aTyp.drop(1)) match {
        case AkkaType ⇒ Some(AkkaConnection(ComponentId(sId)))
        case HttpType ⇒ Some(HttpConnection(ComponentId(sId)))
      }
    }
  }

}
