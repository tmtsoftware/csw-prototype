package csw.util.config

import org.scalatest.FunSpec
import csw.util.config.ConfigDSL._

/**
  * Created by abrighto on 12/07/16.
  */
class ConfigDSLFunctionsTests extends FunSpec {

  describe("Tests DSL functions") {
    val k1 = IntKey("itest")
    val k2 = DoubleKey("dtest")
    val k3 = StringKey("stest")
    val k4 = LongArrayKey("lartest")

    val i1 = set(k1, 1, 2, 3).withUnits(UnitsOfMeasure.Deg)
    val i2 = set(k2, 1.0, 2.0, 3.0).withUnits(UnitsOfMeasure.Meters)
    val i3 = set(k3, "A", "B", "C")
    val i4 = set(k4, LongArray(Array.fill[Long](100)(10)), LongArray(Array.fill[Long](100)(100)))

    it("should allow using head(item) and value(item)") {
      assert(head(i1) == 1)
      assert(value(i1, 1) == 2)
      assert(value(i1, 2) == 3)
      assert(get(i1, 3).isEmpty)
      assert(values(i1) == Vector(1, 2, 3))
      assert(i1 == set(k1, Vector(1, 2, 3), UnitsOfMeasure.Deg))

      assert(head(i2) == 1.0)
      assert(value(i2, 1) == 2.0)
      assert(value(i2, 2) == 3.0)
      assert(get(i2, 3).isEmpty)
      assert(values(i2) == Vector(1.0, 2.0, 3.0))
      assert(i2 == set(k2, Vector(1.0, 2.0, 3.0), UnitsOfMeasure.Deg))
    }
  }


}
