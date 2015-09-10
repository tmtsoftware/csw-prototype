package csw.util.config

import csw.util.config.ConfigKeys.StringValued
import csw.util.config.Configurations.{SetupConfigArg, SetupConfig}

// XXX TODO: This class is left over from previous versions
object TestConfig {

  import csw.util.config.Configurations.ConfigKey._

  val obsId = "TMT-2021A-C-2-1"

  val posName = new Key("posName") with StringValued
  val c1 = new Key("c1") with StringValued
  val c2 = new Key("c2") with StringValued
  val equinox = new Key("equinox") with StringValued

  val k1 = "tcs.base.pos"
  val k2 = "tcs.base.pos.one"

  // Config to use for testing
  val testConfig1 = SetupConfig(k1)
    .set(posName)("NGC738B")
    .set(c1)("22:35:58.530")
    .set(c2)("33:57:55.40")
    .set(equinox)("J2000")

  val testConfig2 = SetupConfig(k1)
    .set(c1)("22:36:01.066")
    .set(c2)("33:58:21.69")
    .set(equinox)("J2000")

  val testConfigArg = SetupConfigArg("obsId", Seq(testConfig1, testConfig2))

  // Reference config for use with checkValid: Tests only presence of keys and value types
  val refConfig1 = SetupConfig(k1)
    .set(posName)("")
    .set(c1)("")
    .set(c2)("")
    .set(equinox)("")

  val refConfig2 = SetupConfig(k1)
    .set(c1)("")
    .set(c2)("")
    .set(equinox)("")

  val refConfigArg = SetupConfigArg(obsId, Seq(refConfig1, refConfig2))
}

