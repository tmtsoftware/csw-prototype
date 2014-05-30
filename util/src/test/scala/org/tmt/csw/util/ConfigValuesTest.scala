package org.tmt.csw.util

import org.scalatest.{ShouldMatchers, FunSpec}
import org.tmt.csw.util.cfg.Units._
import org.tmt.csw.util.cfg.ConfigValues._

class ConfigValuesTest extends FunSpec with ShouldMatchers {
  val numbers1 = (1 to 5).toVector
  val numbers2 = Vector(2.3, 40.22)

  val fqn1 = "tcs.base.pos.name"
  val fqn2 = "tcs.base.pos.ra"
  val fqn3 = "tcs.base.pos.dec"
  val fqn4 = "tcs.wfs1.pos.name"
  val fqn5 = "tcs.wfs2.pos.ra"
  val fqn6 = "tcs.wfs2.pos.dec"
  val fqn7 = "mobie.red.filter"
  val fqn8 = "mobie.blue.filter"

  object testUnits1 extends Units("ms")

  object testUnits2 extends Units("s")

  /**
   * Tests for ValueData
   */
  describe("Bacic ValueData tests") {
    it("it can have values of a single type: Int") {
      val t1 = ValueData(numbers1)
      t1.elems should be(numbers1)
      t1.elems should have size numbers1.size
    }
    it("it can be any type: Double") {
      val t2 = ValueData(numbers2)
      t2.elems should be(numbers2)
      t2.elems should have size numbers2.size
    }

  }

  describe("Check individual access") {
    it("it hould have a value at 0") {
      val t1 = ValueData(numbers1)
      t1(0) should be(numbers1(0))
    }

    it("it should have a value at 2") {
      val t2 = ValueData(numbers1)
      t2(2) should be(numbers1(2))
    }
  }

  describe("Check adding item with :+ to ValueData") {
    it("it should be possible to add an item") {
      var t1 = ValueData(numbers1)
      t1.elems should be(numbers1)

      // Add a value
      val newValue = 100
      t1 = t1 :+ newValue
      t1.elems.length should be(numbers1.length + 1)
      t1.elems should be(Seq.concat(numbers1, Seq(newValue)))
    }
  }

  describe("Check out of range access creates exception") {
    it("it should create an exception for out of range") {
      intercept[IndexOutOfBoundsException] {
        val t3 = ValueData(numbers1)
        // This should create an exception
        t3(numbers1.length + 1)
      }
    }
  }

  describe("Test empty ValueData") {
    val t1 = ValueData.empty
    it("should have no units") {
      t1.units should be(NoUnits)
    }
    it("it should be empty") {
      t1.elems.length should be(0)
    }
  }

  describe("Test use of Units") {
    val t1 = ValueData(numbers1, Meters)
    it("should have expected data") {
      t1.elems should be(numbers1)
    }
    it("should have correct units") {
      t1.units should be(Meters)
    }
  }

  describe("Adding units withUnits") {
    val t1 = ValueData(numbers1, testUnits1)

    it("it should allow adding units afterword") {
      t1.elems should be(numbers1)
      t1.units should be(testUnits1)
    }
    it("should be allow adding new units to same data") {
      val t2 = ValueData.withUnits(t1, testUnits2)
      t2.elems should be(t1.elems)
      t2.units should be(testUnits2)
    }
  }

  describe("Test adding values withValues") {
    val t1 = ValueData(numbers1, testUnits1)
    t1.elems should be(numbers1)
    t1.units should be(testUnits1)

    it("it should be allow adding new values and retaining units") {
      val t2 = ValueData.withValues(t1, numbers2)
      t2.elems should be(numbers2)
      t2.units should be(t1.units)
    }
  }

  /**
   * Following are CValue tests
   */
  describe("Basic creation of CValue") {
    // the :_* takes the sequence and makes it a vararg
    val t1 = CValue(fqn1, NoUnits, numbers1: _*)
    it("it should have the correct fqn") {
      t1.fqn should be(fqn1)
    }
    it("it should have the right data length") {
      t1.length should be(numbers1.length)
    }
    it("it should have the correct data") {
      t1.elems should be(numbers1)
    }
    it("and it should have the correct units") {
      t1.units should be(NoUnits)
    }
  }

  describe("Test string rep for CValue") {
    val t1 = CValue(fqn1, testUnits1, numbers1: _*)
    val toStringExpected = "tcs.base.pos.name(1, 2, 3, 4, 5)[ms]"
    it("it sholud be correct") {
      info("Note: this needs to be changed if toString is changed")
      t1.toString should be(toStringExpected)
    }
  }

  // Note: this is using apply from ValueData?
  describe("Create CValue with ValueData") {
    val v1 = ValueData(numbers1, testUnits1)
    val t1 = CValue(fqn1, v1)
    it("it should have the correct fqn") {
      t1.fqn should be(fqn1)
    }
    it("it should have the right data length") {
      t1.length should be(numbers1.length)
    }
    it("it should have the correct data") {
      t1.elems should be(numbers1)
    }
    it("and it should have the correct units") {
      t1.units should be(testUnits1)
    }
  }

  describe("Test creating an empty CValue") {
    val t1 = CValue(fqn3)
    it("it should have the correct fqn") {
      t1.fqn should be(fqn3)
    }
    it("it should have the right data length") {
      t1.length should be(0)
    }
    it("it should have the correct (i.e. no) data") {
      t1.elems should be(Seq.empty)
    }
    it("and it should have the correct units") {
      t1.units should be(NoUnits)
    }
  }

  describe("Testing +: for CValue") {
    val v1 = ValueData(numbers1, testUnits1)
    it("iniital values should be correct") {
      v1.elems should be(numbers1)
      v1.units should be(testUnits1)
    }
    // Create a new value to add
    val t1 = CValue(fqn1, v1)
    val newValue = 22
    val t2 = t1 :+ newValue
    it("it should have the new value added") {
      t2.elems should be(Seq.concat(numbers1, Seq(newValue)))
      t2.units should be(testUnits1)
    }
  }
}