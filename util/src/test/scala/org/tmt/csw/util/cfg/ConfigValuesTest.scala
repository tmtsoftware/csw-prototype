package org.tmt.csw.util.cfg

import org.tmt.csw.util.cfg.Configurations._
import org.scalatest.{Matchers, FunSpec}
import org.tmt.csw.util.cfg.ConfigValues.{ValueData, CValue}
import org.tmt.csw.util.cfg.FullyQualifiedName.Fqn
import org.tmt.csw.util.cfg.FullyQualifiedName.Fqn._
import org.tmt.csw.util.cfg.UnitsOfMeasure.{Meters, NoUnits, Units}

class ConfigValuesTest extends FunSpec with Matchers {
  val numbers1 = (1 to 5).toVector
  val numbers2 = Vector(2.3, 40.22)

  val fqn1 = "tcs.base.pos.name"
  val fqn1prefix = "tcs.base.pos"
  val fqn1name = "name"
  val fqn2 = "tcs.base.pos.ra"
  // tcs/base/pos/ra
  val fqn3 = "tcs.base.pos.dec"
  val fqn4 = "tcs.wfs1.pos.name"
  val fqn5 = "tcs.wfs2.pos.ra"
  val fqn6 = "tcs.wfs2.pos.dec"
  val fqn7 = "mobie.red.filter"
  val fqn8 = "mobie.red.filter2"

  object testUnits1 extends Units("ms")

  object testUnits2 extends Units("s")

  /**
   * Tests for ValueData
   */
  describe("Basic ValueData tests") {
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
    it("it should have a value at 0") {
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
    import Fqn._
    // the :_* takes the sequence and makes it a vararg
    val t1 = CValue(fqn1.name, NoUnits, numbers1: _*)
    it("it should have the correct fqn") {
      t1.name should be(fqn1.name)
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
    val t1 = CValue(fqn1.name, testUnits1, numbers1: _*)
    val toStringExpected = "name(1, 2, 3, 4, 5)[ms]"
    it("it sholud be correct") {
      info("Note: this needs to be changed if toString is changed")
      t1.toString should be(toStringExpected)
    }
  }

  // Note: this is using apply from ValueData?
  describe("Create CValue with ValueData") {
    val v1 = ValueData(numbers1, testUnits1)
    val t1 = CValue(fqn1.name, v1)
    it("it should have the correct fqn") {
      t1.name should be(fqn1.name)
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
    import Fqn._
    val t1 = CValue(fqn3.name)
    it("it should have the correct fqn") {
      t1.name should be(fqn3.name)
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

  describe("test to see if CValue fqn prefix is removed automatically ") {
    import Fqn._

    // Experimental, not sure if this is a good idea
    val t1 = CValue(fqn3.name)
    it("it should have the correct fqn") {
      t1.name should be(fqn3.name)
    }
    val t2 = CValue(fqn3)
    it("it should be the name not full fqn") {
      t2.name should be(fqn3.name)
    }
  }

  describe("Testing +: for CValue") {
    import Fqn._

    val v1 = ValueData(numbers1, testUnits1)
    it("iniital values should be correct") {
      v1.elems should be(numbers1)
      v1.units should be(testUnits1)
    }
    // Create a new value to add
    val t1 = CValue(fqn1.name, v1)
    val newValue = 22
    val t2 = t1 :+ newValue
    it("it should have the new value added") {
      t2.elems should be(Seq.concat(numbers1, Seq(newValue)))
      t2.units should be(testUnits1)
    }
  }

  /*--------- End of CValue tests -------------- */
  /* ------------- Fqn tests ----------------- */

  describe("FQN tests") {

    val t1 = Fqn(fqn1)
    it("it should have the value of fqn") {
      t1.fqn should equal(fqn1)
    }
    it("it should create an exception for a null str") {
      intercept[AssertionError] {
        val t2 = Fqn(null)
        // This should create an exception
      }
    }
  }

  describe("Fqn: testing a valid name") {
    val r1 = Fqn.validateName("")
    it("an empty string should return None") {
      r1 should be(None)
    }
    it("should be plain text with no separator") {
      val t1 = ".ra"
      val r2 = Fqn.validateName(t1)
      r2 should be(None)
    }
    it("should be okay if none of these are true ") {
      val t3 = "pos"
      val r3 = Fqn.validateName(t3)
      r3 should be(Some(t3))
    }
  }

  describe("Fqn: test prefix and name") {
    val t1 = Fqn(fqn1)

    it("it should return a valid prefix without the last separator") {
      val t3 = t1.prefix
      t3 should equal(fqn1prefix)
    }

    it("it should return the valid name after the prefix") {
      val t4 = t1.name
      t4 should equal(fqn1name)
    }
  }

  describe("Fqn: Testing edge conditions for Fqn name and prefix") {
    val t1 = fqn1name
    it("prefix should return default prefix if no prefix") {
      Fqn.prefix(t1) should equal("")
    }
    it("name should return name if no prefix") {
      Fqn.name(t1) should equal(t1)
    }
  }

  describe("Fqn: testing for isFqn") {
    val t1 = fqn1
    Fqn.isFqn(t1) should be(right = true)

    val t2 = fqn1name
    Fqn.isFqn(t2) should be(right = false)
  }

  /* --------------- SetupConfigTests ------------------ */

  describe("SetupConfig: SetupConfig default construction") {
    val obsId1 = "2014-C2-3-22"
    val sc1 = SetupConfig(obsId1)

    it("it should have the correct obsId") {
      sc1.obsId should be(obsId1)
    }

    it("it should have the default prefix") {
      sc1.prefix should be(SetupConfig.DEFAULT_PREFIX)
    }

    it("Should have no data") {
      sc1.size should be(0)
    }
  }

  describe("SetupConfig: Adding CValues using :+") {

    // Create some data - this should be auto removed names
    val c1 = CValue(fqn1)
    val c2 = CValue(fqn2)
    val c3 = CValue(fqn3)

    val obsId1 = "2014-C2-3-22"
    val sc1 = SetupConfig(obsId1)
    sc1.obsId should be(obsId1)
    sc1.prefix should be(SetupConfig.DEFAULT_PREFIX)
    sc1.size should be(0)

    // Lists add on front, hmmm
    val sc2 = sc1 :+ c1 :+ c2 :+ c3
    it("Should have allow adding new CValues") {
      sc2.values should have size 3
      sc2.values.head should equal(c1)
      sc2.values.tail.head should equal(c2)
      sc2.values.last should equal(c3)
    }
  }

  describe("SetupConfig: test adding values with withValues") {
    import Fqn._

    // Create some data
    val c1 = CValue(fqn1.name, NoUnits, "bob")
    val c2 = CValue(fqn2.name, NoUnits, 2)
    val c3 = CValue(fqn3.name, NoUnits, 3)

    val obsId1 = "2014-C2-4-44"
    val prefix = fqn1.prefix
    val sc1 = SetupConfig(obsId1, prefix).withValues(c1, c2, c3)
    it("it should allow adding varargs withValues") {
      sc1.obsId should be(obsId1)
      sc1.size should be(3)
    }
    info("SC1: " + sc1)
  }

  describe("SetupConfig: get list of all names in config") {
    import Fqn._

    // Create some data
    val c1 = CValue(fqn1.name, NoUnits, "bob")
    val c2 = CValue(fqn2.name, NoUnits, 2)
    val c3 = CValue(fqn3.name, NoUnits, 3)

    val obsId1 = "2014-C2-4-44"
    val sc1 = SetupConfig(obsId1).withValues(c1, c2, c3)

    val names = sc1.names
    it("it should return the names of all cvalues in the config") {
      names.size should be(3)
    }
    it("it should be a list of all test fqns") {
      names should contain(fqn1.name)
      names should contain(fqn2.name)
      names should contain(fqn3.name)
    }

  }

  describe("ConfigList: first filter test") {
    import Fqn._

    // Create some data
    val c1 = CValue(fqn1.name)
    val c2 = CValue(fqn2.name)
    val c3 = CValue(fqn3.name)
    val c4 = CValue(fqn4.name)
    val c5 = CValue(fqn5.name)
    val c6 = CValue(fqn6.name)
    val c7 = CValue(fqn7.name)
    val c8 = CValue(fqn8.name)

    val obsId1 = "2014-C2-4-44"
    val sc1 = SetupConfig(obsId1, fqn1.prefix).withValues(c1, c2, c3)
    val sc2 = SetupConfig(obsId1, fqn4.prefix).withValues(c4)
    val sc3 = SetupConfig(obsId1, fqn5.prefix).withValues(c5, c6)
    val sc4 = SetupConfig(obsId1, fqn7.prefix).withValues(c7, c8)

    // Add to list
    val cl1 = ConfigList(sc1, sc2, sc3, sc4)

    val t1 = "mobie"
    val r1 = cl1.startsWith(t1)

    //info("Select results: " +  r1)
    it("it should result in selecton of 1 SetupConfig with mobie") {
      r1.size should be(1)
    }

    // Wrap in a new configlist
    val cl2 = List(r1)
    it("new mobie list should have 1 members") {
      cl2.size should be(1)
    }

    // This test is from original ConfigList
    val t2 = "pos"
    val r2 = cl1.contains(t2)
    it("it should return results for contains") {
      r2.size should be(3)
    }
  }


  describe("Testing getConfig parts") {
    import Fqn._

    // Create some data
    val c1 = CValue(fqn1.name)
    val c2 = CValue(fqn2.name)
    val c3 = CValue(fqn3.name)
    val c4 = CValue(fqn4.name)
    val c5 = CValue(fqn5.name)
    val c6 = CValue(fqn6.name)
    val c7 = CValue(fqn7.name)
    val c8 = CValue(fqn8.name)

    val obsId1 = "2014-C2-4-44"
    val sc1 = SetupConfig(obsId1, fqn1.prefix).withValues(c1, c2, c3)
    val sc2 = SetupConfig(obsId1, fqn4.prefix).withValues(c4)
    val sc3 = SetupConfig(obsId1, fqn5.prefix).withValues(c5, c6)
    val sc4 = SetupConfig(obsId1, fqn7.prefix).withValues(c7, c8)

    // Add to list
    val cl1 = ConfigList(sc1, sc2, sc3, sc4)

    val t1 = cl1.getFirst
    info("test: " + t1)

  }

  describe("Testing the ConfigList names function") {
    import Fqn._

    // Create some data
    val c1 = CValue(fqn1.name)
    val c2 = CValue(fqn2.name)
    val c3 = CValue(fqn3.name)
    val c4 = CValue(fqn4.name)
    val c5 = CValue(fqn5.name)
    val c6 = CValue(fqn6.name)
    val c7 = CValue(fqn7.name)
    val c8 = CValue(fqn8.name)

    val obsId1 = "2014-C2-4-44"
    val obsId2 = "2014-C2-4-50"
    val sc1 = SetupConfig(obsId1, fqn1.prefix).withValues(c1, c2, c3)
    val sc2 = SetupConfig(obsId1, fqn4.prefix).withValues(c4)
    val sc3 = SetupConfig(obsId2, fqn5.prefix).withValues(c5, c6)
    val sc4 = SetupConfig(obsId2, fqn7.prefix).withValues(c7, c8)

    // Add to list
    val cl1 = ConfigList(sc1, sc2, sc3, sc4)


    it("ConfigList test for returning names when names are unique") {
      val r1 = cl1.prefixes
      r1.size should be(4)
      r1 should contain(fqn1.prefix)
      r1 should contain(fqn4.prefix)
      r1 should contain(fqn5.prefix)
      r1 should contain(fqn7.prefix)
    }

    // Add to list
    val cl2 = ConfigList(sc1, sc1, sc1, sc2)
    it("it is a Set so it should not contain duplicates") {
      val r2 = cl2.prefixes
      r2.size should be(2)
      r2 should contain(fqn1.prefix)
      r2 should contain(fqn4.prefix)
      r2 should not contain fqn5.prefix
      r2 should not contain fqn7.prefix
    }
  }

  describe("Testing the ConfigList obsId function") {

    // Create some data
    val c1 = CValue(fqn1.name)
    val c2 = CValue(fqn2.name)
    val c3 = CValue(fqn3.name)
    val c4 = CValue(fqn4.name)
    val c5 = CValue(fqn5.name)
    val c6 = CValue(fqn6.name)
    val c7 = CValue(fqn7.name)
    val c8 = CValue(fqn8.name)

    val obsId1 = "2014-C2-4-44"
    val obsId2 = "2014-C2-4-50"
    val obsId3 = "2014-Q3-22-22"
    val sc1 = SetupConfig(obsId1, fqn1.prefix).withValues(c1, c2, c3)
    val sc2 = SetupConfig(obsId1, fqn4.prefix).withValues(c4)
    val sc3 = SetupConfig(obsId2, fqn5.prefix).withValues(c5, c6)
    val sc4 = SetupConfig(obsId3, fqn7.prefix).withValues(c7, c8)

    // Add to list
    val cl1 = ConfigList(sc1, sc3, sc4)

    it("ConfigList test for returning obsId when names are unique") {
      val r1 = cl1.obsIds
      r1.size should be(3)
      r1 should contain(obsId1)
      r1 should contain(obsId2)
      r1 should contain(obsId3)
    }
    // Add to list
    val cl2 = ConfigList(sc1, sc1, sc2, sc3, sc4)
    it("it is a Set so it should not contain duplicate obsIds") {
      val r2 = cl2.obsIds
      r2.size should be(3)
      r2 should contain(obsId1)
      r2 should contain(obsId2)
      r2 should contain(obsId3)
    }
  }

  describe("Testing the ConfigList with different config types") {

    // Create some data
    val c1 = CValue(fqn1.name)
    val c2 = CValue(fqn2.name)
    val c3 = CValue(fqn3.name)
    val c4 = CValue(fqn4.name)
    val c5 = CValue(fqn5.name)
    val c6 = CValue(fqn6.name)
    val c7 = CValue(fqn7.name)
    val c8 = CValue(fqn8.name)

    val obsId1 = "2014-C2-4-44"
    val sc1 = SetupConfig(obsId1, fqn1.prefix).withValues(c1, c2, c3)
    val sc3 = SetupConfig(obsId1, fqn5.prefix).withValues(c5, c6)
    val sc4 = SetupConfig(obsId1, fqn7.prefix).withValues(c7, c8)

    val obsConfig1 = ObserveConfig(obsId1)
    val waitConfig1 = WaitConfig(obsId1)

    // Add to list
    val cl1 = List(sc1, sc4, obsConfig1, sc3, waitConfig1)
    it("it should have all the members of the list") {
      cl1.size should be(5)
    }

    it("it should have only 1 wait config") {
      val t2 = cl1.onlyWaitConfigs
      t2.size should be(1)
      val wc: WaitConfig = t2(0)
      wc.obsId should be(obsId1)
    }

    it("it should have only 1 observe config") {
      val t3 = cl1.onlyObserveConfigs
      t3.size should be(1)
      val oc: ObserveConfig = t3(0)
      oc.obsId should be(obsId1)
    }

    it("it should have 3 setup configs") {
      val t4 = cl1.onlySetupConfigs
      t4.size should be(3)
      val sc: SetupConfig = t4(0)
      sc.obsId should be(obsId1)
    }
  }
}
