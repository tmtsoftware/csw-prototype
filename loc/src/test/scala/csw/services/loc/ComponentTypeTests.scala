package csw.services.loc

import csw.services.loc.ComponentType._
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}


class ComponentTypeTests extends FunSpecLike with Matchers with BeforeAndAfterAll {

  it("component type should be creatable from string") {
    assert(ComponentType("Assembly").get === Assembly)
    assert(ComponentType( "hcd").get === HCD)
    assert(ComponentType("CONTAINER").get === Container)
    assert(ComponentType("Service").get === Service)
    intercept[UnknownComponentTypeException] {
      ComponentType("NotAType").get
    }

    assert(Assembly.toString === "Assembly")
    assert(HCD.toString === "HCD")
    assert(Container.toString === "Container")
    assert(Service.toString === "Service")
  }

  it("Component ID should work properly") {
    val name = "test-1"
    val typ = Assembly
    val result = "test-1-Assembly"

    val c1 = ComponentId(name, typ)
    assert(c1.toString() === result)

    val c2 = ComponentId(result).get
    assert(c2.name === name)
    assert(c2.componentType === typ)
  }

}
