package csw.services.pkg

import csw.util.config.Configurations.{SetupConfig, SetupConfigArg}
import csw.util.config.{Key, StringKey}

// XXX TODO: This class is left over from previous versions
object TestConfig {

  val obsId = "TMT-2021A-C-2-1"

  val posName = StringKey("posName")
  val c1 = StringKey("c1")
  val c2 = StringKey("c2")
  val equinox = StringKey("equinox")

  // Configs to use for testing
  val testConfig1 = SetupConfig("tcs.base.pos")
    .set(posName, "NGC738B")
    .set(c1, "22:35:58.530")
    .set(c2, "33:57:55.40")
    .set(equinox, "J2000")

  val testConfig2 = SetupConfig("tcs.ao.pos.one")
    .set(c1, "22:36:01.066")
    .set(c2, "33:58:21.69")
    .set(equinox, "J2000")

  val testConfigArg = SetupConfigArg("obs0001", testConfig1, testConfig2)
}

