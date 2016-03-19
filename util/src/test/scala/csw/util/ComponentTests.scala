package csw.util

import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}

import Components._

/**
  * TMT Source Code: 2/19/16.
  */

/**
  * TMT Source Code: 2/9/16.
  */
class ComponentTests extends FunSpecLike with Matchers with BeforeAndAfterAll {
  // Container is messed up because there is a Container class and a Container ComponentType

  it ("component type should be creatable from string") {
    var typ = "Assembly"
    assert(ComponentType(typ) === Assembly)
    typ = "hcd"
    assert(ComponentType(typ) === HCD)
    typ = "CONTAINER"
    assert(ComponentType(typ) === Container)
    typ = "Service"
    assert(ComponentType(typ) === Service)
    typ = "NotAType"
    intercept[UnknownComponentTypeException]{
      ComponentType(typ)
    }

    assert(Assembly.toString() === "Assembly")
    assert(HCD.toString() === "HCD")
    assert(Container.toString() === "Container")
    assert(Service.toString() === "Service")
  }

  it("Component ID should work properly") {
    val name = "test-1"
    val typ = Assembly
    val result = "test-1-Assembly"

    val c1 = ComponentId(name, typ)
    assert(c1.toString() === result)

    val c2 = ComponentId(result)
    assert(c2.componentName === name)
    assert(c2.componentType === typ)
  }

}
