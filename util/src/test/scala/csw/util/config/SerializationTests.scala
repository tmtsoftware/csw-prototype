package csw.util.config

import csw.util.config.ConfigKeys.{ IntValued, DoubleValued, StringValued }
import csw.util.config.Configurations._
import org.scalatest.FunSuite

/**
 * Created by gillies on 7/30/15.
 */
class SerializationTests extends FunSuite {
  import Configurations.ConfigKey._

  val obsId = ObsID("2023-Q22-4-33")

  val fqn1 = "tcs.base.pos.name"
  val fqn1prefix = "tcs.base.pos"
  val fqn1name = "name"
  val fqn2 = "tcs.base.pos.ra"
  val fqn3 = "tcs.base.pos.dec"

  val ra = new Key("ra") with StringValued
  val dec = new Key("dec") with StringValued
  val epoch = new Key("epoch") with DoubleValued
  val test = new Key("test") with IntValued

  val prefix1 = "tcs.pos"
  val sc1 = SetupConfig(prefix1)
  sc1.set(ra)("12:32:11")
  sc1.set(dec)("30:22:22")
  sc1.set(epoch)(1950)
  sc1.set(test)(1) //.second

  val disperser = new Key("disperser") with StringValued
  val filter1 = new Key("filter1") with StringValued
  val prefix2 = "wfos.blue"
  val sc2 = SetupConfig(prefix2)
  sc2.set(disperser)("gr243")
  sc2.set(filter1)("GG433")

  import StandardKeys._
  val ob1 = ObserveConfig(prefix2)
  ob1.set(exposureTime)(22.3) // .sec,
  ob1.set(repeats)(3)

  val wc1 = WaitConfig(prefix2)

  test("ConfigType Java serialization") {
    import ConfigSerializer._

    //    val prefix = fqn1.prefix
    // ("Test setup config Java serialization") {
    val bytes = write(sc1)
    val scout = read[SetupConfig](bytes)
    assert(scout == sc1)

    //("Test observe config Java serialization") {
    val bytes1 = write(ob1)
    val obout = read[ObserveConfig](bytes1)
    assert(obout == ob1)

    //("Test wait config Java serialization") {
    val bytes2 = write(wc1)
    val wout = read[WaitConfig](bytes2)
    assert(wout == wc1)
  }

  test("SetupConfigArg Java serialization") {
    import ConfigSerializer._

    val sca1 = SetupConfigArg(ConfigInfo(obsId), Seq(sc1))
    val bytes1 = write(sca1)

    val sout1 = read[SetupConfigArg](bytes1)
    assert(sout1 == sca1)
  }

  test("ObserveConfigArg Java serialization") {
    import ConfigSerializer._

    val oca1 = ObserveConfigArg(ConfigInfo(obsId), Seq(ob1))
    val bytes1 = write(oca1)

    val out1 = read[ObserveConfigArg](bytes1)
    assert(out1 == oca1)
  }

  test("WaitConfigArg Java serialization") {
    import ConfigSerializer._

    val wca1 = WaitConfigArg(ConfigInfo(obsId), wc1)
    val bytes1 = write(wca1)

    val out1 = read[WaitConfigArg](bytes1)
    assert(out1 == wca1)
  }

}
