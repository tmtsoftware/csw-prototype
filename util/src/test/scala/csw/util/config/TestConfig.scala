package csw.util.config

import csw.util.config.ConfigKeys.StringValued
import csw.util.config.Configurations.{ SetupConfigArg, SetupConfig }

// XXX TODO: This class is left over from previous versions
object TestConfig {

  import csw.util.config.Configurations.ConfigKey._

  val obsId = "TMT-2021A-C-2-1"

  object posName extends Key("posName") with StringValued
  object c1 extends Key("c1") with StringValued
  object c2 extends Key("c2") with StringValued
  object equinox extends Key("equinox") with StringValued

  // Configs to use for testing
  val testConfig1 = SetupConfig("tcs.base.pos")
    .set(posName)("NGC738B")
    .set(c1)("22:35:58.530")
    .set(c2)("33:57:55.40")
    .set(equinox)("J2000")

  val testConfig2 = SetupConfig("tcs.ao.pos.one")
    .set(c1)("22:36:01.066")
    .set(c2)("33:58:21.69")
    .set(equinox)("J2000")

  val testConfigArg = SetupConfigArg("obs0001", Seq(testConfig1, testConfig2))

//  // Reference config for use with checkValid: Tests only presence of keys and value types
//  val refConfig1 = SetupConfig(k1)
//    .set(posName)("")
//    .set(c1)("")
//    .set(c2)("")
//    .set(equinox)("")
//
//  val refConfig2 = SetupConfig(k1)
//    .set(c1)("")
//    .set(c2)("")
//    .set(equinox)("")
//
//  val refConfigArg = SetupConfigArg(obsId, Seq(refConfig1, refConfig2))
}

