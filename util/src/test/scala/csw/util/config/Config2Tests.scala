package csw.util.config

import csw.util.config.ConfigKeys._
import csw.util.config.Configurations._
import org.scalatest.{Matchers, FunSpec}

/**
 * Tests the config classes
 */
class Config2Tests extends FunSpec with Matchers {
  val testObsId = "2020-Q22-2-3"
  val testConfigInfo = ConfigInfo(ObsID(testObsId))

  /**
   * Tests for ValueData
   */
  describe("Basic ValueData tests") {
    import Configurations.ConfigKey._
    import StandardKeys._

    val ck = "wfos.blue.filter"
    val ck1 = "wfos.prog.cloudcover"
    val ck2 = "wfos.red.filter"
    val ck3 = "wfos.red.detector"

    val sc1 = SetupConfig(ck)
      .set(position)("GG484")
    assert(sc1.get(position).contains("GG484"))

    val sc2 = SetupConfig(ck2)
      .set(position)("IR2")
      .set(cloudCover)(PERCENT_20)
    assert(sc2.get(position).contains("IR2"))
    assert(sc2.get(cloudCover).contains(PERCENT_20))

    val ob1 = ObserveConfig(ck3)
      .set(exposureTime)(22)
      .set(repeats)(2)
      .set(exposureType)(OBSERVE)
      .set(exposureClass)(SCIENCE)
    assert(ob1.get(exposureTime).contains(22.0))
    assert(ob1.get(repeats).contains(2))
    assert(ob1.get(exposureType).contains(OBSERVE))
    assert(ob1.get(exposureClass).contains(SCIENCE))

    println("SCN: " + ob1)

    implicit val mycdata = ConfigInfo("testing")
    val obsId1 = "2014-C2-4-44"
    val sca1 = SetupConfigArg(sc1, sc2)

    println("Sca1: " + sca1)

    val oca1 = ObserveConfigArg(ob1)
    println("oca1: " + oca1)
  }

  describe("Filters") {
    import Configurations.ConfigKey._

    val ck = "wfos.blue.filter"
    val ck2 = "wfos.red.filter"
    val ck3 = "iris.imager.detector"

    val sc1: SetupConfig = SetupConfig(ck)
    val sc2 = SetupConfig(ck2)
    val ob1 = ObserveConfig(ck3)
    val w1 = WaitConfig(ck3)
    val s1 = Seq(sc1, sc2, ob1, w1)
    println("Sca1: " + s1)

    it("should see 3 prefixes - one is duplicate") {
      val r1 = ConfigFilters.prefixes(s1)
      assert(r1.size == 3)
    }

    it("should see 2 setup configs") {
      val r1 = ConfigFilters.onlySetupConfigs(s1)
      assert(r1.size == 2)
    }

    it("should see 1 observe configs") {
      val r1 = ConfigFilters.onlyObserveConfigs(s1)
      assert(r1.size == 1)
    }

    it("should see 1 wait config") {
      val r1 = ConfigFilters.onlyWaitConfigs(s1)
      assert(r1.size == 1)
    }
  }

  describe("Testing config traits") {
    import Configurations.ConfigKey._

    val ck = "wfos.blue.filter"
    val ck2 = "wfos.red.filter"
    val ck3 = "iris.imager.detector"

    val sc1: SetupConfig = SetupConfig(ck)
    val ob1 = ObserveConfig(ck3)
    val w1 = WaitConfig(ck3)

    it("SequenceConfig should allow all three config types") {
      assert(sc1.isInstanceOf[SequenceConfig])
      assert(ob1.isInstanceOf[SequenceConfig])
      assert(w1.isInstanceOf[SequenceConfig])
    }

    it("ControlConfig should not allow WaitConfig") {
      assert(sc1.isInstanceOf[ControlConfig])
      assert(ob1.isInstanceOf[ControlConfig])
      assert(!w1.isInstanceOf[ControlConfig])
    }
  }

  describe("Basic ValueData tests") {
    import Configurations.ConfigKey._
    import StandardKeys._

    val ck = "wfos.blue.detector"

    val ob1 = ObserveConfig(ck)
      .set(exposureTime)(22)
      .set(repeats)(2)
      .set(exposureType)(OBSERVE)
      .set(exposureClass)(SCIENCE)

    it("Should have all the keys") {
      assert(ob1.get(exposureTime).contains(22.0))
      assert(ob1.get(repeats).contains(2))
      assert(ob1.get(exposureType).contains(OBSERVE))
      assert(ob1.get(exposureClass).contains(SCIENCE))
    }

    println("SCN: " + ob1)
    it("Should allow removal of keys") {
      val ob1a = ob1.remove(exposureTime)
      assert(ob1a.get(exposureTime).isEmpty)
      assert(ob1a.size == 3)
      val ob1b = ob1a.remove(exposureClass)
      assert(ob1b.get(exposureClass).isEmpty)
      assert(ob1b.size == 2)
      val ob1c = ob1b.remove(exposureType)
      assert(ob1c.get(exposureType).isEmpty)
      assert(ob1c.size == 1)
      val ob1d = ob1c.remove(repeats)
      assert(ob1d.get(repeats).isEmpty)
      assert(ob1d.size == 0)
    }
  }

}
